-- ============================================================
-- SheepAIMall 电商数据库建表脚本 (MySQL 8.0+)
-- ============================================================

CREATE DATABASE IF NOT EXISTS sheep_ai_mall
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;
USE sheep_ai_mall;

-- ==================== 会员表 ====================
DROP TABLE IF EXISTS member;
CREATE TABLE member
(
    id          BIGINT       NOT NULL COMMENT '会员ID',
    openid      VARCHAR(64)  NOT NULL COMMENT '微信openid',
    nickname    VARCHAR(64)  DEFAULT '' COMMENT '昵称',
    avatar      VARCHAR(512) DEFAULT '' COMMENT '头像URL',
    phone       VARCHAR(20)  DEFAULT '' COMMENT '手机号',
    status      TINYINT      DEFAULT 1 COMMENT '状态 0禁用 1正常',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_openid (openid),
    INDEX idx_phone (phone)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='会员表';


-- ==================== 商品分类表 ====================
DROP TABLE IF EXISTS category;
CREATE TABLE category
(
    id          BIGINT      NOT NULL COMMENT '分类ID',
    parent_id   BIGINT      DEFAULT 0 COMMENT '父分类ID 0=顶级分类',
    name        VARCHAR(64) NOT NULL COMMENT '分类名称',
    icon        VARCHAR(256) DEFAULT '' COMMENT '图标URL',
    sort_order  INT         DEFAULT 0 COMMENT '排序值 越小越前',
    status      TINYINT     DEFAULT 1 COMMENT '状态 0禁用 1启用',
    deleted     TINYINT     DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_sort_order (sort_order)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品分类表';


-- ==================== 商品SPU表 ====================
DROP TABLE IF EXISTS spu;
CREATE TABLE spu
(
    id          BIGINT       NOT NULL COMMENT 'SPU ID',
    category_id BIGINT       NOT NULL COMMENT '分类ID',
    name        VARCHAR(128) NOT NULL COMMENT '商品名称',
    sub_title   VARCHAR(256) DEFAULT '' COMMENT '副标题/卖点',
    brand       VARCHAR(64)  DEFAULT '' COMMENT '品牌',
    description TEXT COMMENT '商品描述(富文本)',
    main_image  VARCHAR(512) DEFAULT '' COMMENT '主图URL',
    image_list  TEXT COMMENT '图片列表 JSON ["url1","url2"]',
    status      TINYINT      DEFAULT 0 COMMENT '状态 0下架 1上架',
    sales_count INT          DEFAULT 0 COMMENT '销量',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_category_id (category_id),
    INDEX idx_status (status),
    FULLTEXT INDEX ft_name (name) /* MySQL 8 内置全文索引 */
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品SPU表';


-- ==================== 商品SKU表 ====================
DROP TABLE IF EXISTS sku;
CREATE TABLE sku
(
    id        BIGINT        NOT NULL COMMENT 'SKU ID',
    spu_id    BIGINT        NOT NULL COMMENT 'SPU ID',
    sku_code  VARCHAR(64)   NOT NULL COMMENT 'SKU编码',
    sku_name  VARCHAR(128)  NOT NULL COMMENT '规格名称 如"红色-128G"',
    spec_info TEXT COMMENT '规格信息 JSON {"颜色":"红色","容量":"128G"}',
    price     DECIMAL(10,2) NOT NULL COMMENT '售价',
    stock     INT           DEFAULT 0 COMMENT '库存',
    image     VARCHAR(512)  DEFAULT '' COMMENT 'SKU图片',
    status    TINYINT       DEFAULT 1 COMMENT '状态 0禁用 1启用',
    deleted   TINYINT       DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_spu_id (spu_id),
    UNIQUE INDEX idx_sku_code (sku_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='商品SKU表';


-- ==================== 购物车表 ====================
DROP TABLE IF EXISTS cart;
CREATE TABLE cart
(
    id          BIGINT   NOT NULL COMMENT '购物车ID',
    member_id   BIGINT   NOT NULL COMMENT '会员ID',
    spu_id      BIGINT   NOT NULL COMMENT 'SPU ID',
    sku_id      BIGINT   NOT NULL COMMENT 'SKU ID',
    quantity    INT      DEFAULT 1 COMMENT '数量',
    selected    TINYINT  DEFAULT 1 COMMENT '是否选中 0未选 1已选',
    deleted     TINYINT  DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_member_id (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='购物车表';


-- ==================== 订单表 ====================
DROP TABLE IF EXISTS order_info;
CREATE TABLE order_info
(
    id               BIGINT        NOT NULL COMMENT '订单ID',
    order_no         VARCHAR(32)   NOT NULL COMMENT '订单号',
    member_id        BIGINT        NOT NULL COMMENT '会员ID',
    total_amount     DECIMAL(10,2) NOT NULL COMMENT '商品总金额',
    pay_amount       DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    status           TINYINT       DEFAULT 0 COMMENT '订单状态 0待支付 1已支付 2已发货 3已完成 4已取消',
    receiver_name    VARCHAR(32)   DEFAULT '' COMMENT '收货人',
    receiver_phone   VARCHAR(20)   DEFAULT '' COMMENT '收货电话',
    receiver_address VARCHAR(256)  DEFAULT '' COMMENT '收货地址',
    remark           VARCHAR(512)  DEFAULT '' COMMENT '订单备注',
    pay_time         DATETIME      DEFAULT NULL COMMENT '支付时间',
    delivery_time    DATETIME      DEFAULT NULL COMMENT '发货时间',
    finish_time      DATETIME      DEFAULT NULL COMMENT '完成时间',
    cancel_time      DATETIME      DEFAULT NULL COMMENT '取消时间',
    deleted          TINYINT       DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_order_no (order_no),
    INDEX idx_member_id (member_id),
    INDEX idx_status (status),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单表';


-- ==================== 订单明细表 ====================
DROP TABLE IF EXISTS order_item;
CREATE TABLE order_item
(
    id         BIGINT        NOT NULL COMMENT '订单明细ID',
    order_id   BIGINT        NOT NULL COMMENT '订单ID',
    spu_id     BIGINT        NOT NULL COMMENT 'SPU ID',
    sku_id     BIGINT        NOT NULL COMMENT 'SKU ID',
    sku_name   VARCHAR(128)  NOT NULL COMMENT 'SKU名称(冗余)',
    price      DECIMAL(10,2) NOT NULL COMMENT '下单时单价',
    quantity   INT           NOT NULL COMMENT '数量',
    total_price DECIMAL(10,2) NOT NULL COMMENT '小计',
    image      VARCHAR(512)  DEFAULT '' COMMENT '商品图片(冗余)',
    deleted    TINYINT       DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_order_id (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细表';


-- ==================== 支付记录表 ====================
DROP TABLE IF EXISTS payment_record;
CREATE TABLE payment_record
(
    id             BIGINT        NOT NULL COMMENT '记录ID',
    order_id       BIGINT        NOT NULL COMMENT '订单ID',
    order_no       VARCHAR(32)   NOT NULL COMMENT '订单编号',
    member_id      BIGINT        NOT NULL COMMENT '会员ID',
    transaction_id VARCHAR(64)   DEFAULT '' COMMENT '微信支付交易ID',
    prepay_id      VARCHAR(64)   DEFAULT '' COMMENT '微信预支付ID',
    pay_amount     DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '实付金额',
    pay_method     VARCHAR(32)   DEFAULT 'WECHAT_JSAPI' COMMENT '支付方式',
    status         VARCHAR(16)   DEFAULT 'PENDING' COMMENT '状态 PENDING/SUCCESS/FAILED/CLOSED/REFUNDED',
    pay_time       DATETIME      DEFAULT NULL COMMENT '支付完成时间',
    deleted        TINYINT       DEFAULT 0 COMMENT '逻辑删除',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_order_id (order_id),
    INDEX idx_order_no (order_no),
    INDEX idx_transaction_id (transaction_id),
    INDEX idx_member_id (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='支付记录表';

-- ==================== AI文案生成记录表 ====================
DROP TABLE IF EXISTS ai_generate_record;
CREATE TABLE ai_generate_record
(
    id          BIGINT       NOT NULL COMMENT '记录ID',
    spu_id      BIGINT       NOT NULL COMMENT '关联SPU ID',
    type        TINYINT      NOT NULL COMMENT '生成类型 1商品标题 2商品描述 3广告文案 4营销话术',
    prompt      TEXT COMMENT '输入的prompt',
    result      MEDIUMTEXT COMMENT 'AI生成结果',
    model       VARCHAR(64)  DEFAULT '' COMMENT '调用模型 如claude-opus-4-7',
    status      TINYINT      DEFAULT 0 COMMENT '状态 0处理中 1已完成 2失败',
    fail_reason VARCHAR(512) DEFAULT '' COMMENT '失败原因',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_spu_id (spu_id),
    INDEX idx_type (type),
    INDEX idx_create_time (create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI文案生成记录表';
