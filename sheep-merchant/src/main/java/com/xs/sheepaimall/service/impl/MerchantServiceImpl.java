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
import com.xs.sheepaimall.mapper.MerchantInfoChangeMapper;
import com.xs.sheepaimall.mapper.MerchantMapper;
import com.xs.sheepaimall.feign.AuthFeignClient;
import com.xs.sheepaimall.feign.OrderFeignClient;
import com.xs.sheepaimall.feign.OssFeignClient;
import com.xs.sheepaimall.feign.ProductFeignClient;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.FundService;
import com.xs.sheepaimall.service.MerchantDsrService;
import com.xs.sheepaimall.service.MerchantService;
import com.xs.sheepaimall.util.SensitiveWordUtil;
import com.xs.sheepaimall.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private MerchantApplyMapper merchantApplyMapper;

    @Autowired
    private MerchantInfoChangeMapper merchantInfoChangeMapper;

    @Autowired
    private CacheHelper cacheHelper;

    @Autowired
    private MerchantDsrService merchantDsrService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private FundService fundService;

    @Autowired
    private SensitiveWordUtil sensitiveWordUtil;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private AuthFeignClient authFeignClient;

    @Autowired
    private OssFeignClient ossFeignClient;

    // ==================== 买家端 ====================

    @Override
    public Page<MerchantVO> pageMerchant(int pageNum, int pageSize, String shopName, String businessScope) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, 1)
                .eq(Merchant::getShopStatus, 1)
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
        return toSimpleVO(merchant);
    }

    @Override
    public Page<Spu> pageMerchantGoods(Long merchantId, int pageNum, int pageSize) {
        Merchant merchant = this.getById(merchantId);
        if (merchant == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商家不存在");
        }
        Page<Spu> page = productFeignClient.pageSpuByMerchant(merchantId, pageNum, pageSize,
                null, null, 1);

        // 批量填充最低价
        if (!page.getRecords().isEmpty()) {
            List<Long> spuIds = page.getRecords().stream().map(Spu::getId).collect(Collectors.toList());
            List<Sku> allSkus = productFeignClient.listSkuBySpuIds(spuIds);
            Map<Long, List<Sku>> skuMap = allSkus.stream()
                    .collect(Collectors.groupingBy(Sku::getSpuId));
            for (Spu spu : page.getRecords()) {
                List<Sku> skus = skuMap.get(spu.getId());
                if (skus == null || skus.isEmpty()) continue;
                List<Sku> activeSkus = skus.stream()
                        .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                        .collect(Collectors.toList());
                if (activeSkus.isEmpty()) continue;
                spu.setMinPrice(activeSkus.stream()
                        .map(Sku::getPrice)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null));
            }
        }

        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(MerchantApplyDTO dto) {
        Long userId = UserContext.getUserId();

        // 已开通或待审核商家不能重复申请
        Long existingMerchant = this.lambdaQuery()
                .eq(Merchant::getUserId, userId)
                .in(Merchant::getStatus, 0, 1)
                .count();
        if (existingMerchant > 0) {
            throw new BizException("您已有待审核或已开通的店铺，请勿重复申请");
        }

        // 查找历史驳回的申请，删除旧资质图片
        MerchantApply oldApply = merchantApplyMapper.selectOne(
                new LambdaQueryWrapper<MerchantApply>()
                        .eq(MerchantApply::getUserId, userId)
                        .orderByDesc(MerchantApply::getCreateTime)
                        .last("LIMIT 1"));
        if (oldApply != null && oldApply.getBusinessLicense() != null
                && !oldApply.getBusinessLicense().equals(dto.getBusinessLicense())) {
            ossFeignClient.deleteByUrl(oldApply.getBusinessLicense());
        }

        MerchantApply apply = new MerchantApply();
        BeanUtil.copyProperties(dto, apply);
        apply.setUserId(userId);
        apply.setStatus(0);
        merchantApplyMapper.insert(apply);

        log.info("商家入驻申请已提交 userId={}, applyId={}", userId, apply.getId());
    }

    @Override
    public MerchantApplyVO getMyApply() {
        Long userId = UserContext.getUserId();
        MerchantApply apply = merchantApplyMapper.selectOne(
                new LambdaQueryWrapper<MerchantApply>()
                        .eq(MerchantApply::getUserId, userId)
                        .orderByDesc(MerchantApply::getCreateTime)
                        .last("LIMIT 1"));
        if (apply == null) {
            return null;
        }
        MerchantApplyVO vo = new MerchantApplyVO();
        BeanUtil.copyProperties(apply, vo);
        vo.setBusinessScope(convertScopeIdsToNames(apply.getBusinessScope()));
        SysUser user = authFeignClient.getUserById(apply.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
        }
        return vo;
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
    public MerchantVO submitInfoChange(MerchantUpdateDTO dto) {
        Merchant merchant = getCurrentMerchant();
        // 记录旧值，用于后续删除 OSS 文件
        String oldLogo = merchant.getShopLogo();
        String oldLicense = merchant.getBusinessLicense();

        // ===== B类字段（展示类）：机审后直接生效 =====
        boolean hasBChanges = false;
        Merchant directUpdate = new Merchant();
        directUpdate.setId(merchant.getId());

        if (dto.getShopName() != null) {
            // 敏感词检测
            List<String> sw = sensitiveWordUtil.checkSensitive(dto.getShopName());
            if (!sw.isEmpty()) {
                throw new BizException("店铺名称包含敏感词：" + String.join(",", sw));
            }
            directUpdate.setShopName(dto.getShopName());
            hasBChanges = true;
        }
        if (dto.getShopLogo() != null) {
            directUpdate.setShopLogo(dto.getShopLogo());
            hasBChanges = true;
        }
        if (dto.getShopDesc() != null) {
            directUpdate.setShopDesc(dto.getShopDesc());
            hasBChanges = true;
        }
        if (dto.getShopNotice() != null) {
            directUpdate.setShopNotice(dto.getShopNotice());
            hasBChanges = true;
        }
        if (dto.getBusinessHours() != null) {
            directUpdate.setBusinessHours(dto.getBusinessHours());
            hasBChanges = true;
        }
        if (dto.getAfterSaleInfo() != null) {
            directUpdate.setAfterSaleInfo(dto.getAfterSaleInfo());
            hasBChanges = true;
        }

        if (hasBChanges) {
            this.updateById(directUpdate);
            log.info("商家B类信息已直接更新 merchantId={}", merchant.getId());
        }

        // ===== A类字段（资质类）：需人工审核 =====
        boolean hasAChanges = false;
        MerchantInfoChange change = new MerchantInfoChange();
        change.setMerchantId(merchant.getId());

        if (dto.getBusinessLicense() != null) {
            change.setBusinessLicense(dto.getBusinessLicense());
            hasAChanges = true;
        }
        if (dto.getFoodLicense() != null) {
            change.setFoodLicense(dto.getFoodLicense());
            hasAChanges = true;
        }
        if (dto.getBusinessScope() != null) {
            change.setBusinessScope(dto.getBusinessScope());
            hasAChanges = true;
        }
        if (dto.getContactName() != null) {
            change.setContactName(dto.getContactName());
            hasAChanges = true;
        }
        if (dto.getContactPhone() != null) {
            change.setContactPhone(dto.getContactPhone());
            hasAChanges = true;
        }
        if (dto.getLegalPerson() != null) {
            change.setLegalPerson(dto.getLegalPerson());
            hasAChanges = true;
        }
        if (dto.getBusinessAddress() != null) {
            change.setBusinessAddress(dto.getBusinessAddress());
            hasAChanges = true;
        }
        if (dto.getVerifiedContact() != null) {
            change.setVerifiedContact(dto.getVerifiedContact());
            hasAChanges = true;
        }

        if (hasAChanges) {
            // 标记变更字段
            List<String> changed = new ArrayList<>();
            if (dto.getBusinessLicense() != null) changed.add("businessLicense");
            if (dto.getFoodLicense() != null) changed.add("foodLicense");
            if (dto.getBusinessScope() != null) changed.add("businessScope");
            if (dto.getContactName() != null) changed.add("contactName");
            if (dto.getContactPhone() != null) changed.add("contactPhone");
            if (dto.getLegalPerson() != null) changed.add("legalPerson");
            if (dto.getBusinessAddress() != null) changed.add("businessAddress");
            if (dto.getVerifiedContact() != null) changed.add("verifiedContact");
            change.setChangedFields(JSONUtil.toJsonStr(changed));
            change.setAuditStatus(0); // 待审核
            merchantInfoChangeMapper.insert(change);
            log.info("商家A类信息变更已提交审核 merchantId={}, changeId={}", merchant.getId(), change.getId());
        }

        // 删除旧 OSS 文件
        if (oldLogo != null && dto.getShopLogo() != null && !oldLogo.equals(dto.getShopLogo())) {
            ossFeignClient.deleteByUrl(oldLogo);
        }
        if (oldLicense != null && dto.getBusinessLicense() != null && !oldLicense.equals(dto.getBusinessLicense())) {
            ossFeignClient.deleteByUrl(oldLicense);
        }

        Merchant updated = this.getById(merchant.getId());
        // 增量同步到 ES（商家信息变更后更新搜索索引）
        // ES 同步由商品服务自行处理（跨服务待后续 Feign 扩展）
        return toSimpleVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuVO addGoods(SpuSaveDTO dto) {
        Merchant merchant = getCurrentMerchant();
        Spu spu = new Spu();
        BeanUtil.copyProperties(dto, spu);
        spu.setId(null);
        spu.setMerchantId(merchant.getId());
        spu.setStatus(0); // 新增商品默认下架，审核通过后自动上架
        spu.setAuditStatus(0); // 待审核
        if (dto.getImageList() != null) {
            spu.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }

        // 校验分类：存在且已启用，并匹配商家经营范围
        validateCategory(spu.getCategoryId(), merchant.getBusinessScope());

        // 机审：检测敏感词/侵权/高风险类目，命中则标记
        String categoryName = "";
        try {
            Category cat = productFeignClient.getCategoryById(spu.getCategoryId());
            if (cat != null) categoryName = cat.getName();
        } catch (Exception ignored) {}
        if (sensitiveWordUtil.needsManualReview(spu.getName(), spu.getDescription(), spu.getBrand(), categoryName)) {
            log.info("商品机审命中标记，需人工审核 merchantId={}, name={}", merchant.getId(), spu.getName());
        }

        if (dto.getSkuList() != null && !dto.getSkuList().isEmpty()) {
            dto.getSkuList().forEach(s -> s.setSpuId(null));
        }
        SpuVO vo = productFeignClient.saveSpu(dto);

        cacheHelper.evictSpuHotPage();
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpuVO updateGoods(Long id, SpuSaveDTO dto) {
        Merchant merchant = getCurrentMerchant();
        Spu existSpu = productFeignClient.getSpuById(id);
        if (existSpu == null || !merchant.getId().equals(existSpu.getMerchantId())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在或不属于您的店铺");
        }

        // 记录旧图片URL，用于后续清理OSS残图
        List<String> oldImages = parseImageList(existSpu.getImageList());
        String oldMainImage = existSpu.getMainImage();

        Spu spu = new Spu();
        BeanUtil.copyProperties(dto, spu);
        spu.setId(id);
        spu.setMerchantId(merchant.getId());
        spu.setStatus(0); // 编辑后重新下架待审核
        spu.setAuditStatus(0); // 重新进入审核
        if (dto.getImageList() != null) {
            spu.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }
        validateCategory(spu.getCategoryId(), merchant.getBusinessScope());
        productFeignClient.updateSpu(dto);

        if (dto.getSkuList() != null) {
            List<Sku> skuList = dto.getSkuList().stream()
                    .map(this::toSkuEntity)
                    .collect(Collectors.toList());
            skuList.forEach(s -> s.setSpuId(spu.getId()));
            productFeignClient.batchSaveSku(spu.getId(), skuList);
        }

        // 清理OSS旧图片（新列表中不含的旧图）
        List<String> newImages = dto.getImageList();
        if (oldImages != null && newImages != null) {
            oldImages.stream()
                    .filter(img -> !newImages.contains(img))
                    .forEach(ossFeignClient::deleteByUrl);
        }
        // 主图变更也清理
        if (oldMainImage != null && dto.getMainImage() != null && !oldMainImage.equals(dto.getMainImage())) {
            ossFeignClient.deleteByUrl(oldMainImage);
        }

        cacheHelper.evictSpuDetail(id);
        cacheHelper.evictSpuHotPage();
        // 增量同步到 ES
        // ES 同步由商品服务自行处理
        return productFeignClient.getSpuDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateGoodsStatus(Long id, Integer status) {
        Merchant merchant = getCurrentMerchant();
        Spu existSpu = productFeignClient.getSpuById(id);
        if (existSpu == null || !merchant.getId().equals(existSpu.getMerchantId())) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在或不属于您的店铺");
        }
        productFeignClient.updateSpuStatus(id, status);
    }

    @Override
    public Page<Spu> pageMyGoods(int pageNum, int pageSize, String keyword, Long categoryId, Integer status) {
        Merchant merchant = getCurrentMerchant();
        Page<Spu> page = productFeignClient.pageSpuByMerchant(merchant.getId(), pageNum, pageSize, keyword, categoryId, status);

        // 批量填充SKU相关字段（价格、库存、多规格）
        if (!page.getRecords().isEmpty()) {
            List<Long> spuIds = page.getRecords().stream().map(Spu::getId).collect(Collectors.toList());
            // 一次查出所有SKU
            List<Sku> allSkus = productFeignClient.listSkuBySpuIds(spuIds);
            Map<Long, List<Sku>> skuMap = allSkus.stream()
                    .collect(Collectors.groupingBy(Sku::getSpuId));

            for (Spu spu : page.getRecords()) {
                List<Sku> skus = skuMap.get(spu.getId());
                if (skus == null || skus.isEmpty()) continue;

                // 仅统计启用中的SKU
                List<Sku> activeSkus = skus.stream()
                        .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                        .collect(Collectors.toList());

                long totalStock = 0;
                long lowStockCount = 0;
                List<com.xs.sheepaimall.vo.SkuStockVO> stockList = new ArrayList<>();
                for (Sku s : activeSkus) {
                    int stock = s.getStock() != null ? s.getStock() : 0;
                    totalStock += stock;
                    if (stock < 20) lowStockCount++;

                    com.xs.sheepaimall.vo.SkuStockVO vo = new com.xs.sheepaimall.vo.SkuStockVO();
                    vo.setSkuId(s.getId());
                    vo.setSkuName(s.getSkuName());
                    vo.setPrice(s.getPrice());
                    vo.setStock(s.getStock());
                    vo.setImage(s.getImage());
                    stockList.add(vo);
                }

                BigDecimal minPrice = activeSkus.stream()
                        .map(Sku::getPrice)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null);

                spu.setTotalStock((int) totalStock);
                spu.setStockStatus(totalStock == 0 ? 0 : totalStock <= 50 ? 2 : 1);
                spu.setMultiSpec(skus.size() > 1);
                spu.setPartOutOfStock(activeSkus.size() > 1 && lowStockCount > 0 && lowStockCount < activeSkus.size());
                spu.setSkuStockList(stockList);
                spu.setMinPrice(minPrice);
            }
        }

        return page;
    }

    @Override
    public Page<MerchantOrderVO> pageMyOrders(int pageNum, int pageSize, Integer status) {
        Merchant merchant = getCurrentMerchant();
        List<Long> spuIds = productFeignClient.listSpuIdsByMerchant(merchant.getId());
        if (spuIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 查该店铺 SPU 涉及的 order_item
        List<OrderItem> allItems = orderFeignClient.listOrderItemsBySpuIds(spuIds);
        if (allItems.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        // 按 orderId 分组，只保留该店铺的商品明细
        Map<Long, List<OrderItem>> itemMap = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Set<Long> orderIds = itemMap.keySet();

        // 分页查订单
        Page<OrderInfo> orderPage = orderFeignClient.pageOrdersByIds(orderIds, pageNum, pageSize, status);

        // 批量查 SPU 名称
        Set<Long> itemSpuIds = allItems.stream().map(OrderItem::getSpuId).collect(Collectors.toSet());
        List<Spu> spuList = productFeignClient.listSpuByIds(new ArrayList<>(itemSpuIds));
        Map<Long, String> spuNameMap = spuList.stream()
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
        OrderInfo order = orderFeignClient.getOrderById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }

        // 只返回该店铺的商品项
        List<OrderItem> allItems = orderFeignClient.getOrderItemsByOrderId(orderId);
        List<Long> mySpuIds = productFeignClient.listSpuIdsByMerchant(merchant.getId());

        List<OrderItem> shopItems = allItems.stream()
                .filter(item -> mySpuIds.contains(item.getSpuId()))
                .collect(Collectors.toList());

        // SPU 名
        Set<Long> spuIds = shopItems.stream().map(OrderItem::getSpuId).collect(Collectors.toSet());
        List<Spu> spuList = productFeignClient.listSpuByIds(new ArrayList<>(spuIds));
        Map<Long, String> spuNameMap = spuList.stream()
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
        List<Long> mySpuIds = productFeignClient.listSpuIdsByMerchant(merchant.getId());
        if (mySpuIds.isEmpty()) {
            throw new BizException("订单中无本店铺商品");
        }

        List<OrderItem> items = orderFeignClient.getOrderItemsByOrderId(orderId).stream()
                .filter(item -> mySpuIds.contains(item.getSpuId()))
                .collect(Collectors.toList());
        if (items.isEmpty()) {
            throw new BizException("该订单不包含本店铺的商品");
        }

        OrderInfo order = orderFeignClient.getOrderById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (order.getStatus() != 1) {
            throw new BizException("仅已支付订单可发货，当前状态：" + getStatusText(order.getStatus()));
        }

        orderFeignClient.deliverOrder(orderId, deliveryCompany, deliveryNo);
        log.info("商家发货 orderId={}, merchantId={}, company={}, no={}",
                orderId, merchant.getId(), deliveryCompany, deliveryNo);
    }

    @Override
    public IncomeStatVO getIncomeStat() {
        Merchant merchant = getCurrentMerchant();

        // 该店铺所有 SPU ID
        List<Long> spuIds = productFeignClient.listSpuIdsByMerchant(merchant.getId());
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
        List<OrderItem> paidItems = orderFeignClient.listOrderItemsBySpuIds(spuIds);
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
        Page<OrderInfo> orderPage = orderFeignClient.pageOrdersByIds(orderIds, 1, 10000, null);
        List<OrderInfo> paidOrders = orderPage.getRecords().stream()
                .filter(o -> o.getStatus() >= 1 && o.getStatus() <= 3)
                .collect(Collectors.toList());

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
        // 可提现余额
        try {
            stat.setAvailableBalance(fundService.getCurrentBalance(merchant.getId()));
        } catch (Exception e) {
            stat.setAvailableBalance(BigDecimal.ZERO);
        }
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
    public Page<MerchantApplyVO> pageAllApply(int pageNum, int pageSize, Integer status, String keyword) {
        Page<MerchantApply> page = merchantApplyMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MerchantApply>()
                        .eq(status != null, MerchantApply::getStatus, status)
                        .and(StrUtil.isNotBlank(keyword), w -> w
                                .like(MerchantApply::getShopName, keyword)
                                .or()
                                .like(MerchantApply::getContactName, keyword)
                                .or()
                                .like(MerchantApply::getContactPhone, keyword))
                        .orderByDesc(MerchantApply::getCreateTime));

        List<MerchantApplyVO> voList = page.getRecords().stream().map(apply -> {
            MerchantApplyVO vo = new MerchantApplyVO();
            BeanUtil.copyProperties(apply, vo);
            vo.setBusinessScope(convertScopeIdsToNames(apply.getBusinessScope()));
            // 查询申请人用户名
            if (apply.getUserId() != null) {
                SysUser user = authFeignClient.getUserById(apply.getUserId());
                if (user != null) {
                    vo.setUsername(user.getUsername());
                }
            }
            return vo;
        }).collect(Collectors.toList());

        Page<MerchantApplyVO> result = new Page<>(pageNum, pageSize);
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
                merchant.setFoodLicense(apply.getFoodLicense());
                merchant.setLegalPerson(apply.getLegalPerson());
                merchant.setBusinessAddress(apply.getBusinessAddress());
                merchant.setStatus(1);
                merchant.setAuditTime(LocalDateTime.now());
                this.save(merchant);
            } else {
                merchant.setShopName(apply.getShopName());
                merchant.setBusinessLicense(apply.getBusinessLicense());
                merchant.setBusinessScope(apply.getBusinessScope());
                merchant.setContactName(apply.getContactName());
                merchant.setContactPhone(apply.getContactPhone());
                merchant.setFoodLicense(apply.getFoodLicense());
                merchant.setLegalPerson(apply.getLegalPerson());
                merchant.setBusinessAddress(apply.getBusinessAddress());
                merchant.setStatus(1);
                merchant.setAuditRemark(null);
                merchant.setAuditTime(LocalDateTime.now());
                this.updateById(merchant);
            }

            // 分配商家角色
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(apply.getUserId());
            userRole.setRoleId(MERCHANT_ROLE_ID);
            authFeignClient.insertUserRole(userRole);

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

    @Override
    public Long getCurrentMerchantId() {
        return getCurrentMerchant().getId();
    }

    @Override
    public Integer getMyShopStatus() {
        return getCurrentMerchant().getShopStatus();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer toggleShopStatus() {
        Merchant merchant = getCurrentMerchant();
        int newStatus = (merchant.getShopStatus() == null || merchant.getShopStatus() == 0) ? 1 : 0;
        merchant.setShopStatus(newStatus);
        this.updateById(merchant);
        // 增量同步到 ES（营业状态变更）
        // ES 同步由商品服务自行处理（跨服务待后续 Feign 扩展）
        log.info("商家营业状态切换 merchantId={}, newStatus={}", merchant.getId(), newStatus);
        return newStatus;
    }

    // ========== 商家信息变更审核 ==========

    @Override
    public Page<MerchantInfoChangeVO> pagePendingInfoChange(int pageNum, int pageSize) {
        Page<MerchantInfoChange> page = merchantInfoChangeMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<MerchantInfoChange>()
                        .eq(MerchantInfoChange::getAuditStatus, 0)
                        .orderByDesc(MerchantInfoChange::getCreateTime));

        Page<MerchantInfoChangeVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toInfoChangeVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditInfoChange(Long changeId, Integer auditStatus, String auditMsg) {
        MerchantInfoChange change = merchantInfoChangeMapper.selectById(changeId);
        if (change == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "变更记录不存在");
        }
        if (change.getAuditStatus() != 0) {
            throw new BizException("该变更已被审核，请勿重复操作");
        }

        if (auditStatus == 1) {
            Merchant merchant = this.getById(change.getMerchantId());
            if (merchant == null) {
                throw new BizException("商家不存在");
            }
            Merchant update = new Merchant();
            update.setId(merchant.getId());
            boolean hasChange = false;
            if (change.getBusinessLicense() != null)    { update.setBusinessLicense(change.getBusinessLicense()); hasChange = true; }
            if (change.getFoodLicense() != null)         { update.setFoodLicense(change.getFoodLicense()); hasChange = true; }
            if (change.getBusinessScope() != null)       { update.setBusinessScope(change.getBusinessScope()); hasChange = true; }
            if (change.getContactName() != null)         { update.setContactName(change.getContactName()); hasChange = true; }
            if (change.getContactPhone() != null)        { update.setContactPhone(change.getContactPhone()); hasChange = true; }
            if (change.getLegalPerson() != null)         { update.setLegalPerson(change.getLegalPerson()); hasChange = true; }
            if (change.getBusinessAddress() != null)     { update.setBusinessAddress(change.getBusinessAddress()); hasChange = true; }
            if (change.getVerifiedContact() != null)     { update.setVerifiedContact(change.getVerifiedContact()); hasChange = true; }
            if (hasChange) {
                this.updateById(update);
            }
            change.setAuditStatus(1);
            log.info("商家信息变更审核通过 merchantId={}, changeId={}", change.getMerchantId(), changeId);
        } else if (auditStatus == 2) {
            if (StrUtil.isBlank(auditMsg)) {
                throw new BizException("审核驳回时必须填写驳回原因");
            }
            change.setAuditStatus(2);
            change.setAuditMsg(auditMsg);
            log.info("商家信息变更审核驳回 merchantId={}, changeId={}, reason={}", change.getMerchantId(), changeId, auditMsg);
        } else {
            throw new BizException("审核状态值错误");
        }

        change.setAuditUserId(UserContext.getUserId());
        change.setAuditTime(LocalDateTime.now());
        merchantInfoChangeMapper.updateById(change);
    }

    /** MerchantInfoChange → VO */
    private MerchantInfoChangeVO toInfoChangeVO(MerchantInfoChange change) {
        MerchantInfoChangeVO vo = new MerchantInfoChangeVO();
        BeanUtil.copyProperties(change, vo);
        vo.setBusinessScope(convertScopeIdsToNames(change.getBusinessScope()));
        Merchant merchant = this.getById(change.getMerchantId());
        if (merchant != null) {
            vo.setShopName(merchant.getShopName());
        }
        Map<Integer, String> statusMap = Map.of(0, "待审核", 1, "已通过", 2, "已驳回");
        vo.setAuditStatusText(statusMap.getOrDefault(change.getAuditStatus(), "未知"));
        return vo;
    }

    // ==================== 内部方法 ====================

    /** 将逗号分隔的分类ID串转换为分类名称串 */
    private String convertScopeIdsToNames(String businessScope) {
        if (StrUtil.isBlank(businessScope)) return businessScope;
        List<Long> ids = Arrays.stream(businessScope.split(","))
                .map(String::trim).filter(StrUtil::isNotBlank)
                .map(s -> { try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }})
                .filter(Objects::nonNull).collect(Collectors.toList());
        if (ids.isEmpty()) return businessScope;
        List<Category> categories = productFeignClient.listCategoryByIds(ids);
        return categories.stream().map(Category::getName).filter(Objects::nonNull).collect(Collectors.joining(","));
    }

    private MerchantVO toSimpleVO(Merchant merchant) {
        MerchantVO vo = new MerchantVO();
        BeanUtil.copyProperties(merchant, vo);
        vo.setBusinessScope(convertScopeIdsToNames(merchant.getBusinessScope()));
        // 注入 DSR 评分
        MerchantDsrVO dsr = merchantDsrService.getLatestDsr(merchant.getId());
        if (dsr != null) {
            vo.setDescribeScore(dsr.getDescribeScore() != null ? dsr.getDescribeScore().doubleValue() : null);
            vo.setServiceScore(dsr.getServiceScore() != null ? dsr.getServiceScore().doubleValue() : null);
            vo.setLogisticsScore(dsr.getLogisticsScore() != null ? dsr.getLogisticsScore().doubleValue() : null);
            vo.setDsrCount(dsr.getTotalCount());
        }
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
        // sku_code 为空时自动生成（数据库 NOT NULL + UNIQUE）
        if (StrUtil.isBlank(sku.getSkuCode())) {
            sku.setSkuCode("SKU" + System.currentTimeMillis() + (int) (Math.random() * 1000));
        }
        return sku;
    }

    /** 解析 imageList JSON → List<String> */
    private List<String> parseImageList(String imageListJson) {
        if (StrUtil.isBlank(imageListJson)) return null;
        try {
            return JSONUtil.toList(imageListJson, String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 校验分类：存在、已启用、且在商家经营范围内 */
    private void validateCategory(Long categoryId, String businessScope) {
        Category cat = productFeignClient.getCategoryById(categoryId);
        if (cat == null) throw new BizException("所选分类不存在");
        if (cat.getStatus() == null || cat.getStatus() == 0) throw new BizException("所选分类未启用");

        if (StrUtil.isNotBlank(businessScope)) {
            List<Long> allowedIds = Arrays.stream(businessScope.split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .map(s -> {
                        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (!allowedIds.isEmpty() && !allowedIds.contains(categoryId)) {
                throw new BizException("该分类不在您的经营范围内");
            }
        }
    }

    @Override
    public List<CategoryVO> getMerchantCategories(Long merchantId) {
        Merchant merchant = this.getById(merchantId);
        if (merchant == null) return Collections.emptyList();

        String scope = merchant.getBusinessScope();
        if (StrUtil.isBlank(scope)) return Collections.emptyList();

        List<Long> ids = Arrays.stream(scope.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .map(s -> {
                    try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (ids.isEmpty()) return Collections.emptyList();

        List<Category> categories = productFeignClient.listCategoryByIds(ids);
        return categories.stream().map(cat -> {
            CategoryVO vo = new CategoryVO();
            BeanUtil.copyProperties(cat, vo);
            return vo;
        }).collect(Collectors.toList());
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
