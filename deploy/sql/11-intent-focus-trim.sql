-- =============================================================
-- 11-intent-focus-trim.sql
-- 减法：意图目录聚焦精简——40 条里保留 22 条上架，其余 17 条下架（不删除）
--
-- 口径（下架只影响「上架状态」，规范本体一直在）：
--   1) 目录接口 /intent/catalog 不再返回该意图，AI 面板里看不到；
--   2) 直接调 /intent/execute 返回 FORBIDDEN「该意图已下架」；
--   3) 待办与推荐（intent-rules）只从上架意图里挑选，下架意图不再产生待办。
-- 一键回滚：把 intent_config.enabled 改回 b'1' 即恢复，无需发版、无需重新导入规范。
--
-- 幂等：intent_config.intent_id 上没有唯一索引（idx_intent 是非唯一索引），
--       所以用 INSERT ... WHERE NOT EXISTS + UPDATE 两步，可重复执行；
--       UPDATE 只动 enabled / remark，不覆盖 09 号脚本设置的角色（roles）。
--       脚本按租户 1 写入（与宿主 admin 一致），换租户请替换 @tenant_id。
--
-- 用法：
--   mysql --host=127.0.0.1 --port=33061 --user=root --password=123456 \
--         --database=ruoyi-office < 11-intent-focus-trim.sql
-- =============================================================

SET @tenant_id = 1;

-- -------------------------------------------------------------
-- 1. 上架 22 条：显式写 enabled=1，避免历史环境里被误关后没人发现
-- -------------------------------------------------------------
INSERT INTO `intent_config` (intent_id, enabled, roles, remark, creator, updater, tenant_id)
SELECT t.intent_id, t.enabled, t.roles, t.remark, '1', '1', @tenant_id FROM (
  SELECT 'crm.backlog.today-priority' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.business.advance-strategy' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.business.quote-strategy' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.business.win-analysis' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '商机赢单分析' AS remark UNION ALL
  SELECT 'crm.clue.follow-up-suggestion' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '线索跟进建议' AS remark UNION ALL
  SELECT 'crm.clue.quality-score' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.contact.meeting-minutes' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.contact.talk-track' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.contract.audit-precheck' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.contract.renewal-plan' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.contract.risk-review' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '角色分层：销售作业意图（不限制）' AS remark UNION ALL
  SELECT 'crm.contract.term-explain' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.customer.analyze' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：通用经营分析入口 + 执行器演示资产（sales-analyst-flow）' AS remark UNION ALL
  SELECT 'crm.customer.followup-plan' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '所有人可用' AS remark UNION ALL
  SELECT 'crm.customer.health-check' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.customer.visit-prep' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.product.bundle-recommend' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.receivable.collection-plan' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '回款催收计划' AS remark UNION ALL
  SELECT 'crm.receivable.overdue-warning' AS intent_id, b'1' AS enabled, '["*"]' AS roles, '上架：聚焦精简（2026-09-14）· 保留为高价值主线意图' AS remark UNION ALL
  SELECT 'crm.stats.funnel-diagnosis' AS intent_id, b'1' AS enabled, '["crm_admin","super_admin"]' AS roles, '角色分层：管理者意图' AS remark UNION ALL
  SELECT 'crm.stats.performance-forecast' AS intent_id, b'1' AS enabled, '["crm_admin","super_admin"]' AS roles, '角色分层：管理者意图' AS remark UNION ALL
  SELECT 'crm.team.coaching-alert' AS intent_id, b'1' AS enabled, '["crm_admin","super_admin"]' AS roles, '角色分层：管理者意图' AS remark
) t
WHERE NOT EXISTS (SELECT 1 FROM `intent_config` c
                  WHERE c.intent_id = t.intent_id AND c.deleted = b'0' AND c.tenant_id = @tenant_id);

UPDATE `intent_config` SET enabled = b'1', updater = '1', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = @tenant_id AND intent_id IN (
  'crm.backlog.today-priority', 'crm.business.advance-strategy', 'crm.business.quote-strategy', 'crm.business.win-analysis', 'crm.clue.follow-up-suggestion', 'crm.clue.quality-score', 'crm.contact.meeting-minutes', 'crm.contact.talk-track', 'crm.contract.audit-precheck', 'crm.contract.renewal-plan', 'crm.contract.risk-review', 'crm.contract.term-explain', 'crm.customer.analyze', 'crm.customer.followup-plan', 'crm.customer.health-check', 'crm.customer.visit-prep', 'crm.product.bundle-recommend', 'crm.receivable.collection-plan', 'crm.receivable.overdue-warning', 'crm.stats.funnel-diagnosis', 'crm.stats.performance-forecast', 'crm.team.coaching-alert');

