package com.xs.sheepaimall.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xs.sheepaimall.dto.CartAddDTO;
import com.xs.sheepaimall.entity.Cart;
import com.xs.sheepaimall.entity.Sku;
import com.xs.sheepaimall.entity.Spu;
import com.xs.sheepaimall.mapper.CartMapper;
import com.xs.sheepaimall.service.CartService;
import com.xs.sheepaimall.service.SkuService;
import com.xs.sheepaimall.service.SpuService;
import com.xs.sheepaimall.vo.CartVO;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 购物车 Service —— Redis Hash 缓存 + MySQL 持久化双写。
 *
 * Redis 结构:
 *   Key:  cart::{memberId}
 *   Type: Hash
 *   Field: {skuId}
 *   Value: JSON {"cartId":1,"spuId":10,"quantity":3,"selected":1}
 */
@Service
public class CartServiceImpl extends ServiceImpl<CartMapper, Cart> implements CartService {

    private static final Logger log = LoggerFactory.getLogger(CartServiceImpl.class);

    private static final String CART_KEY_PREFIX = "cart::";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpuService spuService;

    @Resource
    private SkuService skuService;

    // ==================== 加入购物车 ====================

    @Override
    public void add(CartAddDTO dto) {
        Long memberId = dto.getMemberId();
        Long skuId = dto.getSkuId();
        int qty = dto.getQuantity() != null ? dto.getQuantity() : 1;

        // 查询是否已有同会员+同SKU的购物车记录（false = 多条时不抛异常）
        Cart exist = this.getOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId)
                .eq(Cart::getSkuId, skuId), false);

        if (exist != null) {
            exist.setQuantity(exist.getQuantity() + qty);
            this.updateById(exist);
            syncItemToRedis(memberId, exist);
        } else {
            Cart cart = new Cart();
            cart.setMemberId(memberId);
            cart.setSpuId(dto.getSpuId());
            cart.setSkuId(skuId);
            cart.setQuantity(qty);
            cart.setSelected(1);
            this.save(cart);
            syncItemToRedis(memberId, cart);
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
            syncItemToRedis(updated.getMemberId(), updated);
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
            syncItemToRedis(updated.getMemberId(), updated);
        }
    }

    // ==================== 删除单条 ====================

    @Override
    public void removeItem(Long id) {
        Cart cart = this.getById(id);
        if (cart == null) return;

        this.removeById(id);
        removeItemFromRedis(cart.getMemberId(), cart.getSkuId());
    }

    // ==================== 一键清空 ====================

    @Override
    public void clear(Long memberId) {
        // 删除 MySQL 中该会员所有购物车记录
        this.remove(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId));
        // 删除 Redis 中整个 Hash key
        stringRedisTemplate.delete(CART_KEY_PREFIX + memberId);
    }

    // ==================== 购物车列表 ====================

    @Override
    public List<CartVO> listByMemberId(Long memberId) {
        String redisKey = CART_KEY_PREFIX + memberId;

        // 1. 优先从 Redis Hash 读取（Redis 不可用时降级 MySQL）
        Map<Object, Object> cachedItems;
        try {
            cachedItems = stringRedisTemplate.opsForHash().entries(redisKey);
        } catch (DataAccessException e) {
            log.warn("Redis读取购物车失败 memberId={}, 降级MySQL", memberId, e);
            return loadCartFromDb(memberId);
        }

        // 2. Redis 为空，从 MySQL 加载并回写 Redis
        if (cachedItems.isEmpty()) {
            return loadCartFromDb(memberId);
        }

        // 3. Redis 命中，解析为 Cart 列表再组装 VO
        List<Cart> carts = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : cachedItems.entrySet()) {
            Long skuId = Long.valueOf((String) entry.getKey());
            Cart cart = cacheJsonToCart((String) entry.getValue(), skuId);
            if (cart != null) {
                cart.setMemberId(memberId);
                carts.add(cart);
            }
        }

        return buildCartVOList(carts);
    }

    /** 从 MySQL 加载购物车并回写 Redis（Redis miss 或故障时调用） */
    private List<CartVO> loadCartFromDb(Long memberId) {
        List<Cart> dbCarts = this.list(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId)
                .orderByDesc(Cart::getCreateTime));
        if (dbCarts.isEmpty()) return List.of();

        // 尝试回写 Redis（失败忽略）
        try {
            String redisKey = CART_KEY_PREFIX + memberId;
            Map<String, String> hashMap = new HashMap<>();
            for (Cart cart : dbCarts) {
                hashMap.put(String.valueOf(cart.getSkuId()), toCacheJson(cart));
            }
            stringRedisTemplate.opsForHash().putAll(redisKey, hashMap);
        } catch (DataAccessException e) {
            log.warn("Redis回写购物车失败 memberId={}", memberId, e);
        }

        return buildCartVOList(dbCarts);
    }

    // ==================== 全选 / 取消全选 ====================

    @Override
    public void selectAll(Long memberId, Integer selected) {
        // MySQL 批量更新
        Cart update = new Cart();
        update.setSelected(selected);
        this.update(update, new LambdaQueryWrapper<Cart>()
                .eq(Cart::getMemberId, memberId));

        // 同步 Redis Hash 中每条记录的 selected 字段
        String redisKey = CART_KEY_PREFIX + memberId;
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
    private void syncItemToRedis(Long memberId, Cart cart) {
        try {
            stringRedisTemplate.opsForHash().put(
                    CART_KEY_PREFIX + memberId,
                    String.valueOf(cart.getSkuId()),
                    toCacheJson(cart));
        } catch (DataAccessException e) {
            log.error("购物车Redis同步失败 memberId={} skuId={}", memberId, cart.getSkuId(), e);
            // Redis 同步失败不影响主流程，下次查询时会从 MySQL 重建
        }
    }

    /** 从 Redis Hash 中删除单条 */
    private void removeItemFromRedis(Long memberId, Long skuId) {
        try {
            stringRedisTemplate.opsForHash().delete(
                    CART_KEY_PREFIX + memberId,
                    String.valueOf(skuId));
        } catch (DataAccessException e) {
            log.error("购物车Redis删除失败 memberId={} skuId={}", memberId, skuId, e);
        }
    }

    // ==================== CartVO 组装 ====================

    /** 将 Cart 列表转换为 CartVO 列表（含 SPU/SKU 冗余信息） */
    private List<CartVO> buildCartVOList(List<Cart> carts) {
        if (carts.isEmpty()) return List.of();

        // 批量查询 SPU 和 SKU 信息，避免 N+1
        Set<Long> spuIds = carts.stream().map(Cart::getSpuId).collect(Collectors.toSet());
        Set<Long> skuIds = carts.stream().map(Cart::getSkuId).collect(Collectors.toSet());

        Map<Long, Spu> spuMap = spuService.listByIds(spuIds).stream()
                .collect(Collectors.toMap(Spu::getId, s -> s, (a, b) -> a));
        Map<Long, Sku> skuMap = skuService.listByIds(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));

        return carts.stream()
                .map(cart -> toCartVO(cart, spuMap.get(cart.getSpuId()), skuMap.get(cart.getSkuId())))
                .collect(Collectors.toList());
    }

    /** 单条 Cart → CartVO */
    private CartVO toCartVO(Cart cart, Spu spu, Sku sku) {
        CartVO vo = new CartVO();
        vo.setId(cart.getId());
        vo.setMemberId(cart.getMemberId());
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
