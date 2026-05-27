package com.xs.sheepaimall.common;

/**
 * 缓存常量：缓存名称、TTL（秒）、锁超时等集中管理
 */
public final class CacheConstants {

    private CacheConstants() {}

    /** 缓存名 —— 商品详情 (key: spu::detail::{id}) */
    public static final String SPU_DETAIL = "spu::detail";

    /** 缓存名 —— 商品分类树 (key: category::tree) */
    public static final String CATEGORY_TREE = "category::tree";

    /** 缓存名 —— 热门商品分页 (key: spu::hot::page::{pageNum}::{pageSize}) */
    public static final String SPU_HOT_PAGE = "spu::hot::page";

    // ============ TTL 基准值（秒） ============

    /** 商品详情 30 分钟 */
    public static final long SPU_DETAIL_TTL = 30 * 60;

    /** 分类树 1 小时 */
    public static final long CATEGORY_TREE_TTL = 60 * 60;

    /** 热门商品分页 10 分钟 */
    public static final long SPU_HOT_PAGE_TTL = 10 * 60;

    /** 空值占位 TTL：5 分钟，用于防穿透 */
    public static final long NULL_VALUE_TTL = 5 * 60;

    /** TTL 随机抖动比例 ±20%，防雪崩 */
    public static final double TTL_JITTER_RATIO = 0.2;

    // ============ 防击穿分布式锁 ============

    /** 锁 key 前缀 */
    public static final String LOCK_PREFIX = "lock::cache";

    /** 等待锁的最大时间 3s */
    public static final long LOCK_WAIT_MS = 3000;

    /** 锁自动释放时间 5s */
    public static final long LOCK_LEASE_MS = 5000;

    /** 轮询间隔 100ms */
    public static final long LOCK_RETRY_INTERVAL_MS = 100;
}
