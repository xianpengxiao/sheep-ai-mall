package com.xs.sheepaimall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.MerchantApplyDTO;
import com.xs.sheepaimall.dto.MerchantUpdateDTO;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.security.RequirePermission;
import com.xs.sheepaimall.service.MerchantDsrService;
import com.xs.sheepaimall.service.MerchantService;
import com.xs.sheepaimall.service.ReviewService;
import com.xs.sheepaimall.vo.IncomeStatVO;
import com.xs.sheepaimall.vo.MerchantDsrVO;
import com.xs.sheepaimall.vo.MerchantOrderVO;
import com.xs.sheepaimall.vo.MerchantVO;
import com.xs.sheepaimall.vo.ReviewVO;
import com.xs.sheepaimall.vo.SpuVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "商家", description = "买家端 + 商家后台管理")
@Validated
@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    @Resource
    private MerchantService merchantService;

    @Resource
    private ReviewService reviewService;

    @Resource
    private MerchantDsrService merchantDsrService;

    // ==================== 买家端 ====================

    @Operation(summary = "商家列表分页查询")
    @GetMapping("/page")
    public R<Page<MerchantVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "店铺名称") @RequestParam(required = false) String shopName,
            @Parameter(description = "经营范围") @RequestParam(required = false) String businessScope) {
        return R.ok(merchantService.pageMerchant(pageNum, pageSize, shopName, businessScope));
    }

    @Operation(summary = "商家详情", description = "返回商家信息 + 该店铺在售商品列表")
    @GetMapping("/{id}")
    public R<MerchantVO> detail(@Parameter(description = "商家ID") @PathVariable Long id) {
        return R.ok(merchantService.getMerchantDetail(id));
    }

    @Operation(summary = "店铺商品分页", description = "分页查询指定店铺的在售商品")
    @GetMapping("/{merId}/goods")
    public R<Page<Spu>> goods(
            @Parameter(description = "商家ID") @PathVariable Long merId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize) {
        return R.ok(merchantService.pageMerchantGoods(merId, pageNum, pageSize));
    }

    @Operation(summary = "商家入驻申请")
    @PostMapping("/apply")
    public R<Void> apply(@Valid @RequestBody MerchantApplyDTO dto) {
        merchantService.apply(dto);
        return R.ok();
    }

    // ==================== 商家后台 ====================

    @Operation(summary = "店铺信息查询")
    @GetMapping("/info")
    @RequirePermission("merchant:info:update")
    public R<MerchantVO> myShopInfo() {
        return R.ok(merchantService.getMyShopInfo());
    }

    @Operation(summary = "修改店铺信息")
    @PutMapping("/info")
    @RequirePermission("merchant:info:update")
    public R<MerchantVO> updateShop(@Valid @RequestBody MerchantUpdateDTO dto) {
        return R.ok(merchantService.updateMyShop(dto));
    }

    @Operation(summary = "新增商品")
    @PostMapping("/goods")
    @RequirePermission("merchant:goods:manage")
    public R<SpuVO> addGoods(@Valid @RequestBody SpuSaveDTO dto) {
        return R.ok(merchantService.addGoods(dto));
    }

    @Operation(summary = "修改商品")
    @PutMapping("/goods/{id}")
    @RequirePermission("merchant:goods:manage")
    public R<SpuVO> updateGoods(
            @Parameter(description = "商品ID") @PathVariable Long id,
            @Valid @RequestBody SpuSaveDTO dto) {
        return R.ok(merchantService.updateGoods(id, dto));
    }

    @Operation(summary = "商品上下架")
    @PutMapping("/goods/{id}/status")
    @RequirePermission("merchant:goods:manage")
    public R<Void> updateGoodsStatus(
            @Parameter(description = "商品ID") @PathVariable Long id,
            @Parameter(description = "状态 1上架 0下架") @RequestParam Integer status) {
        merchantService.updateGoodsStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "商家商品列表分页")
    @GetMapping("/goods/page")
    @RequirePermission("merchant:goods:manage")
    public R<Page<Spu>> goodsPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类ID") @RequestParam(required = false) Long categoryId) {
        return R.ok(merchantService.pageMyGoods(pageNum, pageSize, keyword, categoryId));
    }

    @Operation(summary = "店铺订单分页")
    @GetMapping("/order/page")
    @RequirePermission("merchant:order:manage")
    public R<Page<MerchantOrderVO>> orderPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "订单状态筛选") @RequestParam(required = false) Integer status) {
        return R.ok(merchantService.pageMyOrders(pageNum, pageSize, status));
    }

    @Operation(summary = "店铺订单详情")
    @GetMapping("/order/{orderId}")
    @RequirePermission("merchant:order:manage")
    public R<MerchantOrderVO> orderDetail(
            @Parameter(description = "订单ID") @PathVariable Long orderId) {
        return R.ok(merchantService.getMyOrderDetail(orderId));
    }

    @Operation(summary = "订单发货")
    @PutMapping("/order/{orderId}/deliver")
    @RequirePermission("merchant:order:manage")
    public R<Void> deliver(
            @Parameter(description = "订单ID") @PathVariable Long orderId,
            @Parameter(description = "快递公司") @RequestParam String deliveryCompany,
            @Parameter(description = "快递单号") @RequestParam String deliveryNo) {
        merchantService.deliverOrder(orderId, deliveryCompany, deliveryNo);
        return R.ok();
    }

    @Operation(summary = "店铺营收统计", description = "日/月销售额、订单数量")
    @GetMapping("/stat/income")
    @RequirePermission("merchant:stat:view")
    public R<IncomeStatVO> incomeStat() {
        return R.ok(merchantService.getIncomeStat());
    }

    @Operation(summary = "店铺评价列表", description = "分页查询本店铺商品的评价，支持按状态筛选和内容搜索")
    @GetMapping("/review/page")
    @RequirePermission("merchant:review:view")
    public R<Page<ReviewVO>> reviewPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "显示状态 0隐藏 1显示（不传查全部）") @RequestParam(required = false) Integer status,
            @Parameter(description = "评价状态 0待评 1已评 2已过期（不传查全部）") @RequestParam(required = false) Integer reviewStatus,
            @Parameter(description = "评价内容关键词") @RequestParam(required = false) String keyword) {
        return R.ok(reviewService.pageByMerchant(pageNum, pageSize, status, reviewStatus, keyword));
    }
    // ==================== 营业状态 ====================

    @Operation(summary = "查询营业状态", description = "查询当前店铺的营业状态（商家后台）")
    @GetMapping("/shop/status")
    @RequirePermission("merchant:info:update")
    public R<Integer> shopStatus() {
        return R.ok(merchantService.getMyShopStatus());
    }

    @Operation(summary = "切换营业状态", description = "打烊/开店切换（商家后台）")
    @PutMapping("/shop/status")
    @RequirePermission("merchant:info:update")
    public R<Integer> toggleShopStatus() {
        return R.ok(merchantService.toggleShopStatus());
    }

    // ==================== DSR 评分 ====================

    @Operation(summary = "店铺DSR评分", description = "查询指定店铺的最新DSR三维评分（公开接口）")
    @GetMapping("/{id}/dsr")
    public R<MerchantDsrVO> dsr(@Parameter(description = "商家ID") @PathVariable Long id) {
        return R.ok(merchantDsrService.getLatestDsr(id));
    }

    @Operation(summary = "DSR评分趋势", description = "查询本店铺近30天DSR趋势（商家后台）")
    @GetMapping("/stat/dsr")
    @RequirePermission("merchant:stat:view")
    public R<MerchantDsrVO> dsrTrend() {
        Long merchantId = merchantService.getCurrentMerchantId();
        return R.ok(merchantDsrService.getTrendDsr(merchantId));
    }
}
