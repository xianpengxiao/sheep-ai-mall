package com.xs.sheepaimall.service.agent;

import cn.hutool.json.JSONUtil;
import com.xs.sheepaimall.entity.ChatPendingOp;
import com.xs.sheepaimall.service.CartService;
import com.xs.sheepaimall.service.OrderItemService;
import com.xs.sheepaimall.service.OrderService;
import com.xs.sheepaimall.service.PendingOperationService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.service.impl.AuditService;
import com.xs.sheepaimall.service.impl.ChatSessionStateService;
import com.xs.sheepaimall.service.impl.CsAgentConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 智能客服 Agent 调用封装 —— 构建工具回调列表 + ChatClient 带工具的流式调用。
 * <p>每次调用前先由 MVC 线程捕获 userId / merchantId / sessionId 构造
 * {@link CustomerServiceTools} 实例（普通 POJO，非 Bean），再基于其 @Tool 方法生成
 * MethodToolCallback 列表挂到 ChatClient。工具方法签名对 LLM 不可见身份信息。</p>
 * <p>流式改用 chatResponse() 取帧：文本照旧逐帧下发，末帧 metadata 的 Usage 回填到
 * usageRef 供计量落账（设计 D12）；输出上限取 cs_agent_config.max_output_tokens。</p>
 */
@Slf4j
@Service
public class AgentChatService {

    /** 工具入参/结果摘要最大长度（审计埋点用） */
    private static final int AUDIT_SUMMARY_LIMIT = 200;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private PendingOperationService pendingOperationService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private CartService cartService;

    @Autowired
    private SpuService spuService;

    @Autowired
    private SkuService skuService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private CsAgentConfigService csAgentConfigService;

    @Autowired
    private ChatSessionStateService chatSessionStateService;

    /**
     * 商品 ES 检索服务；ES 未配置（spring.elasticsearch.uris 为空）时 Bean 不存在，此处允许为空，
     * 为空则商品搜索自动走 DB 兜底路径。
     */
    @Autowired(required = false)
    private com.xs.sheepaimall.service.ProductSearchService productSearchService;

    @Autowired
    private com.xs.sheepaimall.service.ShippingAddressService shippingAddressService;

    @Autowired
    private com.xs.sheepaimall.service.AfterSaleService afterSaleService;


    /**
     * 带工具的流式对话
     *
     * @param messages      系统消息 + 历史上下文 + 本轮用户消息
     * @param userId        当前用户ID（MVC 线程现场捕获）
     * @param merchantId    会话归属商家ID（null=平台）
     * @param sessionId     会话ID
     * @param pendingOpQueue 本次调用专用并发队列，写工具创建的操作单会放入其中
     * @param usageRef      计量回填引用：流末帧 Usage 写入（input/output/total，失败无 Usage 时不回填）
     * @return AI 回复内容流（工具调用对调用方透明，SSE 只下发文本增量）
     */
    public Flux<String> streamChat(List<Message> messages, Long userId, Long merchantId, Long sessionId,
                                   ConcurrentLinkedQueue<ChatPendingOp> pendingOpQueue,
                                   ConcurrentLinkedQueue<java.util.Map<String, Object>> goodsCardQueue,
                                   ConcurrentLinkedQueue<java.util.Map<String, Object>> orderCardQueue,
                                   AtomicReference<ChatMeter> usageRef) {
        CustomerServiceTools tools = new CustomerServiceTools(userId, merchantId, sessionId,
                orderService, orderItemService, cartService, spuService, skuService,
                pendingOperationService, chatSessionStateService, pendingOpQueue,
                goodsCardQueue, orderCardQueue, productSearchService, shippingAddressService, afterSaleService);
        List<ToolCallback> toolCallbacks = buildToolCallbacks(tools, userId, sessionId, merchantId);

        // 单次输出上限：取全局配置 max_output_tokens
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .maxTokens(csAgentConfigService.getConfig().getMaxOutputTokens())
                .build();

        return ChatClient.builder(chatModel).build()
                .prompt()
                .messages(messages)
                .tools(toolCallbacks)
                .options(options)
                .stream()
                .chatResponse()
                .map(chunk -> {
                    // 每个 chunk 都可能带 Usage（M6 末帧），逐帧覆盖，末帧即最终结果
                    recordUsage(chunk, usageRef);
                    // 工具调用帧无文本，提取为 null 的自然被 filter 掉
                    String text = extractText(chunk);
                    return text;
                })
                .filter(text -> text != null && !text.isEmpty());
    }

