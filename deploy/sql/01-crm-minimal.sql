-- =============================================================
-- 内嵌 AI 意图 SDK · 最小 CRM 表结构与种子数据
-- 适用于 ruoyi-office（MySQL 5.7+），库：ruoyi-office
-- 说明：仅为意图 SDK 演示所需的最小 CRM 子集（客户 + 跟进记录）。
-- CRM 字典（crm_customer_level / source / industry / follow_up_type）
-- 在 ruoyi-vue-pro.sql 基础库中已存在，无需重复初始化。
-- =============================================================

-- 1. 客户表（依据 CrmCustomerDO + BaseDO + tenant_id）
CREATE TABLE IF NOT EXISTS `crm_customer` (
    `id`                   bigint       NOT NULL AUTO_INCREMENT COMMENT '客户编号',
    `name`                 varchar(128) NOT NULL DEFAULT '' COMMENT '客户名称',
    `follow_up_status`     bit(1)       NOT NULL DEFAULT b'0' COMMENT '跟进状态',
    `contact_last_time`    datetime     DEFAULT NULL COMMENT '最后跟进时间',
    `contact_last_content` varchar(1000)         DEFAULT NULL COMMENT '最后跟进内容',
    `contact_next_time`    datetime     DEFAULT NULL COMMENT '下次联系时间',
    `owner_user_id`        bigint                DEFAULT NULL COMMENT '负责人用户编号',
    `owner_time`           datetime     DEFAULT NULL COMMENT '成为负责人时间',
    `lock_status`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '锁定状态',
    `deal_status`          bit(1)       NOT NULL DEFAULT b'0' COMMENT '成交状态',
    `mobile`               varchar(32)           DEFAULT '' COMMENT '手机',
    `telephone`            varchar(32)           DEFAULT '' COMMENT '电话',
    `qq`                   varchar(32)           DEFAULT '' COMMENT 'QQ',
    `wechat`               varchar(32)           DEFAULT '' COMMENT '微信',
    `email`                varchar(128)          DEFAULT '' COMMENT '邮箱',
    `area_id`              int                   DEFAULT NULL COMMENT '地区编号',
    `detail_address`       varchar(255)          DEFAULT '' COMMENT '详细地址',
    `industry_id`          int                   DEFAULT NULL COMMENT '所属行业（字典 crm_customer_industry）',
    `level`                int                   DEFAULT NULL COMMENT '客户等级（字典 crm_customer_level）',
    `source`               int                   DEFAULT NULL COMMENT '客户来源（字典 crm_customer_source）',
    `remark`               varchar(500)          DEFAULT NULL COMMENT '备注',
    `creator`              varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`              varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`              bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`            bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_user_id`),
    KEY `idx_tenant` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='CRM 客户';

