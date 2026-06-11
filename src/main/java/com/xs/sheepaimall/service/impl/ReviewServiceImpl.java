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

    @Resource
    private ProductReviewMapper productReviewMapper;

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
        if (order.getStatus() == null || order.getStatus() != 3) {
            throw new BizException("仅已完成的订单可评价");
        }

        // 查该订单明细的评价记录（原生SQL，不受 @TableLogic 影响）
        ProductReview review = productReviewMapper.selectByOrderItemId(dto.getOrderItemId());
        if (review == null) {
            throw new BizException("该商品不可评价，请先确认收货");
        }

        // 三维评分 → 综合评分四舍五入
        int ds = dto.getDescribeScore();
        int ss = dto.getServiceScore();
        int ls = dto.getLogisticsScore();
        int rating = Math.round((ds + ss + ls) / 3.0f);
        String content = dto.getContent() != null ? dto.getContent() : "";
        String imageList = dto.getImageList() != null ? JSONUtil.toJsonStr(dto.getImageList()) : "[]";

        if (review.getReviewStatus() == 1 && review.getDeleted() != null && review.getDeleted() == 1) {
            // 已评且已删除 → 允许重新评论，原生SQL不受 @TableLogic 影响
            productReviewMapper.updateForReReview(review.getId(), rating, ds, ss, ls, content, imageList);
            // 重新查询完整记录用于返回
            review = productReviewMapper.selectByOrderItemId(dto.getOrderItemId());
            return toReviewVO(review);
        }

        if (review.getReviewStatus() != 0) {
            throw new BizException("该商品已评价或已过期");
        }

        review.setRating(rating);
        review.setDescribeScore(ds);
        review.setServiceScore(ss);
        review.setLogisticsScore(ls);
        review.setContent(content);
        review.setImageList(imageList);
        review.setStatus(1);
        review.setReviewStatus(1);
        this.updateById(review);

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
    public Page<ReviewVO> pageByMerchant(int pageNum, int pageSize, Integer status, Integer reviewStatus, String keyword) {
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

        int displayStatus = status != null ? status : 1;

        Page<ProductReview> page = this.page(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ProductReview>()
                        .in(ProductReview::getSpuId, spuIds)
                        .eq(ProductReview::getStatus, displayStatus)
                        .eq(reviewStatus != null, ProductReview::getReviewStatus, reviewStatus)
                        .orderByDesc(ProductReview::getCreateTime));

        return toReviewVOPage(page);
    }

    @Override
    public Page<ReviewVO> pageByMerchantPublic(Long merchantId, int pageNum, int pageSize, Integer rating) {
        // 查该店铺所有 SPU ID
        List<Long> spuIds = spuService.listObjs(
                new LambdaQueryWrapper<Spu>()
                        .eq(Spu::getMerchantId, merchantId)
                        .select(Spu::getId),
                o -> (Long) o);
        if (spuIds.isEmpty()) {
            return new Page<>(pageNum, pageSize);
        }

        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .in(ProductReview::getSpuId, spuIds)
                .eq(ProductReview::getStatus, 1)
                .orderByDesc(ProductReview::getCreateTime);

        // rating 分类：1差评(1-2) 2中评(3) 3好评(4-5)
        if (rating != null) {
            if (rating == 1) wrapper.le(ProductReview::getRating, 2);
            else if (rating == 2) wrapper.eq(ProductReview::getRating, 3);
            else if (rating == 3) wrapper.ge(ProductReview::getRating, 4);
        }

        Page<ProductReview> page = this.page(new Page<>(pageNum, pageSize), wrapper);
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
    public Page<ReviewVO> pageAllReview(int pageNum, int pageSize, String keyword,
                                         Integer rating, Integer status,
                                         String startTime, String endTime) {
        LambdaQueryWrapper<ProductReview> wrapper = new LambdaQueryWrapper<ProductReview>()
                .eq(rating != null, ProductReview::getRating, rating)
                .eq(status != null, ProductReview::getStatus, status)
                .ge(StrUtil.isNotBlank(startTime), ProductReview::getCreateTime, startTime)
                .le(StrUtil.isNotBlank(endTime), ProductReview::getCreateTime, endTime)
                .orderByDesc(ProductReview::getCreateTime);

        // keyword 同时匹配评价内容 / 商品名 / 用户名
        if (StrUtil.isNotBlank(keyword)) {
            List<Long> matchedSpuIds = spuService.listObjs(
                    new LambdaQueryWrapper<Spu>()
                            .like(Spu::getName, keyword)
                            .select(Spu::getId),
                    o -> (Long) o);
            List<Long> matchedUserIds = sysUserService.listObjs(
                    new LambdaQueryWrapper<SysUser>()
                            .like(SysUser::getUsername, keyword)
                            .select(SysUser::getId),
                    o -> (Long) o);

            wrapper.and(w -> {
                w.like(ProductReview::getContent, keyword);
                if (!matchedSpuIds.isEmpty()) {
                    w.or().in(ProductReview::getSpuId, matchedSpuIds);
                }
                if (!matchedUserIds.isEmpty()) {
                    w.or().in(ProductReview::getUserId, matchedUserIds);
                }
            });
        }

        Page<ProductReview> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        return toReviewVOPage(page);
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

    @Override
    public ReviewVO getByOrderItemId(Long orderItemId) {
        ProductReview review = this.lambdaQuery()
                .eq(ProductReview::getOrderItemId, orderItemId)
                .one();
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评价不存在");
        }
        Long userId = UserContext.getUserId();
        if (!review.getUserId().equals(userId)) {
            throw new BizException("无权查看他人的评价");
        }
        return toReviewVO(review);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleMyStatus(Long id, Integer status) {
        ProductReview review = this.getById(id);
        if (review == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "评价不存在");
        }
        Long userId = UserContext.getUserId();
        if (!review.getUserId().equals(userId)) {
            throw new BizException("只能操作自己的评价");
        }
        review.setStatus(status);
        this.updateById(review);
        log.info("用户已{}评价 reviewId={}", status == 1 ? "显示" : "隐藏", id);
    }

    // =========== helpers ===========

    private ReviewVO toReviewVO(ProductReview review) {
        ReviewVO vo = new ReviewVO();
        BeanUtil.copyProperties(review, vo);
        if (StrUtil.isNotBlank(review.getImageList())) {
            vo.setImageList(JSONUtil.toList(review.getImageList(), String.class));
        }
        Spu spu = spuService.getById(review.getSpuId());
        if (spu != null) vo.setSpuName(spu.getName());
        Sku sku = skuService.getById(review.getSkuId());
        if (sku != null) vo.setSkuName(sku.getSkuName());
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

        Set<Long> spuIds = records.stream().map(ProductReview::getSpuId).collect(Collectors.toSet());
        Map<Long, String> spuNameMap = spuService.listByIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, Spu::getName, (a, b) -> a));

        Set<Long> userIds = records.stream().map(ProductReview::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        List<ReviewVO> voList = records.stream().map(r -> {
            ReviewVO vo = new ReviewVO();
            BeanUtil.copyProperties(r, vo);
            if (StrUtil.isNotBlank(r.getImageList())) {
                vo.setImageList(JSONUtil.toList(r.getImageList(), String.class));
            }
            vo.setSpuName(spuNameMap.get(r.getSpuId()));
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
