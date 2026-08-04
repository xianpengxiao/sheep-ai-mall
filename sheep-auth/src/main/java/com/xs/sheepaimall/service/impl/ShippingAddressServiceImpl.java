package com.xs.sheepaimall.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.common.BizException;
import com.xs.sheepaimall.common.ResultCode;
import com.xs.sheepaimall.dto.AddressSaveDTO;
import com.xs.sheepaimall.entity.ShippingAddress;
import com.xs.sheepaimall.mapper.ShippingAddressMapper;
import com.xs.sheepaimall.service.ShippingAddressService;
import com.xs.sheepaimall.vo.ShippingAddressVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/** 收货地址 Service 实现 */
@Service
public class ShippingAddressServiceImpl extends ServiceImpl<ShippingAddressMapper, ShippingAddress>
        implements ShippingAddressService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShippingAddressVO add(AddressSaveDTO dto) {
        ShippingAddress address = new ShippingAddress();
        BeanUtil.copyProperties(dto, address);

        // 如果是默认地址，先清除该用户其他默认地址
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(dto.getUserId());
        }

        this.save(address);
        return toVO(address);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShippingAddressVO update(AddressSaveDTO dto) {
        Long id = dto.getId();
        if (id == null) {
            throw new BizException("地址ID不能为空");
        }

        ShippingAddress address = this.getById(id);
        if (address == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "地址不存在");
        }
        if (!address.getUserId().equals(dto.getUserId())) {
            throw new BizException("无权修改此地址");
        }

        // 如果是默认地址，先清除该用户其他默认地址
        if (Integer.valueOf(1).equals(dto.getIsDefault())) {
            clearDefault(dto.getUserId());
        }

        BeanUtil.copyProperties(dto, address);
        this.updateById(address);
        return toVO(address);
    }

    @Override
    public void remove(Long id, Long userId) {
        ShippingAddress address = this.getById(id);
        if (address == null) return;
        if (!address.getUserId().equals(userId)) {
            throw new BizException("无权删除此地址");
        }
        this.removeById(id);
    }

    @Override
    public List<ShippingAddressVO> listByUserId(Long userId) {
        List<ShippingAddress> list = this.list(new LambdaQueryWrapper<ShippingAddress>()
                .eq(ShippingAddress::getUserId, userId)
                .orderByDesc(ShippingAddress::getIsDefault)
                .orderByDesc(ShippingAddress::getCreateTime));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id, Long userId) {
        ShippingAddress address = this.getById(id);
        if (address == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BizException("无权操作此地址");
        }

        // 清除该用户所有默认地址标记
        clearDefault(userId);
        // 设置新默认地址
        address.setIsDefault(1);
        this.updateById(address);
    }

    @Override
    public ShippingAddressVO getDetail(Long id, Long userId) {
        ShippingAddress address = this.getById(id);
        if (address == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "地址不存在");
        }
        if (!address.getUserId().equals(userId)) {
            throw new BizException("无权查看此地址");
        }
        return toVO(address);
    }

    /** 清除指定用户的所有默认地址标记 */
    private void clearDefault(Long userId) {
        lambdaUpdate()
                .eq(ShippingAddress::getUserId, userId)
                .eq(ShippingAddress::getIsDefault, 1)
                .set(ShippingAddress::getIsDefault, 0)
                .update();
    }

    private ShippingAddressVO toVO(ShippingAddress address) {
        ShippingAddressVO vo = new ShippingAddressVO();
        BeanUtil.copyProperties(address, vo);
        return vo;
    }
}