-- 2. 跟进记录表（依据 CrmFollowUpRecordDO；bizType：2=客户）
CREATE TABLE IF NOT EXISTS `crm_follow_up_record` (
    `id`           bigint        NOT NULL AUTO_INCREMENT COMMENT '编号',
    `biz_type`     tinyint       NOT NULL DEFAULT 2 COMMENT '业务类型（2=客户）',
    `biz_id`       bigint        NOT NULL COMMENT '业务编号（客户编号）',
    `type`         tinyint       DEFAULT NULL COMMENT '跟进方式（字典 crm_follow_up_type）',
    `content`      varchar(2000) NOT NULL COMMENT '跟进内容',
    `next_time`    datetime      DEFAULT NULL COMMENT '下次联系时间',
    `pic_urls`     varchar(2048)          DEFAULT '[]' COMMENT '图片（JSON 数组）',
    `file_urls`    varchar(2048)          DEFAULT '[]' COMMENT '附件（JSON 数组）',
    `business_ids` varchar(512)           DEFAULT NULL COMMENT '关联商机（JSON 数组）',
    `contact_ids`  varchar(512)           DEFAULT NULL COMMENT '关联联系人（JSON 数组）',
    `creator`      varchar(64)            DEFAULT '' COMMENT '创建者',
    `create_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`      varchar(64)            DEFAULT '' COMMENT '更新者',
    `update_time`  datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      bit(1)        NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`    bigint        NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='CRM 跟进记录';

-- 3. 演示客户（租户 1，负责人 admin=1）
INSERT IGNORE INTO `crm_customer` (`id`, `name`, `follow_up_status`, `contact_last_time`, `contact_last_content`, `contact_next_time`, `owner_user_id`, `deal_status`, `mobile`, `email`, `area_id`, `detail_address`, `industry_id`, `level`, `source`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (1, '华信科技股份有限公司', b'1', NOW() - INTERVAL 7 DAY,  '电话回访产品使用情况，客户反馈整体满意，希望增加报表功能', NOW() + INTERVAL 7 DAY,  1, b'1', '13800000001', 'contact@huaxin.com',   110100, '北京市海淀区中关村软件园 3 号楼',  2, 1, 1, '年度框架协议客户，重点关注报表与集成能力', '1', NOW() - INTERVAL 90 DAY, 1),
       (2, '恒利商贸有限公司',     b'1', NOW() - INTERVAL 15 DAY, '拜访采购部，谈年度续约意向，价格敏感',                     NOW() + INTERVAL 3 DAY,  1, b'0', '13800000002', 'hr@hengli.com',        310100, '上海市浦东新区张江高科 5 号楼',    1, 2, 3, '老客户转介绍，续约关键期',                 '1', NOW() - INTERVAL 60 DAY, 1),
       (3, '启明星教育集团',       b'0', NULL, NULL, NULL, 1, b'0', '13800000003', 'bd@qmx.com',           440100, '广州市天河区智慧城 8 栋',          4, 2, 2, '初次接触，需求待挖掘',                     '1', NOW() - INTERVAL 30 DAY, 1),
       (4, '中科精密制造有限公司', b'1', NOW() - INTERVAL 20 DAY, '现场调研生产线数据采集需求，客户预算充足',                 NOW() + INTERVAL 10 DAY, 1, b'0', '13800000004', 'it@zkjm.com',          320500, '苏州市工业园区 12 号厂房',         3, 1, 4, '展会获取线索，MES 对接需求明确',           '1', NOW() - INTERVAL 45 DAY, 1),
       (5, '云帆互联网服务有限公司', b'0', NULL, NULL, NULL, 1, b'1', '13800000005', 'hello@yunfan.io',      330100, '杭州市余杭区梦想小镇 2 幢',        2, 3, 1, '小微型客户，已成交轻量版',                 '1', NOW() - INTERVAL 120 DAY, 1);

-- 4. 演示跟进记录
INSERT IGNORE INTO `crm_follow_up_record` (`id`, `biz_type`, `biz_id`, `type`, `content`, `next_time`, `creator`, `create_time`, `tenant_id`)
VALUES (1, 2, 1, 1, '电话回访：客户对现有功能满意，提出报表定制需求，需要产品部评估排期。', NOW() + INTERVAL 7 DAY, '1', NOW() - INTERVAL 7 DAY, 1),
       (2, 2, 1, 4, '现场会议：演示了新版本数据看板，客户技术团队确认 API 对接方案可行。', NOW() + INTERVAL 14 DAY, '1', NOW() - INTERVAL 20 DAY, 1),
       (3, 2, 2, 3, '上门拜访：与采购总监沟通续约，客户希望在新合同中增加培训服务。', NOW() + INTERVAL 3 DAY, '1', NOW() - INTERVAL 15 DAY, 1),
       (4, 2, 4, 3, '现场调研：确认数据采集范围覆盖 12 条产线，客户预算约 80 万。', NOW() + INTERVAL 10 DAY, '1', NOW() - INTERVAL 20 DAY, 1);

-- 5. 清理调试期间可能误插的重复字典（90001+ 段为本仓库早期调试使用）
DELETE FROM `system_dict_data` WHERE `id` BETWEEN 90001 AND 99999 AND `dict_type` LIKE 'crm_%';
DELETE FROM `system_dict_type` WHERE `id` BETWEEN 9001 AND 9999 AND `type` LIKE 'crm_%';

-- 6. CRM 数据权限表（依据 CrmPermissionDO；CRM 所有查询经此表做可见性过滤）
CREATE TABLE IF NOT EXISTS `crm_permission` (
    `id`               bigint  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `biz_type`         tinyint NOT NULL COMMENT '业务类型（2=客户）',
    `biz_id`           bigint  NOT NULL COMMENT '业务编号',
    `user_id`          bigint  NOT NULL COMMENT '成员用户编号',
    `level` tinyint NOT NULL DEFAULT 2 COMMENT '权限级别（1=负责人 2=只读 3=读写）',
    `creator`          varchar(64)      DEFAULT '' COMMENT '创建者',
    `create_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`          varchar(64)      DEFAULT '' COMMENT '更新者',
    `update_time`      datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          bit(1)   NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`        bigint   NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_biz_user` (`biz_type`, `biz_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='CRM 成员权限';

-- 7. 演示客户的权限数据（admin=1 为负责人）
INSERT IGNORE INTO `crm_permission` (`id`, `biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
VALUES (1, 2, 1, 1, 1, '1', NOW(), 1),
       (2, 2, 2, 1, 1, '1', NOW(), 1),
       (3, 2, 3, 1, 1, '1', NOW(), 1),
       (4, 2, 4, 1, 1, '1', NOW(), 1),
       (5, 2, 5, 1, 1, '1', NOW(), 1);

-- 8. 客户公海配置表（依据 CrmCustomerPoolConfigDO；客户分页查询会读取）
CREATE TABLE IF NOT EXISTS `crm_customer_pool_config` (
    `id`                  bigint   NOT NULL AUTO_INCREMENT COMMENT '编号',
    `enabled`             bit(1)   NOT NULL DEFAULT b'0' COMMENT '是否启用',
    `contact_expire_days` int      DEFAULT NULL COMMENT '不跟进天数',
    `deal_expire_days`    int      DEFAULT NULL COMMENT '不成交天数',
    `notify_enabled`      bit(1)   NOT NULL DEFAULT b'0' COMMENT '是否开启提前通知',
    `notify_days`         int      DEFAULT NULL COMMENT '提前通知天数',
    `creator`             varchar(64)      DEFAULT '' COMMENT '创建者',
    `create_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`             varchar(64)      DEFAULT '' COMMENT '更新者',
    `update_time`         datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`             bit(1)   NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`           bigint   NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='CRM 客户公海配置';
