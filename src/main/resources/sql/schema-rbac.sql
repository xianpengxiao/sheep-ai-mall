-- ============================================================
-- SheepAIMall RBAC 权限模型建表脚本 (MySQL 8.0+)
-- 包含：系统用户、角色、权限、用户-角色关联、角色-权限关联
-- ============================================================

USE sheep_ai_mall;

-- ==================== 系统用户表 ====================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user
(
    id          BIGINT       NOT NULL COMMENT '用户ID',
    username    VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(256) NOT NULL COMMENT '加密密码(BCrypt)',
    real_name   VARCHAR(64)  DEFAULT '' COMMENT '真实姓名',
    phone       VARCHAR(20)  DEFAULT '' COMMENT '手机号',
    email       VARCHAR(128) DEFAULT '' COMMENT '邮箱',
    avatar      VARCHAR(512) DEFAULT '' COMMENT '头像URL',
    status      TINYINT      DEFAULT 1 COMMENT '状态 0禁用 1正常 2锁定',
    last_login  DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    login_ip    VARCHAR(64)  DEFAULT '' COMMENT '最后登录IP',
    remark      VARCHAR(512) DEFAULT '' COMMENT '备注',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_username (username),
    INDEX idx_phone (phone),
    INDEX idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';


-- ==================== 系统角色表 ====================
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role
(
    id          BIGINT       NOT NULL COMMENT '角色ID',
    role_code   VARCHAR(64)  NOT NULL COMMENT '角色编码(权限标识) 如 ROLE_ADMIN',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称 如 管理员',
    description VARCHAR(256) DEFAULT '' COMMENT '角色描述',
    status      TINYINT      DEFAULT 1 COMMENT '状态 0禁用 1正常',
    sort_order  INT          DEFAULT 0 COMMENT '排序值 越小越前',
    deleted     TINYINT      DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_role_code (role_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统角色表';


-- ==================== 系统权限表 ====================
DROP TABLE IF EXISTS sys_permission;
CREATE TABLE sys_permission
(
    id            BIGINT       NOT NULL COMMENT '权限ID',
    parent_id     BIGINT       DEFAULT 0 COMMENT '父权限ID 0=顶级权限/菜单',
    perm_code     VARCHAR(128) NOT NULL COMMENT '权限标识 如 spu:create spu:delete',
    perm_name     VARCHAR(64)  NOT NULL COMMENT '权限名称 如 新增商品',
    perm_type     TINYINT      DEFAULT 1 COMMENT '类型 1菜单 2按钮 3接口',
    path          VARCHAR(256) DEFAULT '' COMMENT '前端路由/接口路径',
    icon          VARCHAR(128) DEFAULT '' COMMENT '菜单图标',
    sort_order    INT          DEFAULT 0 COMMENT '排序值 越小越前',
    status        TINYINT      DEFAULT 1 COMMENT '状态 0禁用 1正常',
    deleted       TINYINT      DEFAULT 0 COMMENT '逻辑删除 0未删除 1已删除',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX idx_perm_code (perm_code),
    INDEX idx_parent_id (parent_id),
    INDEX idx_perm_type (perm_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='系统权限表';


-- ==================== 用户-角色关联表 ====================
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role
(
    id      BIGINT NOT NULL COMMENT '关联ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_role_id (role_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户-角色关联表';


-- ==================== 角色-权限关联表 ====================
DROP TABLE IF EXISTS sys_role_permission;
CREATE TABLE sys_role_permission
(
    id            BIGINT NOT NULL COMMENT '关联ID',
    role_id       BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (id),
    INDEX idx_role_id (role_id),
    INDEX idx_permission_id (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色-权限关联表';


-- ==================== 初始化数据 ====================

-- 管理员账号由 DataInitRunner 在应用启动时自动创建（密码经 BCrypt 加密）
-- 此处不再 INSERT sys_user，避免硬编码密码哈希不一致的问题
-- 如需手动插入管理员，请使用 BCrypt 工具对原始密码加密后执行 INSERT

-- 角色
INSERT INTO sys_role (id, role_code, role_name, description, sort_order)
VALUES (1, 'ROLE_ADMIN', '超级管理员', '拥有系统全部权限', 1),
       (2, 'ROLE_OPERATOR', '运营人员', '商品管理、订单管理权限', 2),
       (3, 'ROLE_VIEWER', '只读用户', '仅查看权限', 3);

-- 权限（按模块分组）
INSERT INTO sys_permission (id, parent_id, perm_code, perm_name, perm_type, sort_order)
VALUES
-- 商品管理
(100, 0, 'spu:menu',     '商品管理',      1, 1),
(101, 100, 'spu:list',   '商品列表',      1, 1),
(102, 100, 'spu:create', '新增商品',      2, 2),
(103, 100, 'spu:update', '编辑商品',      2, 3),
(104, 100, 'spu:delete', '删除商品',      2, 4),
-- 分类管理
(200, 0, 'category:menu',   '分类管理',    1, 2),
(201, 200, 'category:list', '分类列表',    1, 1),
(202, 200, 'category:create','新增分类',   2, 2),
(203, 200, 'category:update','编辑分类',   2, 3),
(204, 200, 'category:delete','删除分类',   2, 4),
-- 订单管理
(300, 0, 'order:menu',   '订单管理',      1, 3),
(301, 300, 'order:list', '订单列表',      1, 1),
(302, 300, 'order:cancel','取消订单',     2, 2),
-- 用户管理
(400, 0, 'sys:menu',     '系统管理',      1, 4),
(401, 400, 'sys:user:list',   '用户列表',  1, 1),
(402, 400, 'sys:user:create', '新增用户',  2, 2),
(403, 400, 'sys:user:update', '编辑用户',  2, 3),
(404, 400, 'sys:role:list',   '角色列表',  1, 4),
(405, 400, 'sys:role:create', '新增角色',  2, 5);

-- 管理员拥有所有角色
INSERT INTO sys_user_role (id, user_id, role_id) VALUES (1, 1, 1);

-- 超级管理员拥有所有权限
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT
    ROW_NUMBER() OVER (ORDER BY p.id) + 1000,
    1,
    p.id
FROM sys_permission p;

-- 运营人员权限（商品管理 + 订单管理）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT
    ROW_NUMBER() OVER (ORDER BY p.id) + 2000,
    2,
    p.id
FROM sys_permission p
WHERE p.perm_code LIKE 'spu:%' OR p.perm_code LIKE 'category:%' OR p.perm_code LIKE 'order:%';

-- 只读用户权限（仅查看）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT
    ROW_NUMBER() OVER (ORDER BY p.id) + 3000,
    3,
    p.id
FROM sys_permission p
WHERE p.perm_code LIKE '%:list' OR p.perm_code LIKE '%:menu';
