package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.MerchantApplyDTO;
import com.xs.sheepaimall.dto.MerchantAuditDTO;
import com.xs.sheepaimall.dto.MerchantUpdateDTO;
import com.xs.sheepaimall.dto.SpuSaveDTO;
import com.xs.sheepaimall.entity.Merchant;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.vo.*;

/**
 * 商家 Service
 */
public interface MerchantService extends IService<Merchant> {

    // ========== 买家端 ==========

    /** 商家列表分页查询 */
    Page<MerchantVO> pageMerchant(int pageNum, int pageSize, String shopName, String businessScope);

    /** 商家详情（含店铺在售商品列表） */
    MerchantVO getMerchantDetail(Long id);

    /** 店铺商品分页 */
    Page<Spu> pageMerchantGoods(Long merchantId, int pageNum, int pageSize);

    /** 提交入驻申请 */
    void apply(MerchantApplyDTO dto);

    /** 查询当前用户的入驻申请状态 */
    MerchantApplyVO getMyApply();

    // ========== 商家后台 ==========

    /** 查询当前商家的店铺信息 */
    MerchantVO getMyShopInfo();

    /** 提交店铺信息修改（A类字段进入审核，B类字段直接生效） */
    MerchantVO submitInfoChange(MerchantUpdateDTO dto);

    /** 新增商品（商家自己的商品） */
    SpuVO addGoods(SpuSaveDTO dto);

    /** 编辑商品（校验归属） */
    SpuVO updateGoods(Long id, SpuSaveDTO dto);

    /** 商品上下架 */
    void updateGoodsStatus(Long id, Integer status);

    /** 商家商品列表分页 */
    Page<Spu> pageMyGoods(int pageNum, int pageSize, String keyword, Long categoryId, Integer status);

    /** 店铺订单分页 */
    Page<MerchantOrderVO> pageMyOrders(int pageNum, int pageSize, Integer status);

    /** 店铺订单详情 */
    MerchantOrderVO getMyOrderDetail(Long orderId);

    /** 发货 */
    void deliverOrder(Long orderId, String deliveryCompany, String deliveryNo);

    /** 营收统计 */
    IncomeStatVO getIncomeStat();

    // ========== 平台管理 ==========

    /** 全量商家列表 */
    Page<MerchantVO> pageAllMerchant(int pageNum, int pageSize, Integer status, String keyword);

    /** 入驻申请列表 */
    Page<MerchantApplyVO> pageAllApply(int pageNum, int pageSize, Integer status, String keyword);

    /** 审核入驻申请 */
    void auditApply(Long applyId, MerchantAuditDTO dto);

    /** 禁用/启用商家 */
    void toggleMerchantStatus(Long id, Integer status);

    /** 获取当前营业状态（商家后台） */
    Integer getMyShopStatus();

    /** 切换营业状态（商家打烊/开店） */
    Integer toggleShopStatus();

    /** 获取当前登录用户对应的已开通商家ID（商家后台专用） */
    Long getCurrentMerchantId();

    // ========== 商家信息变更审核 ==========

    /** 管理员：待审核的商家信息变更列表 */
    Page<MerchantInfoChangeVO> pagePendingInfoChange(int pageNum, int pageSize);

    /** 管理员：审核商家信息变更 */
    void auditInfoChange(Long changeId, Integer auditStatus, String auditMsg);
}
