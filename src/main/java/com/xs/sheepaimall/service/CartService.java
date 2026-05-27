package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.CartAddDTO;
import com.xs.sheepaimall.entity.Cart;
import com.xs.sheepaimall.vo.CartVO;

import java.util.List;

/**
 * 购物车 Service —— Redis Hash 缓存 + MySQL 持久化双写
 */
public interface CartService extends IService<Cart> {

    /** 加入购物车（已存在同SKU则累加数量） */
    void add(CartAddDTO dto);

    /** 修改数量 */
    void updateQuantity(Long id, Integer quantity);

    /** 修改选中状态 */
    void updateSelected(Long id, Integer selected);

    /** 删除单条购物车记录 */
    void removeItem(Long id);

    /** 一键清空会员购物车 */
    void clear(Long memberId);

    /** 查询会员购物车列表（含商品冗余信息，Redis优先） */
    List<CartVO> listByMemberId(Long memberId);

    /** 全选 / 取消全选 */
    void selectAll(Long memberId, Integer selected);
}
