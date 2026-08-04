package com.xs.sheepaimall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.dto.CartAddDTO;
import com.xs.sheepaimall.entity.Cart;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.feign.ProductFeignClient;
import com.xs.sheepaimall.mapper.CartMapper;
import com.xs.sheepaimall.service.CartService;
import com.xs.sheepaimall.vo.CartVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 购物车 Service —— Redis Hash 缓存 + MySQL 持久化双写。
 *
 * Redis 结构:
 *   Key:  cart::{userId}
 *   Type: Hash
 *   Field: {skuId}
 *   Value: JSON {"cartId":1,"spuId":10,"quantity":3,"selected":1}
 */
@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private static final String CART_KEY_PREFIX = "cart::";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ProductFeignClient productFeignClient;

    // ==================== 加入购物车 ====================

    @Override
    public void add(CartAddDTO dto) {
        Long userId = dto.getUserId();
        Long skuId = dto.getSkuId();
        int qty = dto.getQuantity() != null ? dto.getQuantity() : 1;

        // 查询是否已有同会员+同SKU的购物车记录（false = 多条时不抛异常）
        Cart exist = this.getOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getSkuId, skuId), false);

        if (exist != null) {
            exist.setQuantity(exist.getQuantity() + qty);
            this.updateById(exist);
            syncItemToRedis(userId, exist);
        } else {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setSpuId(dto.getSpuId());
            cart.setSkuId(skuId);
            cart.setQuantity(qty);
            cart.setSelected(1);
            this.save(cart);
            syncItemToRedis(userId, cart);
        }
    }

    // ==================== 修改数量 ====================

    @Override
    public void updateQuantity(Long id, Integer quantity) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setQuantity(quantity);
        this.updateById(cart);

        // 查出最新数据同步到 Redis
        Cart updated = this.getById(id);
        if (updated != null) {
            syncItemToRedis(updated.getUserId(), updated);
        }
    }

    // ==================== 修改选中状态 ====================

    @Override
    public void updateSelected(Long id, Integer selected) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setSelected(selected);
        this.updateById(cart);

        Cart updated = this.getById(id);
        if (updated != null) {
            syncItemToRedis(updated.getUserId(), updated);
        }
    }

    // ==================== 删除单条 ====================

    @Override
    public void removeItem(Long id) {
        Cart cart = this.getById(id);
        if (cart == null) return;

        // 先删 Redis，再删 MySQL：避免 Redis 删除失败后缓存脏数据
        removeItemFromRedis(cart.getUserId(), cart.getSkuId());
        this.removeById(id);
    }

    // ==================== 批量删除 ====================

    @Override
    public void batchRemove(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        List<Cart> carts = this.listByIds(ids);
        if (carts.isEmpty()) return;

        // 按 userId 分组，批量删 Redis
        Map<Long, List<Cart>> grouped = carts.stream()
                .collect(Collectors.groupingBy(Cart::getUserId));
        grouped.forEach((userId, itemList) -> {
            List<Long> skuIds = itemList.stream().map(Cart::getSkuId).collect(Collectors.toList());
            stringRedisTemplate.opsForHash().delete(CART_KEY_PREFIX + userId, skuIds.stream().map(String::valueOf).toArray());
        });

        // 批量删 MySQL
        this.removeByIds(ids);
    }

    // ==================== 一键清空 ====================

    @Override
    public void clear(Long userId) {
        // 先删 Redis，再删 MySQL
        stringRedisTemplate.delete(CART_KEY_PREFIX + userId);
        this.remove(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId));
    }

    // ==================== 购物车列表 ====================

    @Override
    public List<CartVO> listByUserId(Long userId) {
        String redisKey = CART_KEY_PREFIX + userId;

        // 1. 优先从 Redis Hash 读取（Redis 不可用时降级 MySQL）
        Map<Object, Object> cachedItems;
        try {
            cachedItems = stringRedisTemplate.opsForHash().entries(redisKey);
        } catch (DataAccessException e) {
            log.warn("Redis读取购物车失败 userId={}, 降级MySQL", userId, e);
            return loadCartFromDb(userId);
        }

        // 2. Redis 为空，从 MySQL 加载并回写 Redis
        if (cachedItems.isEmpty()) {
            return loadCartFromDb(userId);
        }

        // 3. Redis 命中，解析为 Cart 列表再组装 VO
        List<Cart> carts = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : cachedItems.entrySet()) {
            Long skuId = Long.valueOf((String) entry.getKey());
            Cart cart = cacheJsonToCart((String) entry.getValue(), skuId);
            if (cart != null) {
                cart.setUserId(userId);
                carts.add(cart);
            }
        }

        return buildCartVOList(carts);
    }

    /** 从 MySQL 加载购物车并回写 Redis（Redis miss 或故障时调用） */
    private List<CartVO> loadCartFromDb(Long userId) {
        List<Cart> dbCarts = this.list(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime));
        if (dbCarts.isEmpty()) return List.of();

        // 尝试回写 Redis（失败忽略）
        try {
            String redisKey = CART_KEY_PREFIX + userId;
            Map<String, String> hashMap = new HashMap<>();
            for (Cart cart : dbCarts) {
                hashMap.put(String.valueOf(cart.getSkuId()), toCacheJson(cart));
            }
            stringRedisTemplate.opsForHash().putAll(redisKey, hashMap);
        } catch (DataAccessException e) {
            log.warn("Redis回写购物车失败 userId={}", userId, e);
        }

        return buildCartVOList(dbCarts);
    }

    // ==================== 全选 / 取消全选 ====================

    @Override
    public void selectAll(Long userId, Integer selected) {
        // MySQL 批量更新
        Cart update = new Cart();
        update.setSelected(selected);
        this.update(update, new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId));

        // 同步 Redis Hash 中每条记录的 selected 字段
        String redisKey = CART_KEY_PREFIX + userId;
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(redisKey);
        if (!entries.isEmpty()) {
            for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                CartItemCache cache = JSONUtil.toBean((String) entry.getValue(), CartItemCache.class);
                if (cache != null) {
                    cache.setSelected(selected);
                    stringRedisTemplate.opsForHash().put(redisKey,
                            (String) entry.getKey(),
                            JSONUtil.toJsonStr(cache));
                }
            }
        }
    }

    // ==================== Redis 同步（内部方法） ====================

    /** 同步单条购物车记录到 Redis Hash */
    private void syncItemToRedis(Long userId, Cart cart) {
        try {
            stringRedisTemplate.opsForHash().put(
                    CART_KEY_PREFIX + userId,
                    String.valueOf(cart.getSkuId()),
                    toCacheJson(cart));
        } catch (DataAccessException e) {
            log.error("购物车Redis同步失败 userId={} skuId={}", userId, cart.getSkuId(), e);
            // Redis 同步失败不影响主流程，下次查询时会从 MySQL 重建
        }
    }

    /** 从 Redis Hash 中删除单条 */
    private void removeItemFromRedis(Long userId, Long skuId) {
        try {
            stringRedisTemplate.opsForHash().delete(
                    CART_KEY_PREFIX + userId,
                    String.valueOf(skuId));
        } catch (DataAccessException e) {
            log.error("购物车Redis删除失败 userId={} skuId={}", userId, skuId, e);
        }
    }

    // ==================== CartVO 组装 ====================

    /** 将 Cart 列表转换为 CartVO 列表（含 SPU/SKU 冗余信息） */
    private List<CartVO> buildCartVOList(List<Cart> carts) {
        if (carts.isEmpty()) return List.of();

        // 批量查询 SPU 和 SKU 信息，避免 N+1
        Set<Long> spuIds = carts.stream().map(Cart::getSpuId).collect(Collectors.toSet());
        Set<Long> skuIds = carts.stream().map(Cart::getSkuId).collect(Collectors.toSet());

        Map<Long, Spu> spuMap = productFeignClient.listSpuByIds((List<Long>) spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, s -> s, (a, b) -> a));
        Map<Long, Sku> skuMap = productFeignClient.listSkuByIds((List<Long>) skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));

        return carts.stream()
                .map(cart -> toCartVO(cart, spuMap.get(cart.getSpuId()), skuMap.get(cart.getSkuId())))
                .collect(Collectors.toList());
    }

    /** 单条 Cart → CartVO */
    private CartVO toCartVO(Cart cart, Spu spu, Sku sku) {
        CartVO vo = new CartVO();
        vo.setId(cart.getId());
        vo.setUserId(cart.getUserId());
        vo.setSpuId(cart.getSpuId());
        vo.setSkuId(cart.getSkuId());
        vo.setQuantity(cart.getQuantity());
        vo.setSelected(cart.getSelected());

        if (spu != null) {
            vo.setSpuName(spu.getName());
            vo.setSpuImage(spu.getMainImage());
        }
        if (sku != null) {
            vo.setSkuName(sku.getSkuName());
            vo.setSkuImage(sku.getImage());
            vo.setPrice(sku.getPrice());
            if (sku.getSpecInfo() != null) {
                vo.setSpecInfo(JSONUtil.toBean(sku.getSpecInfo(), Map.class));
            }
        }
        return vo;
    }

    // ==================== 缓存序列化 ====================

    /** Cart → Redis Hash Value JSON */
    private String toCacheJson(Cart cart) {
        CartItemCache cache = new CartItemCache(
                cart.getId(), cart.getSpuId(), cart.getQuantity(), cart.getSelected());
        return JSONUtil.toJsonStr(cache);
    }

    /** Redis Hash Value JSON → Cart（skuId 从 Hash field key 传入） */
    private Cart cacheJsonToCart(String json, Long skuId) {
        CartItemCache cache = JSONUtil.toBean(json, CartItemCache.class);
        if (cache == null) return null;
        Cart cart = new Cart();
        cart.setId(cache.getCartId());
        cart.setSpuId(cache.getSpuId());
        cart.setSkuId(skuId);
        cart.setQuantity(cache.getQuantity());
        cart.setSelected(cache.getSelected());
        return cart;
    }

    /**
     * Redis Hash 中存储的购物车条目信息（最小必要字段）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemCache {
        private Long cartId;
        private Long spuId;
        private Integer quantity;
        private Integer selected;
    }
}