    /** 从 ChatResponse 提取文本（工具调用帧无 output 文本时返回 null） */
    private String extractText(ChatResponse chunk) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return null;
        }
        return chunk.getResult().getOutput().getText();
    }

    /** 逐帧提取 Usage 回填计量引用（M6 为 getPromptTokens/getCompletionTokens） */
    private void recordUsage(ChatResponse chunk, AtomicReference<ChatMeter> usageRef) {
        try {
            if (chunk == null || chunk.getMetadata() == null || chunk.getMetadata().getUsage() == null) {
                return;
            }
            var usage = chunk.getMetadata().getUsage();
            ChatMeter meter = new ChatMeter();
            meter.setInputTokens(usage.getPromptTokens() == null ? 0 : usage.getPromptTokens());
            meter.setOutputTokens(usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
            meter.setTotalTokens(usage.getTotalTokens() == null
                    ? meter.getInputTokens() + meter.getOutputTokens() : usage.getTotalTokens());
            usageRef.set(meter);
        } catch (Exception e) {
            log.warn("Usage 提取失败，本次计量记 0: {}", e.getMessage());
        }
    }

    /**
     * 扫描工具类中所有 @Tool 方法，生成 MethodToolCallback（方法签名 + javadoc 描述暴露给 LLM），
     * 并包一层审计埋点：记录工具名、参数摘要（≤200字符）、结果摘要（≤200字符），action=TOOL_CALL。
     * <p>工具在 Reactor 线程执行，UserContext 不可见，actorId 用构造时捕获的 userId。</p>
     */
    private List<ToolCallback> buildToolCallbacks(CustomerServiceTools tools, Long userId,
                                                  Long sessionId, Long merchantId) {
        List<ToolCallback> callbacks = new ArrayList<>();
        for (Method method : CustomerServiceTools.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                ToolCallback delegate = MethodToolCallback.builder()
                        .toolDefinition(ToolDefinition.from(method))
                        .toolObject(tools)
                        .toolMethod(method)
                        .build();
                callbacks.add(wrapWithAudit(delegate, userId, sessionId, merchantId));
            }
        }
        return callbacks;
    }

    /** 工具调用审计包装：delegate call()，前后记录摘要（D6） */
    private ToolCallback wrapWithAudit(ToolCallback delegate, Long userId, Long sessionId, Long merchantId) {
        ToolDefinition definition = delegate.getToolDefinition();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                String result = null;
                Throwable error = null;
                try {
                    result = delegate.call(toolInput);
                    return result;
                } catch (RuntimeException e) {
                    error = e;
                    throw e;
                } finally {
                    try {
                        String detail = JSONUtil.createObj()
                                .set("tool", definition.name())
                                .set("args", summary(toolInput))
                                .set("result", error != null ? "异常: " + summary(error.getMessage()) : summary(result))
                                .toString();
                        auditService.record(AuditService.ACTOR_USER, userId, sessionId, merchantId,
                                AuditService.ACTION_TOOL_CALL, detail);
                    } catch (Exception auditEx) {
                        log.warn("工具调用审计失败 tool={}: {}", definition.name(), auditEx.getMessage());
                    }
                }
            }
        };
    }

    /** 摘要截断：单条 ≤200 字符 */
    private String summary(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > AUDIT_SUMMARY_LIMIT ? text.substring(0, AUDIT_SUMMARY_LIMIT) : text;
    }
}
