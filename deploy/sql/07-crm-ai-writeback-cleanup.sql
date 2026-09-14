-- =============================================================
-- 07-crm-ai-writeback-cleanup.sql
-- 清理「意图即服务」演示过程中 AI 写回 CRM 的跟进记录
--
-- 写回纪律：所有 AI 写入的跟进记录都以 【AI 开头（【AI 方案】/【AI 纪要】/【AI 催收计划】），
-- 因此一条 SQL 即可清干净，不会误伤真人录入的跟进记录。
--
-- 用法（演示结束后执行）：
--   mysql --host=127.0.0.1 --port=33061 --user=root --password=123456 --database=ruoyi-office < 07-crm-ai-writeback-cleanup.sql
-- =============================================================

-- ① 先看一眼将要清理的记录（建议先执行这段确认范围）
SELECT id, biz_type, biz_id, type, create_time, LEFT(content, 40) AS content_preview
FROM crm_follow_up_record
WHERE deleted = 0 AND content LIKE '【AI%'
ORDER BY id DESC;

-- ② 逻辑删除（保留审计痕迹，页面不再展示）
UPDATE crm_follow_up_record
SET deleted = 1
WHERE deleted = 0 AND content LIKE '【AI%';

-- ③ 如需彻底删除（连留痕一起清掉），再执行下面这句
-- DELETE FROM crm_follow_up_record WHERE content LIKE '【AI%';
