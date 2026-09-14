-- =============================================================
-- 内嵌 AI 意图 SDK · 意图三表 DDL（MySQL 5.7+ / 8.x）
--
-- 这是 SDK 自有资产的三张表，与其它方言版本（postgresql/ oracle/ sqlserver/）
-- 一一对应。deploy/sql/02-intent-admin.sql 还额外包含 ruoyi-office 宿主的
-- 菜单/角色/演示用户种子，那部分属于宿主应用，不在 SDK 交付范围内。
--
-- 零建表替代方案：SDK 提供内存与会话文件仓储（intent.host.storage=FILE），
-- 不开这三张表也能完整运行；开表是为了让意图可在后台增删改并即时生效。
-- =============================================================

CREATE TABLE IF NOT EXISTS `intent_config` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `intent_id`   varchar(128) NOT NULL COMMENT '意图编号',
    `enabled`     bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否上架',
    `roles`       varchar(512) NOT NULL DEFAULT '["*"]' COMMENT '可见角色编码（JSON 数组，["*"]=不限制）',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    `creator`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_intent_config_intent` (`intent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='意图配置';

CREATE TABLE IF NOT EXISTS `intent_spec` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `intent_id`   varchar(128) NOT NULL COMMENT '意图编号（系统.域.动作）',
    `spec_json`   mediumtext   NOT NULL COMMENT 'IntentSpec 完整定义（JSON）',
    `source`      varchar(16)  NOT NULL DEFAULT 'custom' COMMENT '来源（builtin=classpath 种子 / custom=后台创建）',
    `remark`      varchar(500)          DEFAULT NULL COMMENT '备注',
    `creator`     varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`     varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`   bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_intent_spec_intent` (`intent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='意图规范';

CREATE TABLE IF NOT EXISTS `intent_executor` (
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `executor_id`  varchar(128) NOT NULL COMMENT '执行器标识（IntentSpec.executor 引用）',
    `profile_json` mediumtext   NOT NULL COMMENT 'ExecutorProfile 完整定义（JSON）',
    `source`       varchar(16)  NOT NULL DEFAULT 'custom' COMMENT '来源（builtin=classpath 种子 / custom=后台创建）',
    `remark`       varchar(500)          DEFAULT NULL COMMENT '备注',
    `creator`      varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`    bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_intent_executor_executor` (`executor_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='执行器档案';