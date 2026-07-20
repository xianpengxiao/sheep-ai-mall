package com.xs.sheepaimall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xs.sheepaimall.entity.ProductReview;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    /** 按订单明细ID查询评价记录（原生SQL，不受 @TableLogic 影响） */
    @Select("SELECT * FROM product_review WHERE order_item_id = #{orderItemId} LIMIT 1")
    ProductReview selectByOrderItemId(@Param("orderItemId") Long orderItemId);

    /** 更新评价内容（原生SQL，不受 @TableLogic 影响，用于删除后重新评论） */
    @Update("UPDATE product_review SET deleted = 0, review_status = 1, status = 1, " +
            "rating = #{rating}, describe_score = #{ds}, service_score = #{ss}, " +
            "logistics_score = #{ls}, content = #{content}, image_list = #{imageList} " +
            "WHERE id = #{id}")
    int updateForReReview(@Param("id") Long id,
                          @Param("rating") Integer rating,
                          @Param("ds") Integer ds,
                          @Param("ss") Integer ss,
                          @Param("ls") Integer ls,
                          @Param("content") String content,
                          @Param("imageList") String imageList);
}
