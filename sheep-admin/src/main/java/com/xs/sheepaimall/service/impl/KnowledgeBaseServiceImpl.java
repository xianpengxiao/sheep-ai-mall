package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.config.VectorStoreConfig;
import com.xs.sheepaimall.dto.KnowledgeDocSaveDTO;
import com.xs.sheepaimall.entity.ChatRagDoc;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.mapper.ChatRagDocMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量知识库服务实现 — 平台库 + 每商家一表（PgVectorStore），PG 不可用时降级不阻断主流程。
 * <p>商家库 VectorStore 按 merchantId 缓存，避免每次检索都重复构建。</p>
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    /** 管理员角色编码，拥有知识库全量管理权限 */
    private static final String ADMIN_ROLE_CODE = "ROLE_ADMIN";

    /** 检索结果单块内容最大展示长度 */
    private static final int SEARCH_CONTENT_MAX_LEN = 500;

    @Autowired
    @Qualifier("pgVectorJdbcTemplate")
    private JdbcTemplate pgVectorJdbcTemplate;

    @Autowired
    @Qualifier("platformVectorStore")
    private VectorStore platformVectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ChatRagDocMapper chatRagDocMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    /** 商家向量库缓存：merchantId -> VectorStore（平台库直接走 platformVectorStore 不入缓存） */
    private final Map<Long, VectorStore> merchantStoreCache = new ConcurrentHashMap<>();

    // ======================== 向量库路由 ========================

    @Override
    public VectorStore getStore(Long merchantId) {
        if (merchantId == null) {
            return platformVectorStore;
        }
        return merchantStoreCache.computeIfAbsent(merchantId, id -> {
            // 首次访问商家库时确保表已建好（幂等）
            createMerchantVectorTable(id);
            return PgVectorStore.builder(pgVectorJdbcTemplate, embeddingModel)
                    .vectorTableName(VectorStoreConfig.MERCHANT_TABLE_PREFIX + id)
                    .dimensions(VectorStoreConfig.EMBEDDING_DIMENSIONS)
                    .initializeSchema(false)
                    .build();
        });
    }

    @Override
    public void createMerchantVectorTable(Long merchantId) {
        // merchantId 为 Long 天然安全，拼接建表 SQL 前仍校验正数，防御注入
        if (merchantId == null || merchantId <= 0) {
            throw new BizException("商家ID非法，无法初始化向量表");
        }
        String tableName = VectorStoreConfig.MERCHANT_TABLE_PREFIX + merchantId;
        pgVectorJdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                // gen_random_uuid 为 PG13+ 内置函数，无需 uuid-ossp 扩展
                + "id uuid DEFAULT gen_random_uuid() PRIMARY KEY, "
                + "content text, "
                + "metadata json, "
                + "embedding vector(" + VectorStoreConfig.EMBEDDING_DIMENSIONS + "))");
        pgVectorJdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + tableName + "_embedding_idx ON "
                + tableName + " USING hnsw (embedding vector_cosine_ops)");
        log.info("商家向量表已初始化（幂等）table={}", tableName);
    }

    // ======================== 嵌入入库与检索 ========================

    @Override
    public String embedDoc(ChatRagDoc doc) {
        try {
            if (doc == null || doc.getId() == null || StrUtil.isBlank(doc.getContent())) {
                return null;
            }
            // 按 Token 切分，避免超长文档超出 embedding 输入限制（M6 的 splitText 为 protected，借道 split(Document)）
            List<Document> chunks = new TokenTextSplitter().split(new Document(doc.getContent()));
            String docId = String.valueOf(doc.getId());
            List<Document> documents = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("docId", docId);
                // PgVectorStore 拒绝 null 值元数据：null 字段一律不入 metadata
                if (StrUtil.isNotBlank(doc.getTitle())) {
                    metadata.put("title", doc.getTitle());
                }
                if (StrUtil.isNotBlank(doc.getCategory())) {
                    metadata.put("category", doc.getCategory());
                }
                if (doc.getMerchantId() != null) {
                    metadata.put("merchantId", String.valueOf(doc.getMerchantId()));
                }
                // 分块主键必须满足 PG uuid 类型（PgVectorStore 强校验），用随机 UUID；
                // 文档归属追踪走 metadata.docId（删除/重建按 docId 过滤）
                documents.add(new Document(java.util.UUID.randomUUID().toString(), chunks.get(i).getText(), metadata));
            }
            getStore(doc.getMerchantId()).add(documents);
            return null;
        } catch (Exception e) {
            // 单文档嵌入失败/向量库整体不可用：不阻断文档主流程，但原因必须对操作方可见（spec：嵌入可靠性）
            String status = classifyEmbedFailure(e);
            log.warn("文档嵌入失败 docId={} status={}: {}", doc == null ? null : doc.getId(), status, e.getMessage());
            return status;
        }
    }

    /** 区分失败类型：向量库整体不可连接 → 未同步（降级）；其余（内容/模型/表结构等）→ 单文档失败 */
    private String classifyEmbedFailure(Exception e) {
        try {
            pgVectorJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "嵌入失败：" + e.getMessage();
        } catch (Exception pgEx) {
            return "向量未同步：向量库暂不可用";
        }
    }

    @Override
    public String search(Long merchantId, String query, int topK) {
        try {
            if (StrUtil.isBlank(query)) {
                return "";
            }
            List<Document> docs = getStore(merchantId)
                    .similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
            if (docs == null || docs.isEmpty()) {
                log.debug("向量检索未命中 merchantId={} topK={}", merchantId, topK);
                return "";
            }
            log.info("向量检索命中 merchantId={} topK={} 命中{}条 首条={}",
                    merchantId, topK, docs.size(),
                    docs.get(0).getMetadata() == null ? "" : Objects.toString(docs.get(0).getMetadata().get("title"), ""));
            StringBuilder sb = new StringBuilder("┌─知识库资料─┐\n");
            for (Document doc : docs) {
                Object title = doc.getMetadata() == null ? null : doc.getMetadata().get("title");
                sb.append("【").append(Objects.toString(title, "")).append("】")
                        .append(truncate(doc.getText(), SEARCH_CONTENT_MAX_LEN))
                        .append("\n\n");
            }
            sb.append("└────┘");
            return sb.toString();
        } catch (Exception e) {
            // PG 不可用时降级为空结果，绝不影响对话流程
            log.warn("向量检索失败 merchantId={}，降级为空结果: {}", merchantId, e.getMessage());
            return "";
        }
    }

    @Override
    public String onDocChanged(ChatRagDoc doc) {
        if (doc == null || doc.getId() == null) {
            return null;
        }
        // 先删后插，天然幂等：重复执行不会产生重复向量
        removeDoc(doc.getId(), doc.getMerchantId());
        if (doc.getEnabled() != null && doc.getEnabled() == 1 && StrUtil.isNotBlank(doc.getContent())) {
            return embedDoc(doc);
        }
        return null;
    }

    @Override
    public void removeDoc(Long docId, Long merchantId) {
        try {
            Filter.Expression expression = new FilterExpressionBuilder()
                    .eq("docId", String.valueOf(docId))
                    .build();
            getStore(merchantId).delete(expression);
        } catch (Exception e) {
            log.warn("删除文档向量失败 docId={}: {}", docId, e.getMessage());
        }
    }

    // ======================== 知识库文档管理 ========================

    /**
     * 文档分页：服务端强制归属隔离——非管理员只能看到并查到归属当前商家的文档
     * （忽略入参 merchantId，不信任前端传参）；管理员维持入参语义（null=全量，可过滤）。
     */
    @Override
    public Page<ChatRagDoc> pageDocs(int pageNum, int pageSize, String title, String category, Long merchantId) {
        Long effectiveMerchantId = isAdmin() ? merchantId : getCurrentMerchant().getId();
        LambdaQueryWrapper<ChatRagDoc> wrapper = new LambdaQueryWrapper<ChatRagDoc>()
                // admin 传 null=全量（不加过滤）；非 admin 强制本人
                .eq(effectiveMerchantId != null, ChatRagDoc::getMerchantId, effectiveMerchantId)
                .like(StrUtil.isNotBlank(title), ChatRagDoc::getTitle, title)
                .like(StrUtil.isNotBlank(category), ChatRagDoc::getCategory, category)
                .orderByDesc(ChatRagDoc::getId);
        return chatRagDocMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public ChatRagDoc saveDoc(KnowledgeDocSaveDTO dto) {
        ChatRagDoc doc = new ChatRagDoc();
        BeanUtil.copyProperties(dto, doc);
        doc.setId(null);
        // 归属规则：管理员可指定 merchantId（null=平台公共文档），商家强制归属当前商家
        doc.setMerchantId(resolveTargetMerchantId(dto.getMerchantId()));
        doc.setEnabled(1);
        chatRagDocMapper.insert(doc);
        // 嵌入结果对操作方可见：null=成功，否则为失败/未同步原因（随响应返回，前端展示）
        doc.setEmbedStatus(onDocChanged(doc));
        return doc;
    }

    @Override
    public ChatRagDoc uploadMdDoc(MultipartFile file, String title, String category,
                                  String keywords, Long merchantId) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!original.toLowerCase().endsWith(".md")) {
            throw new BizException("仅支持 .md 格式的 Markdown 文件");
        }
        String text;
        try {
            text = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new BizException("文件读取失败");
        }
        if (StrUtil.isBlank(text)) {
            throw new BizException("文件内容为空");
        }
        KnowledgeDocSaveDTO dto = new KnowledgeDocSaveDTO();
        dto.setTitle(StrUtil.isNotBlank(title) ? title : original.substring(0, original.length() - 3));
        dto.setContent(stripMarkdown(text));
        dto.setKeywords(keywords);
        dto.setCategory(category);
        dto.setMerchantId(merchantId);
        // 归属与权限完全走 saveDoc 管线（resolveTargetMerchantId 统一判定）
        return saveDoc(dto);
    }

    /** 剥离 Markdown 排版符号（设计 D6）：结构标记去除，链接/图片保留锚文本与 Alt 文本，语义内容完整保留 */
    private String stripMarkdown(String md) {
        String s = md;
        s = s.replaceAll("```[a-zA-Z]*", "");                      // 代码块围栏与语言标记
        s = s.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");    // 图片 → Alt 文本
        s = s.replaceAll("\\[([^\\]]+)\\]\\([^)]*\\)", "$1");     // 链接 → 锚文本
        s = s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "");          // ATX 标题标记
        s = s.replaceAll("(?m)^\\s*>\\s?", "");                    // 引用
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "");               // 无序列表
        s = s.replaceAll("(?m)^\\s*\\d+\\.\\s+", "");             // 有序列表
        s = s.replaceAll("(?m)^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$", ""); // 分隔线
        s = s.replaceAll("(\\*\\*|__)([^*_]+)\\1", "$2");         // 粗体
        s = s.replaceAll("([*_])([^*_\\n]+)\\1", "$2");           // 斜体
        s = s.replace("`", "");                                   // 行内代码标记
        s = s.replaceAll("\\n{3,}", "\n\n");                      // 压缩连续空行
        return s.trim();
    }

    @Override
    public ChatRagDoc updateDoc(Long id, KnowledgeDocSaveDTO dto) {
        ChatRagDoc doc = chatRagDocMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        checkOwner(doc);
        doc.setTitle(dto.getTitle());
        doc.setContent(dto.getContent());
        doc.setKeywords(dto.getKeywords());
        doc.setCategory(dto.getCategory());
        chatRagDocMapper.updateById(doc);
        doc.setEmbedStatus(onDocChanged(doc));
        return doc;
    }

    @Override
    public String changeEnabled(Long id, Integer enabled) {
        ChatRagDoc doc = chatRagDocMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        checkOwner(doc);
        doc.setEnabled(enabled);
        chatRagDocMapper.updateById(doc);
        // 禁用即仅删向量，启用则重新嵌入；状态随响应返回
        return onDocChanged(doc);
    }

    @Override
    public void deleteDoc(Long id) {
        ChatRagDoc doc = chatRagDocMapper.selectById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        checkOwner(doc);
        chatRagDocMapper.deleteById(id);
        removeDoc(doc.getId(), doc.getMerchantId());
    }

    // ======================== 辅助方法 ========================

    /** 当前用户是否为平台管理员 */
    private boolean isAdmin() {
        List<String> roles = UserContext.getRoles();
        return roles != null && roles.contains(ADMIN_ROLE_CODE);
    }

    /** 当前登录用户对应的商家，不存在则抛业务异常 */
    private Merchant getCurrentMerchant() {
        Merchant merchant = merchantMapper.selectOne(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getUserId, UserContext.getUserId()));
        if (merchant == null) {
            throw new BizException("当前账号不是商家");
        }
        return merchant;
    }

    /** 解析文档归属商家：管理员用入参（null=平台），商家强制归属自己 */
    private Long resolveTargetMerchantId(Long requestedMerchantId) {
        if (isAdmin()) {
            return requestedMerchantId;
        }
        return getCurrentMerchant().getId();
    }

    /** 校验文档操作权限：管理员可操作全部，商家只能操作归属自己的文档 */
    private void checkOwner(ChatRagDoc doc) {
        if (isAdmin()) {
            return;
        }
        Merchant merchant = getCurrentMerchant();
        if (doc.getMerchantId() == null || !doc.getMerchantId().equals(merchant.getId())) {
            throw new BizException("无权操作该文档");
        }
    }

    /** 超长内容截断 */
    private String truncate(String content, int maxLen) {
        if (content == null) {
            return "";
        }
        return content.length() > maxLen ? content.substring(0, maxLen) + "..." : content;
    }
}
