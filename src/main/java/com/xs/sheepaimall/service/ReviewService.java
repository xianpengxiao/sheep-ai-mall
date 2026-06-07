package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.ReviewDTO;
import com.xs.sheepaimall.entity.ProductReview;
import com.xs.sheepaimall.vo.ReviewVO;

import java.util.List;

/**
 * 商品评价 Service
 */
public interface ReviewService extends IService<ProductReview> {

    /** 提交评价（需校验订单归属和已完成状态） */
    ReviewVO create(ReviewDTO dto);

    /** 查询某个商品的所有评价（仅显示状态） */
    Page<ReviewVO> pageBySpu(Long spuId, int pageNum, int pageSize);

    /** [商家] 分页查询店铺商品的评价（支持按状态筛选、内容关键词搜索） */
    Page<ReviewVO> pageByMerchant(int pageNum, int pageSize, Integer status, Integer reviewStatus, String keyword);

    /** [管理员] 隐藏/显示评价 */
    void toggleStatus(Long id, Integer status);

    /** [管理员] 删除评价 */
    void removeReview(Long id);

    /** [用户] 删除自己的评价 */
    void deleteMyReview(Long id);

    /** [用户] 通过订单明细ID查询自己的评价 */
    ReviewVO getByOrderItemId(Long orderItemId);

    /** [用户] 显示/隐藏自己的评价 */
    void toggleMyStatus(Long id, Integer status);
}
