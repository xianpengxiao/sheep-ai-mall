package com.xs.sheepaimall.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xs.sheepaimall.dto.AddressSaveDTO;
import com.xs.sheepaimall.entity.ShippingAddress;
import com.xs.sheepaimall.vo.ShippingAddressVO;

import java.util.List;

/** 收货地址 Service */
public interface ShippingAddressService extends IService<ShippingAddress> {

    /** 添加地址 */
    ShippingAddressVO add(AddressSaveDTO dto);

    /** 修改地址 */
    ShippingAddressVO update(AddressSaveDTO dto);

    /** 删除地址 */
    void remove(Long id, Long userId);

    /** 查询用户所有地址 */
    List<ShippingAddressVO> listByUserId(Long userId);

    /** 设置默认地址（每个用户仅一个默认地址） */
    void setDefault(Long id, Long userId);

    /** 查询地址详情 */
    ShippingAddressVO getDetail(Long id, Long userId);
}
