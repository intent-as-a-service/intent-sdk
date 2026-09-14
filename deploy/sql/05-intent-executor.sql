-- =============================================================
-- 内嵌 AI 意图 SDK · 执行器档案 DB 化存储（可维护）
-- 与 intent_spec 同一模式：启动时 classpath YAML 作为种子写入（已存在则跳过），
-- 后台增删改即时生效。
-- 宿主读取 profile_json 后经 ExecutorProfileLoader.parse(json, false) 反序列化，
-- 经 ExecutorProfiles.build 构建后注册进 IntentRuntimeConfig.customExecutors。
-- =============================================================

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
    KEY `idx_executor` (`executor_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='执行器档案';
