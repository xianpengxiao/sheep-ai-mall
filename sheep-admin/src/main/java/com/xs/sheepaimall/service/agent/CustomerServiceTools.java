package com.xs.sheepaimall.service.agent;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xs.sheepaimall.dto.CartAddDTO;
import com.xs.sheepaimall.entity.ChatPendingOp;
import com.xs.sheepaimall.entity.OrderInfo;
import com.xs.sheepaimall.entity.OrderItem;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.service.CartService;
import com.xs.sheepaimall.service.OrderItemService;
import com.xs.sheepaimall.service.OrderService;
import com.xs.sheepaimall.service.PendingOperationService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.service.impl.AuditService;
import com.xs.sheepaimall.service.impl.ChatSessionStateService;
import com.xs.sheepaimall.vo.CartVO;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * 智能客服工具集（每次请求 new 的普通类，非 Spring Bean）。
 * <p>身份（userId / merchantId / sessionId）在构造时由 MVC 线程从 UserContext 与会话现场捕获——
 * ChatClient 的工具调用在 Reactor 线程执行，UserContext ThreadLocal 在那里不可见；
 * 且 MethodToolCallback 会把方法签名暴露给 LLM，身份绝不能成为入参。</p>
 * <p>读工具直接执行；写工具只生成待确认操作单（{@link PendingOperationService}），绝不真正执行，
 * 创建成功的操作单会放入 pendingOpQueue，由调用方在 SSE 末尾下发结构化标记。</p>
 */
public class CustomerServiceTools {

    /** 单条工具输出最大字符数，超出截断并注明 */
    private static final int MAX_OUTPUT_LENGTH = 1000;
    private static final int ORDER_SUMMARY_LIMIT = 10;
    private static final int PRODUCT_SEARCH_LIMIT = 10;

    /** 单次最多下发的卡片条数（对齐需求：商品/订单卡片每次最多 2 张） */
    private static final int CARD_LIMIT = 2;

    private final Long userId;
    private final Long merchantId;
    private final Long sessionId;
    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final CartService cartService;
    private final SpuService spuService;
    private final SkuService skuService;
    private final PendingOperationService pendingOperationService;
    /** 客服会话状态机（构造时注入，requestHumanService 用） */
    private final ChatSessionStateService chatSessionStateService;
    /** 本次调用中创建的操作单通知队列（构造时传入，doOnComplete 时消费） */
    private final ConcurrentLinkedQueue<ChatPendingOp> pendingOpQueue;
    /** 商品卡片队列：searchProducts 命中时入队结构化数据（最多2条），流末尾随 [[GOODS_CARDS:...]] 标记下发 */
    private final ConcurrentLinkedQueue<Map<String, Object>> goodsCardQueue;
    /** 订单卡片队列：queryMyOrders/queryOrderDetail 入队（最多2条），随 [[ORDER_CARDS:...]] 标记下发 */
    private final ConcurrentLinkedQueue<Map<String, Object>> orderCardQueue;

    public CustomerServiceTools(Long userId, Long merchantId, Long sessionId,
                                OrderService orderService, OrderItemService orderItemService,
                                CartService cartService, SpuService spuService, SkuService skuService,
                                PendingOperationService pendingOperationService,
                                ChatSessionStateService chatSessionStateService,
                                ConcurrentLinkedQueue<ChatPendingOp> pendingOpQueue,
                                ConcurrentLinkedQueue<Map<String, Object>> goodsCardQueue,
                                ConcurrentLinkedQueue<Map<String, Object>> orderCardQueue) {
        this.userId = userId;
        this.merchantId = merchantId;
        this.sessionId = sessionId;
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.cartService = cartService;
        this.spuService = spuService;
        this.skuService = skuService;
        this.pendingOperationService = pendingOperationService;
        this.chatSessionStateService = chatSessionStateService;
        this.pendingOpQueue = pendingOpQueue;
        this.goodsCardQueue = goodsCardQueue;
        this.orderCardQueue = orderCardQueue;
    }