-- -------------------------------------------------------------
-- 2. 下架 17 条：enabled=0，目录不展示、直调被拒；规范本体保留
--    （remark 写明各自的取舍理由，便于日后复核与回滚决策）
-- -------------------------------------------------------------
INSERT INTO `intent_config` (intent_id, enabled, roles, remark, creator, updater, tenant_id)
SELECT t.intent_id, t.enabled, t.roles, t.remark, '1', '1', @tenant_id FROM (
  SELECT 'crm.backlog.weekly-review' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 周会场景，与今日优先级同源' AS remark UNION ALL
  SELECT 'crm.business.competitor-play' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 需要手输竞品名，演示链路长' AS remark UNION ALL
  SELECT 'crm.business.risk-check' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 与商机推进策略重叠（原方案即标注可并入）' AS remark UNION ALL
  SELECT 'crm.clue.nurture-plan' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 长周期培育，演示价值低于评分与跟进建议' AS remark UNION ALL
  SELECT 'crm.contact.decision-role' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 与沟通话术生成的信息面重叠' AS remark UNION ALL
  SELECT 'crm.contract.fulfillment-track' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 依赖交付数据，宿主当前数据不足' AS remark UNION ALL
  SELECT 'crm.customer.churn-warning' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 与客户健康度诊断重叠（都在回答这个客户健康吗）' AS remark UNION ALL
  SELECT 'crm.customer.duplicate-check' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 建档期工具，演示主线不涉及' AS remark UNION ALL
  SELECT 'crm.customer.loss-analysis' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 面向已丢单，客户页叙事是在跟客户' AS remark UNION ALL
  SELECT 'crm.customer.tag-suggest' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 运营动作，不属于销售作业主线' AS remark UNION ALL
  SELECT 'crm.customer.timeline' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 与 360° 拜访准备的信息面重叠' AS remark UNION ALL
  SELECT 'crm.product.value-pitch' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 单产品卖点，场景窄' AS remark UNION ALL
  SELECT 'crm.receivable.payment-habit' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 历史回款样本偏少，结论不稳' AS remark UNION ALL
  SELECT 'crm.receivable.reconcile' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 偏财务对账，销售场景弱' AS remark UNION ALL
  SELECT 'crm.stats.portrait-insight' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 结论偏软，硬数据少' AS remark UNION ALL
  SELECT 'crm.stats.rank-coaching' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 结论偏软，硬数据少' AS remark UNION ALL
  SELECT 'crm.team.workload-balance' AS intent_id, b'0' AS enabled, '["*"]' AS roles, '下架：聚焦精简（2026-09-14）· 全局意图（每页都出现），且输出为纯文本建议' AS remark
) t
WHERE NOT EXISTS (SELECT 1 FROM `intent_config` c
                  WHERE c.intent_id = t.intent_id AND c.deleted = b'0' AND c.tenant_id = @tenant_id);

UPDATE `intent_config` SET enabled = b'0', updater = '1', update_time = NOW()
WHERE deleted = b'0' AND tenant_id = @tenant_id AND intent_id IN (
  'crm.backlog.weekly-review', 'crm.business.competitor-play', 'crm.business.risk-check', 'crm.clue.nurture-plan', 'crm.contact.decision-role', 'crm.contract.fulfillment-track', 'crm.customer.churn-warning', 'crm.customer.duplicate-check', 'crm.customer.loss-analysis', 'crm.customer.tag-suggest', 'crm.customer.timeline', 'crm.product.value-pitch', 'crm.receivable.payment-habit', 'crm.receivable.reconcile', 'crm.stats.portrait-insight', 'crm.stats.rank-coaching', 'crm.team.workload-balance');

-- 下架理由逐条落库（复核与复盘时看这里，不用翻文档）
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 周会场景，与今日优先级同源' WHERE intent_id = 'crm.backlog.weekly-review' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 需要手输竞品名，演示链路长' WHERE intent_id = 'crm.business.competitor-play' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 与商机推进策略重叠（原方案即标注可并入）' WHERE intent_id = 'crm.business.risk-check' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 长周期培育，演示价值低于评分与跟进建议' WHERE intent_id = 'crm.clue.nurture-plan' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 与沟通话术生成的信息面重叠' WHERE intent_id = 'crm.contact.decision-role' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 依赖交付数据，宿主当前数据不足' WHERE intent_id = 'crm.contract.fulfillment-track' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 与客户健康度诊断重叠（都在回答这个客户健康吗）' WHERE intent_id = 'crm.customer.churn-warning' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 建档期工具，演示主线不涉及' WHERE intent_id = 'crm.customer.duplicate-check' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 面向已丢单，客户页叙事是在跟客户' WHERE intent_id = 'crm.customer.loss-analysis' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 运营动作，不属于销售作业主线' WHERE intent_id = 'crm.customer.tag-suggest' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 与 360° 拜访准备的信息面重叠' WHERE intent_id = 'crm.customer.timeline' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 单产品卖点，场景窄' WHERE intent_id = 'crm.product.value-pitch' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 历史回款样本偏少，结论不稳' WHERE intent_id = 'crm.receivable.payment-habit' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 偏财务对账，销售场景弱' WHERE intent_id = 'crm.receivable.reconcile' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 结论偏软，硬数据少' WHERE intent_id = 'crm.stats.portrait-insight' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 结论偏软，硬数据少' WHERE intent_id = 'crm.stats.rank-coaching' AND tenant_id = @tenant_id;
UPDATE `intent_config` SET remark = '下架：聚焦精简（2026-09-14）· 全局意图（每页都出现），且输出为纯文本建议' WHERE intent_id = 'crm.team.workload-balance' AND tenant_id = @tenant_id;

