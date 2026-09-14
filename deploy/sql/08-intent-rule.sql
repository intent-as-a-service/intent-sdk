-- =============================================================
-- 08-intent-rule.sql
-- 自动跟进规则（自然语言 → 规则 YAML → 试算预览 → 启用）
--
-- 规则与 classpath intent-rules/*.yaml 同构（查询 + 条件 + 输出模板 + 参数映射），
-- 区别只是存在 DB：运营在后台用自然语言生成、试算确认后启用，即时生效。
-- 停用/删除规则只影响本表数据，不动代码、不发版。
--
-- 用法：
--   mysql --host=127.0.0.1 --port=33061 --user=root --password=123456 --database=ruoyi-office < 08-intent-rule.sql
-- =============================================================

CREATE TABLE IF NOT EXISTS `intent_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '编号',
  `rule_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则标识（YAML 里的 id）',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '状态：0=草稿 1=已启用',
  `yaml` mediumtext COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则定义（YAML）',
  `source_text` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成规则时的自然语言原话',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `creator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_rule_key` (`rule_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动跟进规则';

-- ---------------------------------------------------------------
-- 菜单：意图中心 → 自动跟进规则（挂 intent/rule/index + 两个权限点）
-- 用 INSERT ... SELECT 保证重复执行不报错（不存在才插）
-- ---------------------------------------------------------------
INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                           status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9121, '自动跟进规则', '', 2, 3, 9100, 'rule', 'ep:magic-stick', 'intent/rule/index', 'IntentRule',
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE id = 9121);

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                           status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9122, '规则查询', 'intent:rule:query', 3, 1, 9121, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE id = 9122);

INSERT INTO `system_menu` (id, name, permission, type, sort, parent_id, path, icon, component, component_name,
                           status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 9123, '规则维护', 'intent:rule:update', 3, 2, 9121, '', '', '', NULL,
       0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `system_menu` WHERE id = 9123);

-- ---------------------------------------------------------------
-- 清理 AI 标签写回（crm.customer.tag-suggest 写进客户备注的哨兵行）
-- 演示结束后执行：整行抹掉，真人备注原样保留
-- ---------------------------------------------------------------
-- UPDATE crm_customer
-- SET remark = TRIM(REPLACE(remark, CONCAT('【AI标签】', SUBSTRING_INDEX(SUBSTRING_INDEX(remark, '【AI标签】', -1), '\n', 1)), ''))
-- WHERE deleted = 0 AND remark LIKE '%【AI标签】%';