    // ==================== 读工具（直接执行） ====================

    /**
     * 查询当前用户最近的订单摘要列表（订单号、状态、金额、下单时间）。
     * 当用户询问"我的订单"、"订单到哪了"等问题时使用。
     */
    @Tool(description = "查询当前用户最近的订单摘要列表（订单号、状态、金额、下单时间），用于回答订单相关问题")
    public String queryMyOrders() {
        List<OrderInfo> orders = orderService.listByUserId(userId);
        if (orders.isEmpty()) {
            return wrap("订单数据", "该用户暂无订单");
        }
        // 前 2 条入队订单卡片数据
        orders.stream().limit(CARD_LIMIT).forEach(this::offerOrderCard);
        String body = orders.stream()
                .limit(ORDER_SUMMARY_LIMIT)
                .map(o -> "订单ID:" + o.getId()
                        + " 订单号:" + o.getOrderNo()
                        + " 状态:" + statusText(o.getStatus())
                        + " 金额:" + o.getTotalAmount() + "元"
                        + " 下单时间:" + o.getCreateTime())
                .collect(Collectors.joining("\n"));
        return wrap("订单数据", body);
    }

    /**
     * 查询指定订单的详细信息（含商品明细）。
     * 当用户想查看某个订单的商品、金额、收货信息时使用。
     */
    @Tool(description = "查询指定订单的详细信息（含商品明细、收货信息），orderId 为订单ID")
    public String queryOrderDetail(@ToolParam(description = "订单ID") Long orderId) {
        // 不用 OrderService.getDetailById：其内部走 UserContext 做归属校验，而 Reactor 线程上 ThreadLocal 不可见
        OrderInfo order = orderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return wrap("订单详情", "未找到该订单");
        }
        List<OrderItem> items = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        offerOrderCard(order);
        StringBuilder sb = new StringBuilder();
        sb.append("订单号:").append(order.getOrderNo())
                .append(" 状态:").append(statusText(order.getStatus()))
                .append(" 金额:").append(order.getTotalAmount()).append("元")
                .append(" 收货人:").append(order.getReceiverName())
                .append(" 地址:").append(order.getReceiverAddress())
                .append(" 下单时间:").append(order.getCreateTime());
        if (!items.isEmpty()) {
            sb.append("\n商品明细:");
            items.forEach(it -> sb.append("\n- ").append(it.getSkuName())
                    .append(" 单价:").append(it.getPrice()).append("元")
                    .append(" 数量:").append(it.getQuantity()));
        }
        return wrap("订单详情", sb.toString());
    }

    /**
     * 按关键词搜索在售商品（名称/副标题模糊匹配，按销量排序）。
     * 当用户想找商品、比价、求推荐时使用；会话归属商家时结果自动收敛到该商家。
     */
    @Tool(description = "按关键词搜索在售商品（名称、副标题匹配，按销量排序，最多返回10条），用于商品推荐与查找")
    public String searchProducts(@ToolParam(description = "搜索关键词，如 蓝牙耳机") String keyword) {
        // 不用 ProductSearchService（ES）：其不支持商家过滤且依赖 ES 可用性，直接走 SpuService 条件查询最简
        List<Spu> spus = spuService.lambdaQuery()
                .eq(Spu::getStatus, 1)
                .eq(Spu::getAuditStatus, 1)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Spu::getName, keyword).or().like(Spu::getSubTitle, keyword))
                .eq(merchantId != null, Spu::getMerchantId, merchantId)
                .orderByDesc(Spu::getSalesCount)
                .last("LIMIT " + PRODUCT_SEARCH_LIMIT)
                .list();
        if (spus.isEmpty()) {
            return wrap("商品数据", "未找到相关商品");
        }
        // 前 2 条入队卡片数据（结构化标记随 SSE 下发，前端渲染商品卡片）
        spus.stream().limit(CARD_LIMIT).forEach(this::offerGoodsCard);
        String body = spus.stream()
                .map(p -> "商品ID:" + p.getId()
                        + " 名称:" + p.getName()
                        + (StrUtil.isNotBlank(p.getSubTitle()) ? " 副标题:" + p.getSubTitle() : "")
                        + " 销量:" + p.getSalesCount())
                .collect(Collectors.joining("\n"));
        return wrap("商品数据", body);
    }

    /**
     * 查询当前用户的购物车内容（商品、规格、数量、单价）。
     * 当用户询问购物车、想结算时使用。
     */
    @Tool(description = "查询当前用户的购物车内容（商品名称、规格、数量、单价）")
    public String queryCart() {
        List<CartVO> carts = cartService.listByUserId(userId);
        if (carts.isEmpty()) {
            return wrap("购物车数据", "购物车是空的");
        }
        String body = carts.stream()
                .map(c -> c.getSpuName() + " " + (c.getSkuName() == null ? "" : c.getSkuName())
                        + " 数量:" + c.getQuantity()
                        + " 单价:" + c.getPrice() + "元")
                .collect(Collectors.joining("\n"));
        return wrap("购物车数据", body);
    }

    // ==================== 写工具（只生成待确认操作单，绝不执行） ====================

    /**
     * 修改用户收货地址。只生成待确认操作单，需用户确认后才真正执行；
     * 创建成功后返回引导文案，操作单信息会随 SSE 下发给前端渲染确认卡片。
     */
    @Tool(description = "修改当前用户的收货地址。只生成待确认操作单，需用户确认后才执行")
    public String updateAddress(
            @ToolParam(description = "要修改的地址ID") Long addressId,
            @ToolParam(description = "收货人姓名") String receiverName,
            @ToolParam(description = "收货人手机号") String receiverPhone,
            @ToolParam(description = "省份") String province,
            @ToolParam(description = "城市") String city,
            @ToolParam(description = "区县") String district,
            @ToolParam(description = "详细地址") String detailAddress) {
        // 参数 key 与 AddressSaveDTO 字段名对齐，confirm 时直接 toBean 反序列化
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("id", addressId);
        args.put("receiverName", receiverName);
        args.put("receiverPhone", receiverPhone);
        args.put("province", province);
        args.put("city", city);
        args.put("district", district);
        args.put("detailAddress", detailAddress);
        String displayText = "修改收货地址：" + province + city + district + detailAddress
                + "（" + receiverName + " " + receiverPhone + "）";
        createPendingOp(PendingOperationService.OP_UPDATE_ADDRESS, args, displayText);
        return "已为该请求生成待确认操作，请告知用户确认。";
    }

    /**
     * 取消指定订单（仅待支付订单可取消）。只生成待确认操作单，需用户确认后才真正执行。
     */
    @Tool(description = "取消当前用户的指定订单（仅待支付状态可取消）。只生成待确认操作单，需用户确认后才执行")
    public String cancelOrder(@ToolParam(description = "订单ID") Long orderId) {
        OrderInfo order = orderService.getById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            return "未找到该订单，请告知用户核对订单ID。";
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("orderId", orderId);
        String displayText = "取消订单：" + order.getOrderNo() + "（金额" + order.getTotalAmount() + "元）";
        createPendingOp(PendingOperationService.OP_CANCEL_ORDER, args, displayText);
        return "已为该请求生成待确认操作，请告知用户确认。";
    }

    /**
     * 将指定商品规格加入购物车。只生成待确认操作单，需用户确认后才真正执行。
     */
    @Tool(description = "将指定商品规格加入当前用户购物车。只生成待确认操作单，需用户确认后才执行")
    public String addToCart(
            @ToolParam(description = "SKU ID（商品规格ID）") Long skuId,
            @ToolParam(description = "数量") Integer quantity) {
        var sku = skuService.getById(skuId);
        if (sku == null || sku.getStatus() == null || sku.getStatus() != 1) {
            return "该商品规格不存在或已下架，请告知用户重新选择。";
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("spuId", sku.getSpuId());
        args.put("skuId", skuId);
        args.put("quantity", quantity == null || quantity <= 0 ? 1 : quantity);
        String displayText = "加入购物车：" + sku.getSkuName() + " x" + args.get("quantity");
        createPendingOp(PendingOperationService.OP_ADD_CART, args, displayText);
        return "已为该请求生成待确认操作，请告知用户确认。";
    }

    // ==================== 状态工具（即时执行，不进确认闸） ====================

    /**
     * 将当前会话转接人工客服。当用户问题无法有效回答时使用。
     * <p>设计 D3：会话状态变更为可逆操作（坐席可转回/超时自动转回），即时执行、
     * 不生成待确认操作单。transfer 的归属校验基于构造时捕获的 userId，
     * Reactor 线程上无 UserContext 也不受影响；transfer 内部已做幂等守卫。</p>
     */
    @Tool(description = "将当前会话转接人工客服。当用户问题无法有效回答、用户明确要求真人客服或问题需人工处理时使用")
    public String requestHumanService() {
        chatSessionStateService.transfer(sessionId, userId, AuditService.ACTOR_USER);
        return "已为用户转接人工客服，请告知用户人工客服将尽快接入";
    }

    // ==================== 内部辅助 ====================

    /** 创建操作单并放入通知队列 */
    private void createPendingOp(String opType, Map<String, Object> args, String displayText) {
        ChatPendingOp op = pendingOperationService.create(userId, sessionId, opType, args, displayText);
        pendingOpQueue.offer(op);
    }

    /** 商品卡片入队：附带第一个在售 SKU（卡片"加入购物车"默认规格；无在售 SKU 则卡片无加购按钮） */
    private void offerGoodsCard(Spu spu) {
        var sku = skuService.lambdaQuery()
                .eq(Sku::getSpuId, spu.getId())
                .eq(Sku::getStatus, 1)
                .orderByAsc(Sku::getPrice)
                .last("LIMIT 1")
                .one();
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("spuId", spu.getId());
        card.put("name", spu.getName());
        card.put("price", sku != null && sku.getPrice() != null ? sku.getPrice().toPlainString()
                : (spu.getMinPrice() != null ? spu.getMinPrice().toPlainString() : null));
        card.put("salesCount", spu.getSalesCount());
        card.put("image", StrUtil.isNotBlank(spu.getMainImage()) ? spu.getMainImage()
                : (sku != null ? sku.getImage() : null));
        if (sku != null) {
            card.put("skuId", sku.getId());
            card.put("skuName", sku.getSkuName());
        }
        goodsCardQueue.offer(card);
    }

    /** 订单卡片入队 */
    private void offerOrderCard(OrderInfo order) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("orderId", order.getId());
        card.put("orderNo", order.getOrderNo());
        card.put("statusText", statusText(order.getStatus()));
        card.put("totalAmount", order.getTotalAmount() != null ? order.getTotalAmount().toPlainString() : null);
        card.put("createTime", order.getCreateTime() != null ? order.getCreateTime().toString() : null);
        orderCardQueue.offer(card);
    }

    /** 读工具输出统一包裹定界符并截断（单条 ≤1000 字符），标注为数据而非指令 */
    private String wrap(String title, String body) {
        String content = (body == null || body.isEmpty()) ? "（无数据）" : body;
        if (content.length() > MAX_OUTPUT_LENGTH) {
            content = content.substring(0, MAX_OUTPUT_LENGTH) + "\n（内容过长已截断）";
        }
        return "┌─" + title + "─┐\n" + content + "\n└────┘";
    }

    /** 订单状态文案，与 OrderServiceImpl 保持一致 */
    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待支付";
            case 1 -> "已支付";
            case 2 -> "已发货";
            case 3 -> "已完成";
            case 4 -> "已取消";
            default -> "未知";
        };
    }
}