-- -------------------------------------------------------------
-- 3. 附带归位：crm.customer.analyze 的执行器 sales-analyst-flow
--    该意图是「同一个意图换个执行器」的演示资产（确定性流程编排：约 0.1s / 0 token，
--    对比默认 Agent 执行器的多轮工具调用）。执行器档案走 DB 存储（见 05-intent-executor.sql），
--    若曾被重建覆盖，用下面两段恢复；已存在则原样跳过。
-- -------------------------------------------------------------
INSERT INTO `intent_executor` (executor_id, profile_json, source, remark, creator, updater, tenant_id)
SELECT 'sales-analyst-flow', '{"id":"sales-analyst-flow","type":"agent","name":"销售分析师（流程编排）","description":"自动读取客户档案，生成结构化经营分析报告（确定性流程，结果稳定可审计）","model":{"baseUrl":"https://api.deepseek.com","api":"openai-completions","provider":"deepseek","modelId":"deepseek-chat","modelName":null,"apiKey":null,"contextWindow":128000,"maxTokens":8192},"tools":[],"knowledge":[],"skills":[],"memory":null,"limits":{"maxTurns":12,"outputMaxRetries":1},"steps":[],"output":{"blocks":"[{\\\\"kind\\\\":\\\\"kv\\\\",\\\\"title\\\\":\\\\"客户档案速览\\\\",\\\\"items\\\\":[{\\\\"label\\\\":\\\\"客户名称\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.name}\\\\"},{\\\\"label\\\\":\\\\"所属行业\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.industry}\\\\"},{\\\\"label\\\\":\\\\"客户等级\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.level}\\\\"},{\\\\"label\\\\":\\\\"来源渠道\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.source}\\\\"},{\\\\"label\\\\":\\\\"成交状态\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.dealStatus}\\\\"},{\\\\"label\\\\":\\\\"联系电话\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.mobile}\\\\"},{\\\\"label\\\\":\\\\"办公地址\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.address}\\\\"},{\\\\"label\\\\":\\\\"最近联系\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.contactLastContent}\\\\"},{\\\\"label\\\\":\\\\"下次联系\\\\",\\\\"value\\\\":\\\\"${nodes.n1.details.customer.contactNextTime}\\\\"}]}]","title":"客户经营分析报告","summary":"客户「${nodes.n1.details.customer.name}」（${nodes.n1.details.customer.level}）当前成交状态：${nodes.n1.details.customer.dealStatus}。上次联系：${nodes.n1.details.customer.contactLastContent}；已排期下次联系：${nodes.n1.details.customer.contactNextTime}。"},"flow":[{"id":"n1","type":"tool","tool":"crm_get_customer","args":{"customerId":"${params.customerId}"},"prompt":null,"kb":null,"query":null,"topK":null,"when":null,"stopWhen":null}]}', 'custom', '确定性流程：客户经营分析报告', '1', '1', @tenant_id
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `intent_executor` e
                  WHERE e.executor_id = 'sales-analyst-flow' AND e.deleted = b'0');

UPDATE `intent_spec`
SET spec_json = JSON_SET(spec_json, '$.executor', 'sales-analyst-flow'), update_time = NOW()
WHERE intent_id = 'crm.customer.analyze'
  AND JSON_EXTRACT(spec_json, '$.executor') IS NULL;

-- -------------------------------------------------------------
-- 4. 复核：上架应为 23 条（22 条 CRM 域 + 1 条跨系统示例 scm.stock.check）
-- -------------------------------------------------------------
SELECT enabled, COUNT(*) AS cnt FROM `intent_config`
WHERE intent_id LIKE 'crm.%' AND deleted = b'0' GROUP BY enabled;

SELECT intent_id, enabled, roles, remark FROM `intent_config`
WHERE intent_id LIKE 'crm.%' AND deleted = b'0' ORDER BY enabled DESC, intent_id;

SELECT intent_id, JSON_UNQUOTE(JSON_EXTRACT(spec_json, '$.executor')) AS executor
FROM `intent_spec` WHERE intent_id = 'crm.customer.analyze';
