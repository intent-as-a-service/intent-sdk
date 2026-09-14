-- =============================================================
-- 10-intent-alias-gaps.sql
-- 口语别名补齐 + 客户域意图挂到"客户详情页"
--
-- 背景：IntentSpec 的 aliases / pages 定义在 classpath YAML 里，启动时"已存在则跳过"，
-- 所以只改 YAML 对**已装库**无效。本脚本把当前环境已生效的改动补成可重复执行的 SQL，
-- 便于其它环境（演示机 / 客户现场）一键对齐。
--
-- 口径：
--   aliases = 用户口语说法（前端"说出来想做什么"匹配框用它们命中意图）
--   pages   = 意图挂载点；crm/customer/* 表示客户详情等子页面
--   本脚本是**整段替换**而不是追加，重复执行结果一致（幂等）。
--
-- 用法：
--   mysql --host=127.0.0.1 --port=33061 --user=root --password=123456 --database=ruoyi-office < 10-intent-alias-gaps.sql
--   执行后在后台「意图中心 → 意图管理」点一下刷新，或等 30 秒目录缓存过期即可看到新说法。
-- =============================================================

UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('这个客户最近怎么样', '看看这个客户', '客户情况怎么样', '客户最近动态', '查看客户详情'))
 WHERE intent_id = 'crm.customer.analyze';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('帮我做个跟进方案', '这个客户怎么跟', '生成跟进计划', '下一步怎么跟进', '这个客户该跟进了', '帮我创建一条跟进任务'))
 WHERE intent_id = 'crm.customer.followup-plan';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('我的客户都是什么类型', '客户画像', '客户结构分析', '客户都从哪来', '帮我整理一下这个客户的完整画像'))
 WHERE intent_id = 'crm.stats.portrait-insight';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('这个单子卡在哪了', '商机怎么推进', '下一步怎么走', '单子推不动', '帮我推荐下一个动作', '推荐下一个动作'))
 WHERE intent_id = 'crm.business.advance-strategy';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('该催款了怎么催', '回款催收计划', '催款话术', '帮我要钱', '帮我提醒一下这个客户该回款了', '这客户该回款了'))
 WHERE intent_id = 'crm.receivable.collection-plan';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('竞品怎么打', '对手抢单怎么办', '打竞品的话术', '竞对应对', '对手在抢我的单子'))
 WHERE intent_id = 'crm.business.competitor-play';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('帮我写个话术', '怎么跟这个联系人聊', '开场白怎么说', '客户异议怎么回', '帮我写个跟进话术'))
 WHERE intent_id = 'crm.contact.talk-track';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.aliases', JSON_ARRAY('这个月能成多少', '业绩预测', '能完成目标吗', '这个月业绩怎么样', '团队这个月业绩怎么样'))
 WHERE intent_id = 'crm.stats.performance-forecast';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.pages', JSON_ARRAY('crm/customer', 'crm/customer/*'))
 WHERE intent_id = 'crm.customer.analyze';
UPDATE `intent_spec` SET spec_json = JSON_SET(spec_json, '$.pages', JSON_ARRAY('crm/customer', 'crm/customer/*'))
 WHERE intent_id = 'crm.customer.followup-plan';

-- 复核：下列查询应返回 8 行，aliases 里能看到新补的口语说法
SELECT intent_id,
       JSON_LENGTH(JSON_EXTRACT(spec_json, '$.aliases')) AS alias_count,
       JSON_EXTRACT(spec_json, '$.pages')               AS pages
FROM `intent_spec`
WHERE intent_id IN ('crm.customer.analyze', 'crm.customer.followup-plan', 'crm.stats.portrait-insight', 'crm.business.advance-strategy', 'crm.receivable.collection-plan', 'crm.business.competitor-play', 'crm.contact.talk-track', 'crm.stats.performance-forecast')
ORDER BY intent_id;
