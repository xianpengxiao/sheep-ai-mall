package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.ReviewDTO;
import com.xs.sheepaimall.entity.*;
import com.xs.sheepaimall.mapper.ProductReviewMapper;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.*;
import com.xs.sheepaimall.vo.ReviewVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ReviewServiceImpl extends ServiceImpl<ProductReviewMapper, ProductReview> implements ReviewService {

    @Resource
    private OrderService orderService;

    @Resource
    private OrderItemService orderItemService;

    @Resource
    private SpuService spuService;

    @Resource
    private SkuService skuService;

    @Resource
    private SysUserService sysUserService;

    @Resource
    private MerchantService merchantService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewVO create(ReviewDTO dto) {
        Long userId = UserContext.getUserId();

        OrderItem orderItem = orderItemService.getById(dto.getOrderItemId());
        if (orderItem == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单明细不存在");
        }
        if (!orderItem.getOrderId().equals(dto.getOrderId())) {
            throw new BizException("订单明细不属于该订单");
        }

        OrderInfo order = orderService.getById(dto.getOrderId());
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException("无权评价他人的订单");
        }
        if (order.getStatus() == null || order.getStatus() == 0 || order.getStatus() == 4) {
            throw new BizException("仅已支付的订单可评价");
        }

        Long exist = this.lambdaQuery()
                .eq(ProductReview::getOrderItemId, dto.getOrderItemId())
                .count();
        if (exist > 0) {
            throw new BizException("该商品已评价，请勿重复提交");
        }

        ProductReview review = new ProductReview();
        BeanUtil.copyProperties(dto, review);
        review.setSpuId(orderItem.getSpuId());
        review.setSkuId(orderItem.getSkuId());
        review.setUserId(userId);
        review.setStatus(1);
        if (dto.getImageList() != null) {
            review.setImageList(JSONUtil.toJsonStr(dto.getImageList()));
        }
        this.save(review);

        log.info("商品评价已提交 reviewId={}, userId={}, spuId={}", review.getId(), userId, orderItem.getSpuId());
        return toReviewVO(review);
    }

    @Override
    public Page<ReviewVO> pageBySpu(Long spuId, int pageNum, int pageSize) {
        Spu spu = spuService.getById(spuId);
        if (spu == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "商品不存在");
        }

        Page<ProductReview> page = this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ProductReview>()
                        .eq(ProductReview::getSpuId, spuId)
                        .eq(ProductReview::getStatus, 1)
                        .orderByDesc(ProductReview::getCreateTime));

        return toReviewVOPage(page);
    }

    @Override
    public Page<ReviewVO> pageByMerchant(int pageNum, int pageSize) {
        Long userId = UserContext.getUserId();
        Merchant merchant = merchantService.lambdaQuery()
                .eq(Merchant::getUserId, userId)
                .one();
        if (merchant == null) {
            throw new BizException("您还不是商家");
        }

        // 查该店铺所有 SPU ID
        List<Long> spuIds = spuService.listObjs(
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchant.getId())
                        .select(Spu::getId),
                o -> (Long) o);
        if (spuIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        Page<ProductReview> page = this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ProductReview>()
                        .in(ProductReview::getSpuId, spuIds)
                        .orderByDesc(ProductReview::getCreateTime));

        return toReviewVOPage(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long id, Integer status) {
        ProductReview review = this.getById(id);
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评价不存在");
        }
        review.setStatus(status);
        this.updateById(review);
        log.info("评价状态已变更 reviewId={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeReview(Long id) {
        ProductReview review = this.getById(id);
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评价不存在");
        }
        this.removeById(id);
        log.info("评价已删除 reviewId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyReview(Long id) {
        ProductReview review = this.getById(id);
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评价不存在");
        }
        Long userId = UserContext.getUserId();
        if (!review.getUserId().equals(userId)) {
            throw new BizException("只能删除自己的评价");
        }
        this.removeById(id);
        log.info("用户删除自己的评价 reviewId={}, userId={}", id, userId);
    }

    // =========== helpers ===========

    private ReviewVO toReviewVO(ProductReview review) {
        ReviewVO vo = new ReviewVO();
        BeanUtil.copyProperties(review, vo);
        if (StrUtil.isNotBlank(review.getImageList())) {
            vo.setImageList(JSONUtil.toList(review.getImageList(), String.class));
        }
        Sku sku = skuService.getById(review.getSkuId());
        if (sku != null) {
            vo.setSkuName(sku.getSkuName());
        }
        SysUser user = sysUserService.getById(review.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar() != null ? user.getAvatar() : "");
        }
        return vo;
    }

    private Page<ReviewVO> toReviewVOPage(Page<ProductReview> page) {
        List<ProductReview> records = page.getRecords();
        if (records.isEmpty()) {
            return new Page<ReviewVO>(page.getCurrent(), page.getSize()).setTotal(page.getTotal());
        }

        Set<Long> skuIds = records.stream().map(ProductReview::getSkuId).collect(Collectors.toSet());
        Map<Long, Sku> skuMap = skuService.listByIds(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));

        Set<Long> userIds = records.stream().map(ProductReview::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        List<ReviewVO> voList = records.stream().map(r -> {
            ReviewVO vo = new ReviewVO();
            BeanUtil.copyProperties(r, vo);
            if (StrUtil.isNotBlank(r.getImageList())) {
                vo.setImageList(JSONUtil.toList(r.getImageList(), String.class));
            }
            Sku sku = skuMap.get(r.getSkuId());
            if (sku != null) vo.setSkuName(sku.getSkuName());
            SysUser user = userMap.get(r.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setAvatar(user.getAvatar() != null ? user.getAvatar() : "");
            }
            return vo;
        }).collect(Collectors.toList());

        Page<ReviewVO> result = new Page<>(page.getCurrent(), page.getSize());
        result.setTotal(page.getTotal());
        result.setRecords(voList);
        return result;
    }
}
