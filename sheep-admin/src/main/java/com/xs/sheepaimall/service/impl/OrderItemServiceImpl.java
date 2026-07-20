package com.xs.sheepaimall.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.entity.OrderItem;
import com.xs.sheepaimall.mapper.OrderItemMapper;
import com.xs.sheepaimall.service.OrderItemService;
import org.springframework.stereotype.Service;

@Service
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> implements OrderItemService {
}
