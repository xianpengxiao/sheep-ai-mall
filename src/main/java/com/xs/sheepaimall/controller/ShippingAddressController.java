package com.xs.sheepaimall.controller;

import com.xs.sheepaimall.common.R;
import com.xs.sheepaimall.dto.AddressSaveDTO;
import com.xs.sheepaimall.security.UserContext;
import com.xs.sheepaimall.service.ShippingAddressService;
import com.xs.sheepaimall.vo.ShippingAddressVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 收货地址接口 */
@Tag(name = "收货地址", description = "收货地址增删改查、设置默认")
@Validated
@RestController
@RequestMapping("/api/address")
public class ShippingAddressController {

    @Resource
    private ShippingAddressService addressService;

    @Operation(summary = "添加收货地址")
    @PostMapping("/add")
    public R<ShippingAddressVO> add(@Valid @RequestBody AddressSaveDTO dto) {
        dto.setUserId(UserContext.getUserId());
        return R.ok(addressService.add(dto));
    }

    @Operation(summary = "修改收货地址")
    @PutMapping("/update")
    public R<ShippingAddressVO> update(@Valid @RequestBody AddressSaveDTO dto) {
        dto.setUserId(UserContext.getUserId());
        return R.ok(addressService.update(dto));
    }

    @Operation(summary = "删除收货地址")
    @DeleteMapping("/{id}")
    public R<Object> remove(@Parameter(description = "地址ID") @PathVariable Long id) {
        addressService.remove(id, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "查询我的收货地址列表", description = "按默认地址优先、创建时间倒序排列")
    @GetMapping("/list")
    public R<List<ShippingAddressVO>> list() {
        return R.ok(addressService.listByUserId(UserContext.getUserId()));
    }

    @Operation(summary = "设置默认地址")
    @PutMapping("/{id}/default")
    public R<Object> setDefault(@Parameter(description = "地址ID") @PathVariable Long id) {
        addressService.setDefault(id, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "查询地址详情")
    @GetMapping("/{id}")
    public R<ShippingAddressVO> detail(@Parameter(description = "地址ID") @PathVariable Long id) {
        return R.ok(addressService.getDetail(id, UserContext.getUserId()));
    }
}
