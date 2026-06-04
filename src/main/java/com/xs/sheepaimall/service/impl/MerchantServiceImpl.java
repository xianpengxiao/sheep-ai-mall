package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.CacheHelper;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.*;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.MerchantApplyMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.mapper.OrderInfoMapper;
import com.xs.sheepaimall.mapper.SysUserRoleMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.MerchantService;
import com.xs.sheepaimall.service.OrderItemService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements MerchantService {

    /** 商家角色ID（对应 sys_role.id=4, ROLE_MERCHANT） */
    private static final long MERCHANT_ROLE_ID = 4L;

    @Resource
    private MerchantApplyMapper merchantApplyMapper;

    @Resource
    private OrderInfoMapper orderInfoMapper;

    @Resource
    private SpuService spuService;

    @Resource
    private SkuService skuService;

    @Resource
    private OrderItemService orderItemService;

    @Resource
    private SysUserRoleMapper sysUserRoleMapper;

    @Resource
    private CacheHelper cacheHelper;

    // ==================== 买家端 ====================

    @Override
    public Page<MerchantVO> pageMerchant(int pageNum, int pageSize, String shopName, String businessScope) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, 1)
                .like(StrUtil.isNotBlank(shopName), Merchant::getShopName, shopName)
                .like(StrUtil.isNotBlank(businessScope), Merchant::getBusinessScope, businessScope)
                .orderByDesc(Merchant::getCreateTime);

        Page<Merchant> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        List<MerchantVO> voList = page.getRecords().stream()
                .map(this::toSimpleVO)
                .collect(Collectors.toList());

        Page<MerchantVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public MerchantVO getMerchantDetail(Long id) {
        Merchant merchant = this.getById(id);
        if (merchant == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商家不存在");
        }
        MerchantVO vo = toSimpleVO(merchant);

        List<Spu> goods = spuService.list(new LambdaQueryWrapper<Spu>()
                .eq(Spu::getMerchantId, id)
                .eq(Spu::getStatus, 1)
                .orderByDesc(Spu::getSalesCount));
        vo.setGoodsList(goods.stream().map(this::toSpuVO).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public Page<Spu> pageMerchantGoods(Long merchantId, int pageNum, int pageSize) {
        Merchant merchant = this.getById(merchantId);
        if (merchant == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商家不存在");
        }
        return spuService.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchantId)
                        .eq(Spu::getStatus, 1)
                        .orderByDesc(Spu::getSalesCount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(MerchantApplyDTO dto) {
        Long userId = UserContext.getUserId();

        Long existingCount = this.lambdaQuery()
                .eq(Merchant::getUserId, userId)
                .in(Merchant::getStatus, 0, 1)
                .count();
        if (existingCount > 0) {
            throw new BizException("您已有待审核或已开通的店铺，请勿重复申请");
        }

        MerchantApply apply = new MerchantApply();
        BeanUtil.copyProperties(dto, apply);
        apply.setUserId(userId);
        apply.setStatus(0);
        merchantApplyMapper.insert(apply);

        log.info("商家入驻申请已提交 userId={}, applyId={}", userId, apply.getId());
    }

    // ==================== 商家后台 ====================

    /** 获取当前登录用户对应的已开通商家 */
    private Merchant getCurrentMerchant() {
        Long userId = UserContext.getUserId();
        Merchant merchant = this.lambdaQuery()
                .eq(Merchant::getUserId, userId)
                .one();
        if (merchant == null) {
            throw new BizException("您还不是商家，请先提交入驻申请");
        }
        if (merchant.getStatus() != 1) {
            throw new BizException("店铺状态异常，请联系管理员");
        }
        return merchant;
    }

    @Override
    public MerchantVO getMyShopInfo() {
        return toSimpleVO(getCurrentMerchant());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantVO updateMyShop(MerchantUpdateDTO dto) {
        Merchant merchant = getCurrentMerchant();
        BeanUtil.copyProperties(dto, merchant, "id", "userId", "status");
        this.updateById(merchant);
        log.info("商家信息已更新 merchantId={}", merchant.getId());
        return toSimpleVO(merchant);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuVO addGoods(SpuSaveDTO dto) {
        Merchant merchant = getCurrentMerchant();
        Spu spu = new Spu();
        BeanUtil.copyProperties(dto, spu);
        spu.setId(null);
        spu.setMerchantId(merchant.getId());
        if (dto.getImageList() != null) {
            spu.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }
        spuService.save(spu);

        if (dto.getSkuList() != null && !dto.getSkuList().isEmpty()) {
            List<Sku> skuList = dto.getSkuList().stream()
                    .map(this::toSkuEntity)
                    .collect(Collectors.toList());
            skuList.forEach(s -> s.setSpuId(spu.getId()));
            skuService.batchSaveOrUpdate(spu.getId(), skuList);
        }

        cacheHelper.evictSpuHotPage();
        return spuService.getDetailById(spu.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuVO updateGoods(Long id, SpuSaveDTO dto) {
        Merchant merchant = getCurrentMerchant();
        Spu existSpu = spuService.getById(id);
        if (existSpu == null || !merchant.getId().equals(existSpu.getMerchantId())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在或不属于您的店铺");
        }

        Spu spu = new Spu();
        BeanUtil.copyProperties(dto, spu);
        spu.setId(id);
        spu.setMerchantId(merchant.getId());
        if (dto.getImageList() != null) {
            spu.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }
        spuService.updateById(spu);

        if (dto.getSkuList() != null) {
            List<Sku> skuList = dto.getSkuList().stream()
                    .map(this::toSkuEntity)
                    .collect(Collectors.toList());
            skuList.forEach(s -> s.setSpuId(spu.getId()));
            skuService.batchSaveOrUpdate(spu.getId(), skuList);
        }

        cacheHelper.evictSpuDetail(id);
        cacheHelper.evictSpuHotPage();
        return spuService.getDetailById(spu.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGoodsStatus(Long id, Integer status) {
        Merchant merchant = getCurrentMerchant();
        Spu existSpu = spuService.getById(id);
        if (existSpu == null || !merchant.getId().equals(existSpu.getMerchantId())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在或不属于您的店铺");
        }
        spuService.updateStatus(id, status);
    }

    @Override
    public Page<Spu> pageMyGoods(int pageNum, int pageSize, String keyword) {
        Merchant merchant = getCurrentMerchant();
        return spuService.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchant.getId())
                        .like(StrUtil.isNotBlank(keyword), Spu::getName, keyword)
                        .orderByDesc(Spu::getCreateTime));
    }

    @Override
    public Page<MerchantOrderVO> pageMyOrders(int pageNum, int pageSize, Integer status) {
        Merchant merchant = getCurrentMerchant();
        List<Long> spuIds = spuService.listObjs(
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchant.getId())
                        .select(Spu::getId),
                o -> ((Long) o));
        if (spuIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 查该店铺 SPU 涉及的 order_item
        List<OrderItem> allItems = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().in(OrderItem::getSpuId, spuIds));
        if (allItems.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 按 orderId 分组，只保留该店铺的商品明细
        Map<Long, List<OrderItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Set<Long> orderIds = itemMap.keySet();

        // 分页查订单
        Page<OrderInfo> orderPage = orderInfoMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OrderInfo>()
                        .in(OrderInfo::getId, orderIds)
                        .eq(status != null, OrderInfo::getStatus, status)
                        .orderByDesc(OrderInfo::getCreateTime));

        // 批量查 SPU 名称
        Set<Long> itemSpuIds = allItems.stream().map(OrderItem::getSpuId).collect(Collectors.toSet());
        Map<Long, String> spuNameMap = spuService.listByIds(itemSpuIds).stream()
                .collect(Collectors.toMap(Spu::getId, Spu::getName, (a, b) -> a));

        List<MerchantOrderVO> voList = orderPage.getRecords().stream()
                .map(order -> {
                    MerchantOrderVO vo = new MerchantOrderVO();
                    BeanUtil.copyProperties(order, vo);
                    vo.setStatusText(getStatusText(order.getStatus()));

                    List<OrderItem> shopItems = itemMap.getOrDefault(order.getId(), Collections.emptyList());
                    vo.setItems(shopItems.stream().map(item -> {
                        OrderItemVO iv = new OrderItemVO();
                        BeanUtil.copyProperties(item, iv);
                        iv.setSpuName(spuNameMap.get(item.getSpuId()));
                        return iv;
                    }).collect(Collectors.toList()));
                    return vo;
                })
                .collect(Collectors.toList());

        Page<MerchantOrderVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(orderPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    public MerchantOrderVO getMyOrderDetail(Long orderId) {
        Merchant merchant = getCurrentMerchant();
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        // 只返回该店铺的商品项
        List<OrderItem> allItems = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId));
        List<Long> mySpuIds = spuService.listObjs(
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchant.getId())
                        .select(Spu::getId),
                o -> ((Long) o));

        List<OrderItem> shopItems = allItems.stream()
                .filter(item -> mySpuIds.contains(item.getSpuId()))
                .collect(Collectors.toList());

        // SPU 名
        Set<Long> spuIds = shopItems.stream().map(OrderItem::getSpuId).collect(Collectors.toSet());
        Map<Long, String> spuNameMap = spuService.listByIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, Spu::getName, (a, b) -> a));

        MerchantOrderVO vo = new MerchantOrderVO();
        BeanUtil.copyProperties(order, vo);
        vo.setStatusText(getStatusText(order.getStatus()));
        vo.setItems(shopItems.stream().map(item -> {
            OrderItemVO iv = new OrderItemVO();
            BeanUtil.copyProperties(item, iv);
            iv.setSpuName(spuNameMap.get(item.getSpuId()));
            return iv;
        }).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverOrder(Long orderId, String deliveryCompany, String deliveryNo) {
        Merchant merchant = getCurrentMerchant();

        // 校验订单包含该店铺的商品
        List<Long> mySpuIds = spuService.listObjs(
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchant.getId())
                        .select(Spu::getId),
                o -> ((Long) o));
        if (mySpuIds.isEmpty()) {
            throw new BizException("订单中无本店铺商品");
        }

        List<OrderItem> items = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .in(OrderItem::getSpuId, mySpuIds));
        if (items.isEmpty()) {
            throw new BizException("该订单不包含本店铺的商品");
        }

        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BizException("仅已支付订单可发货，当前状态：" + getStatusText(order.getStatus()));
        }

        order.setStatus(2);
        order.setDeliveryTime(LocalDateTime.now());
        orderInfoMapper.updateById(order);
        log.info("商家发货 orderId={}, merchantId={}, company={}, no={}",
                orderId, merchant.getId(), deliveryCompany, deliveryNo);
    }

    @Override
    public IncomeStatVO getIncomeStat() {
        Merchant merchant = getCurrentMerchant();

        // 该店铺所有 SPU ID
        List<Long> spuIds = spuService.listObjs(
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchant.getId())
                        .select(Spu::getId),
                o -> ((Long) o));
        if (spuIds.isEmpty()) {
            IncomeStatVO empty = new IncomeStatVO();
            empty.setTodayAmount(BigDecimal.ZERO);
            empty.setTodayOrderCount(0);
            empty.setMonthAmount(BigDecimal.ZERO);
            empty.setMonthOrderCount(0);
            empty.setTotalAmount(BigDecimal.ZERO);
            empty.setTotalOrderCount(0);
            return empty;
        }

        // 查这些 SPU 涉及的已支付订单明细
        List<OrderItem> paidItems = orderItemService.list(
                new LambdaQueryWrapper<OrderItem>()
                        .in(OrderItem::getSpuId, spuIds));
        if (paidItems.isEmpty()) {
            IncomeStatVO empty = new IncomeStatVO();
            empty.setTodayAmount(BigDecimal.ZERO);
            empty.setTodayOrderCount(0);
            empty.setMonthAmount(BigDecimal.ZERO);
            empty.setMonthOrderCount(0);
            empty.setTotalAmount(BigDecimal.ZERO);
            empty.setTotalOrderCount(0);
            return empty;
        }

        Set<Long> orderIds = paidItems.stream().map(OrderItem::getOrderId).collect(Collectors.toSet());

        // 查已支付/已发货/已完成的订单
        List<OrderInfo> paidOrders = orderInfoMapper.selectList(
                new LambdaQueryWrapper<OrderInfo>()
                        .in(OrderInfo::getId, orderIds)
                        .in(OrderInfo::getStatus, 1, 2, 3));

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        BigDecimal todayAmount = BigDecimal.ZERO;
        int todayCount = 0;
        BigDecimal monthAmount = BigDecimal.ZERO;
        int monthCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderInfo o : paidOrders) {
            BigDecimal amount = o.getPayAmount() != null ? o.getPayAmount() : BigDecimal.ZERO;
            totalAmount = totalAmount.add(amount);

            LocalDateTime payTime = o.getPayTime();
            if (payTime != null) {
                if (!payTime.isBefore(todayStart) && !payTime.isAfter(todayEnd)) {
                    todayAmount = todayAmount.add(amount);
                    todayCount++;
                }
                if (!payTime.isBefore(monthStart)) {
                    monthAmount = monthAmount.add(amount);
                    monthCount++;
                }
            }
        }

        IncomeStatVO stat = new IncomeStatVO();
        stat.setTodayAmount(todayAmount);
        stat.setTodayOrderCount(todayCount);
        stat.setMonthAmount(monthAmount);
        stat.setMonthOrderCount(monthCount);
        stat.setTotalAmount(totalAmount);
        stat.setTotalOrderCount(paidOrders.size());
        return stat;
    }

    // ==================== 平台管理 ====================

    @Override
    public Page<MerchantVO> pageAllMerchant(int pageNum, int pageSize, Integer status, String keyword) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(status != null, Merchant::getStatus, status)
                .and(StrUtil.isNotBlank(keyword), w -> w
                        .like(Merchant::getShopName, keyword)
                        .or()
                        .like(Merchant::getContactName, keyword)
                        .or()
                        .like(Merchant::getContactPhone, keyword))
                .orderByDesc(Merchant::getCreateTime);

        Page<Merchant> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        List<MerchantVO> voList = page.getRecords().stream()
                .map(this::toSimpleVO)
                .collect(Collectors.toList());

        Page<MerchantVO> result = new Page<>(pageNum, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditApply(Long applyId, MerchantAuditDTO dto) {
        MerchantApply apply = merchantApplyMapper.selectById(applyId);
        if (apply == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "申请记录不存在");
        }
        if (apply.getStatus() != 0) {
            throw new BizException("该申请已审核，请勿重复操作");
        }

        Long adminUserId = UserContext.getUserId();
        apply.setStatus(dto.getStatus());
        apply.setAuditRemark(dto.getAuditRemark());
        apply.setAuditUserId(adminUserId);
        apply.setAuditTime(LocalDateTime.now());
        merchantApplyMapper.updateById(apply);

        if (dto.getStatus() == 1) {
            // 审核通过 → 创建商家记录并分配商家角色
            Merchant merchant = this.lambdaQuery()
                    .eq(Merchant::getUserId, apply.getUserId())
                    .one();
            if (merchant == null) {
                merchant = new Merchant();
                merchant.setUserId(apply.getUserId());
                merchant.setShopName(apply.getShopName());
                merchant.setBusinessLicense(apply.getBusinessLicense());
                merchant.setBusinessScope(apply.getBusinessScope());
                merchant.setContactName(apply.getContactName());
                merchant.setContactPhone(apply.getContactPhone());
                merchant.setStatus(1);
                merchant.setAuditTime(LocalDateTime.now());
                this.save(merchant);
            } else {
                merchant.setShopName(apply.getShopName());
                merchant.setBusinessLicense(apply.getBusinessLicense());
                merchant.setBusinessScope(apply.getBusinessScope());
                merchant.setContactName(apply.getContactName());
                merchant.setContactPhone(apply.getContactPhone());
                merchant.setStatus(1);
                merchant.setAuditRemark(null);
                merchant.setAuditTime(LocalDateTime.now());
                this.updateById(merchant);
            }

            // 分配商家角色
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(apply.getUserId());
            userRole.setRoleId(MERCHANT_ROLE_ID);
            sysUserRoleMapper.insert(userRole);

            log.info("商家入驻审核通过 applyId={}, userId={}, merchantId={}",
                    applyId, apply.getUserId(), merchant.getId());
        } else {
            log.info("商家入驻审核驳回 applyId={}, reason={}", applyId, dto.getAuditRemark());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleMerchantStatus(Long id, Integer status) {
        Merchant merchant = this.getById(id);
        if (merchant == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商家不存在");
        }
        merchant.setStatus(status);
        this.updateById(merchant);
        log.info("商家状态已变更 merchantId={}, status={}", id, status);
    }

    // ==================== 内部方法 ====================

    private MerchantVO toSimpleVO(Merchant merchant) {
        MerchantVO vo = new MerchantVO();
        BeanUtil.copyProperties(merchant, vo);
        return vo;
    }

    private SpuVO toSpuVO(Spu spu) {
        SpuVO vo = new SpuVO();
        BeanUtil.copyProperties(spu, vo);
        if (StrUtil.isNotBlank(spu.getImageList())) {
            vo.setImageList(JSONUtil.toList(spu.getImageList(), String.class));
        }
        return vo;
    }

    private Sku toSkuEntity(SkuSaveDTO dto) {
        Sku sku = new Sku();
        BeanUtil.copyProperties(dto, sku);
        if (dto.getSpecInfo() != null) {
            sku.setSpecInfo(JSONUtil.toJsonStr(dto.getSpecInfo()));
        }
        return sku;
    }

    private String getStatusText(Integer status) {
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
