-- =============================================================
-- 09-intent-role-layering.sql
-- 角色分层：把"管理者意图"从销售视角里收起来
--
-- 口径：intent_config.roles 覆盖 IntentSpec.policy.roles（未配置的意图沿用 YAML 里的策略）。
-- 管理者意图 = 看全团队数据的统计与分配类；销售意图 = 自己客户/商机/合同/回款的作业类（保持 ["*"]）。
--
-- 验证方式：
--   管理员（super_admin）目录里能看到下列意图；
--   普通销售（common）目录里看不到，直接调接口执行返回 FORBIDDEN。
--
-- 用法：
--   mysql --host=127.0.0.1 --port=33061 --user=root --password=123456 --database=ruoyi-office < 09-intent-role-layering.sql
-- =============================================================

-- 管理者意图：团队统计 / 辅导 / 资源分配
SET @manager_roles = '["crm_admin","super_admin"]';

INSERT INTO `intent_config` (intent_id, enabled, roles, remark, creator, updater)
SELECT t.intent_id, b'1', @manager_roles, '角色分层：管理者意图', '1', '1'
FROM (
  SELECT 'crm.stats.funnel-diagnosis' AS intent_id UNION ALL
  SELECT 'crm.stats.performance-forecast' UNION ALL
  SELECT 'crm.stats.rank-coaching' UNION ALL
  SELECT 'crm.stats.portrait-insight' UNION ALL
  SELECT 'crm.team.coaching-alert' UNION ALL
  SELECT 'crm.team.workload-balance'
) t
WHERE NOT EXISTS (SELECT 1 FROM `intent_config` c WHERE c.intent_id = t.intent_id);

UPDATE `intent_config`
SET roles = @manager_roles, remark = '角色分层：管理者意图', update_time = NOW()
WHERE intent_id IN ('crm.stats.funnel-diagnosis', 'crm.stats.performance-forecast',
                    'crm.stats.rank-coaching', 'crm.stats.portrait-insight',
                    'crm.team.coaching-alert', 'crm.team.workload-balance');

-- ---------------------------------------------------------------
-- 销售作业类意图：客户/商机/合同/回款这些"我自己手上"的活，不做角色收窄
-- （历史上有两条被收窄过，这里一并归位，避免出现"销售看不到自己客户分析"的怪状态）
-- ---------------------------------------------------------------
UPDATE `intent_config`
SET roles = '["*"]', remark = '角色分层：销售作业意图（不限制）', update_time = NOW()
WHERE intent_id IN ('crm.customer.analyze', 'crm.contract.risk-review');
