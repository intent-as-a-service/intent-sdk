-- =============================================================
-- 内嵌 AI 意图 SDK · 意图上架与角色权限配置
-- 适用于完整版 ruoyi-office 数据库（dump-ruoyi-office-20260305.sql 之后执行）
-- 注意：本库 system_menu 已占用 5219 以前编号，故菜单使用 9100+ 段；
--       演示用户使用 150/151。
-- =============================================================

-- 1. 意图配置表（上架状态 + 可见角色；roles 为角色编码 JSON 数组，["*"]=不限制）
CREATE TABLE IF NOT EXISTS `intent_config` (
    `id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '编号',
    `intent_id`  varchar(128) NOT NULL COMMENT '意图编号',
    `enabled`    bit(1)       NOT NULL DEFAULT b'1' COMMENT '是否上架',
    `roles`      varchar(512) NOT NULL DEFAULT '[\"*\"]' COMMENT '可见角色编码（JSON 数组，[\"*\"]=不限制）',
    `remark`     varchar(500)          DEFAULT NULL COMMENT '备注',
    `creator`    varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`    varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`    bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`  bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_intent` (`intent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='意图配置';

-- 2. 菜单：意图中心（目录） / 智能操作 / 意图管理 / 配置保存按钮 / 工作台（登录默认落地页，隐藏菜单但路由可用）
INSERT IGNORE INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES (9100, '意图中心', '', 1, 90, 0, '/intent', 'ant-design:api-outlined', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
       (9101, '意图调试台', '', 2, 1, 9100, 'center', 'ant-design:thunderbolt-outlined', 'intent/center/index', 'IntentCenter', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
       (9102, '意图管理', 'intent:config:query', 2, 2, 9100, 'config', 'ant-design:setting-outlined', 'intent/config/index', 'IntentConfig', 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
       (9103, '意图配置保存', 'intent:config:update', 3, 1, 9102, '', '', NULL, NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'),
       (9110, '工作台', '', 2, 1, 0, '/workspace', 'ant-design:appstore-outlined', 'dashboard/workspace/index', 'Workspace', 0, b'0', b'1', b'1', '1', NOW(), '1', NOW(), b'0');

-- 3. 角色授权：超级管理员(1)全量；CRM 管理员(3)与普通角色(2)可进入智能操作
INSERT IGNORE INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `create_time`, `tenant_id`)
VALUES (1, 9100, '1', NOW(), 1), (1, 9101, '1', NOW(), 1), (1, 9102, '1', NOW(), 1), (1, 9103, '1', NOW(), 1), (1, 9110, '1', NOW(), 1),
       (2, 9100, '1', NOW(), 1), (2, 9101, '1', NOW(), 1), (2, 9110, '1', NOW(), 1),
       (3, 9100, '1', NOW(), 1), (3, 9101, '1', NOW(), 1), (3, 9110, '1', NOW(), 1);

-- 4. 演示用户（密码同 admin123；150=CRM 管理员，151=普通角色，用于多角色验证）
INSERT IGNORE INTO `system_users` (`id`, `username`, `password`, `nickname`, `dept_id`, `status`, `creator`, `create_time`, `deleted`, `tenant_id`)
VALUES (150, 'xiaowang', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '销售小王', 103, 0, '1', NOW(), b'0', 1),
       (151, 'xiaoli', '$2a$04$.vd8nPeLwxt6hnSzmAoAyul8BOLX7Cib6QhcxRe30rfvrIPQHH1OG', '客服小李', 103, 0, '1', NOW(), b'0', 1);
INSERT IGNORE INTO `system_user_role` (`user_id`, `role_id`, `creator`, `create_time`, `tenant_id`)
VALUES (150, 3, '1', NOW(), 1),
       (151, 2, '1', NOW(), 1);

-- 5. 意图演示配置：分析意图对管理与 CRM 角色开放，其余不限制
INSERT IGNORE INTO `intent_config` (`id`, `intent_id`, `enabled`, `roles`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (1, 'crm.customer.analyze',       b'1', '["super_admin","crm_admin"]', '仅管理与 CRM 角色可见', '1', NOW(), 1),
       (2, 'crm.customer.followup-plan', b'1', '["*"]',                       '所有人可用',            '1', NOW(), 1),
       (3, 'scm.stock.check',            b'1', '["*"]',                       '跨系统演示',            '1', NOW(), 1);

-- 6. 新增页面级意图的演示配置（合同/商机/回款/线索）
INSERT IGNORE INTO `intent_config` (`id`, `intent_id`, `enabled`, `roles`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (4, 'crm.contract.risk-review',        b'1', '["super_admin","crm_admin"]', '合同风险审查',   '1', NOW(), 1),
       (5, 'crm.business.win-analysis',       b'1', '["*"]',                       '商机赢单分析',   '1', NOW(), 1),
       (6, 'crm.receivable.collection-plan',  b'1', '["*"]',                       '回款催收计划',   '1', NOW(), 1),
       (7, 'crm.clue.follow-up-suggestion',   b'1', '["*"]',                       '线索跟进建议',   '1', NOW(), 1);
