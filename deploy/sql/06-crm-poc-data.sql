-- =============================================================
-- 内嵌 AI 意图 SDK · CRM POC 演示数据补丁（P0/P1 意图配套）
--
-- 设计约束：
--   1) 全部日期相对 NOW() 生成 —— 可重复执行，任何时候跑都是"新鲜"的
--      （演示前重跑本脚本即可，续约合同/回款计划/联系节奏自动回到演示状态）
--   2) 金额链路闭环：商机 = 合同 = 回款计划合计，客户现场可交叉核对
--   3) 演示数据统一归口 admin（owner_user_id=1），徽标数字与「我负责的」列表口径一致
--   4) 幂等：主键行用 ON DUPLICATE KEY UPDATE，权限行先删后插
--
-- 依赖：dump 完整库 + 03-crm-demo-chain.sql
-- =============================================================

-- -------------------------------------------------------------
-- 0. 数据修复：合同 380~389 的审批状态是非法值 2（枚举只有 0/10/20/30/40）
--    统一改为 20（CrmAuditStatusEnum.APPROVE），否则待办口径取不到这些合同
-- -------------------------------------------------------------
UPDATE `crm_contract` SET `audit_status` = 20, `update_time` = NOW()
WHERE `id` BETWEEN 380 AND 389 AND `audit_status` = 2;

-- -------------------------------------------------------------
-- 0.1 定向清理：yudao 自带的测试垃圾行会污染意图口径（现场演示会看到"阿巴""哈哈哈"）
--     仅逻辑删除下列明确无业务含义的记录，且都满足"金额为 0/极小 + 命名无意义 + 2024 年"
--     范围严格限定，不触碰任何演示链路数据（客户/商机/合同 320/360/380 起）
-- -------------------------------------------------------------
-- 待审核合同里的无意义记录（金额 0）
UPDATE `crm_contract` SET `deleted` = 1, `update_time` = NOW() WHERE `id` = 6 AND `deleted` = 0;
-- 2024 年的僵尸回款计划（1 元 / 220 元，早于演示链路）
UPDATE `crm_receivable_plan` SET `deleted` = 1, `update_time` = NOW() WHERE `id` IN (7, 8) AND `deleted` = 0;
-- 测试命名的商机（haoxx / 哈罗 / 摩西摩西 / 哈哈哈 / 新的线索）
UPDATE `crm_business` SET `deleted` = 1, `update_time` = NOW() WHERE `id` IN (5, 6, 7, 13, 14) AND `deleted` = 0;

-- -------------------------------------------------------------
-- 1. 消除重名：id=12 与 id=320 都叫「宏图建筑设计院有限公司」
--    （320 是演示主角链路，保留原名；12 改名）
-- -------------------------------------------------------------
UPDATE `crm_customer` SET `name` = '宏图建筑设计院（历史归档）'
WHERE `id` = 12 AND `name` = '宏图建筑设计院有限公司';

-- -------------------------------------------------------------
-- 2. 演示主角回归 admin：客户 320 → 商机 360 → 合同 384/390 → 回款 400
--    保证「我负责的」口径、待办徽标、意图入口三处一致
-- -------------------------------------------------------------
UPDATE `crm_customer` SET `owner_user_id` = 1, `owner_time` = NOW() - INTERVAL 180 DAY,
       `level` = 1, `deal_status` = 1,
       `follow_up_status` = b'0',
       `contact_last_time` = NOW() - INTERVAL 21 DAY,
       `contact_last_content` = '上次沟通：智慧工地一期验收通过，客户信息中心提出移动端巡检与数据看板扩展需求，约定本月内提交二期方案。',
       `contact_next_time` = NOW() - INTERVAL 4 DAY,
       `remark` = 'A 级客户 · 建筑设计行业龙头 · 一期已部署（智慧工地），二期需求明确'
WHERE `id` = 320;

UPDATE `crm_business` SET `owner_user_id` = 1, `status_type_id` = 6, `status_id` = 11,
       `create_time` = NOW() - INTERVAL 95 DAY, `update_time` = NOW() - INTERVAL 35 DAY
WHERE `id` = 360;   -- 宏图设计院·智慧工地管理系统：停在「方案制定」35 天

UPDATE `crm_contract` SET `owner_user_id` = 1, `update_time` = NOW() - INTERVAL 35 DAY
WHERE `id` IN (384, 390);

UPDATE `crm_receivable` SET `owner_user_id` = 1 WHERE `id` = 400;

-- -------------------------------------------------------------
-- 3. 超期未联系客户（支撑「客户超期未联系」规则 + 健康度诊断）
-- -------------------------------------------------------------
UPDATE `crm_customer` SET `owner_user_id` = 1, `owner_time` = COALESCE(`owner_time`, NOW() - INTERVAL 120 DAY),
       `follow_up_status` = b'0',
       `contact_last_time` = NOW() - INTERVAL 18 DAY,
       `contact_last_content` = '电话沟通：门店巡检小程序试用反馈良好，待确认采购预算与上线门店批次。',
       `contact_next_time` = NOW() - INTERVAL 6 DAY
WHERE `id` = 321;

UPDATE `crm_customer` SET `owner_user_id` = 1, `owner_time` = COALESCE(`owner_time`, NOW() - INTERVAL 150 DAY),
       `follow_up_status` = b'0',
       `contact_last_time` = NOW() - INTERVAL 26 DAY,
       `contact_last_content` = '拜访记录：进销存管理需求已确认，客户内部预算审批中，等待回复。',
       `contact_next_time` = NOW() - INTERVAL 11 DAY
WHERE `id` = 327;

-- 327 名下的进销存商机（370）改由 admin 负责，保证「商机停滞」对 admin 可见
UPDATE `crm_business` SET `owner_user_id` = 1, `status_type_id` = 6, `status_id` = 10,
       `create_time` = NOW() - INTERVAL 110 DAY, `update_time` = NOW() - INTERVAL 42 DAY
WHERE `id` = 370;   -- 朗润医疗·进销存管理：停在「需求分析」42 天

-- -------------------------------------------------------------
-- 4. 待审核合同 2 份（支撑「合同审批预检」+ 待办「合同待我审批」）
--    金额与在手商机一致：395 ← 商机 371（宏图移动端扩展 24000）
--                        396 ← 商机 365（盛世传媒项目协作 90000）
-- -------------------------------------------------------------
INSERT INTO `crm_contract`
  (`id`,`name`,`no`,`customer_id`,`business_id`,`owner_user_id`,`audit_status`,`order_date`,`start_time`,`end_time`,
   `total_product_price`,`discount_percent`,`total_price`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
  (395,'宏图设计院移动端巡检扩展合同','HT-2026-0403',320,371,1,10,
   NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 5 DAY, NOW() + INTERVAL 370 DAY,
   24000.00,100.00,24000.00,'商机：宏图设计院·移动端扩展（24000 元）；含移动端授权 20 账号，验收后 7 日内付全款。',
   '1',NOW() - INTERVAL 2 DAY,'1',NOW() - INTERVAL 2 DAY,0,1),
  (396,'盛世传媒项目协作平台合同','HT-2026-0404',325,365,1,10,
   NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 3 DAY, NOW() + INTERVAL 368 DAY,
   90000.00,100.00,90000.00,'商机：盛世传媒·项目协作管理（90000 元）；驻场实施 20 人天 + 报表定制 2 项。',
   '1',NOW() - INTERVAL 1 DAY,'1',NOW() - INTERVAL 1 DAY,0,1)
ON DUPLICATE KEY UPDATE
  `audit_status` = VALUES(`audit_status`), `owner_user_id` = VALUES(`owner_user_id`),
  `order_date` = VALUES(`order_date`), `start_time` = VALUES(`start_time`), `end_time` = VALUES(`end_time`),
  `update_time` = NOW();

DELETE FROM `crm_contract_product` WHERE `contract_id` IN (395,396);
INSERT INTO `crm_contract_product`
  (`id`,`contract_id`,`product_id`,`product_price`,`contract_price`,`count`,`total_price`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
  (3950,395,209,24000.00,24000.00,1,24000.00,'1',NOW(),'1',NOW(),0,1),
  (3960,396,204,60000.00,60000.00,1,60000.00,'1',NOW(),'1',NOW(),0,1),
  (3961,396,206,15000.00,15000.00,2,30000.00,'1',NOW(),'1',NOW(),0,1);

-- -------------------------------------------------------------
-- 5. 回款计划 12 条：金额合计 = 对应合同金额（闭环）
--    其中 3 笔已逾期（admin 负责 → 触发「回款计划已到期未回款」待办）
--         2 笔 7 天内到期（演示"即将到期"的紧迫感）
-- -------------------------------------------------------------
INSERT INTO `crm_receivable_plan`
  (`id`,`period`,`customer_id`,`contract_id`,`owner_user_id`,`receivable_id`,`return_time`,`return_type`,
   `price`,`remind_days`,`remind_time`,`remark`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
  (100,1,326,380,1,NULL,NOW() - INTERVAL 18 DAY,1,155200.00,7,NOW() - INTERVAL 25 DAY,'金辉 MES 二期：签约款 50%（已逾期）','1',NOW() - INTERVAL 60 DAY,'1',NOW(),0,1),
  (101,2,326,380,1,NULL,NOW() + INTERVAL 25 DAY,1,155200.00,7,NOW() + INTERVAL 18 DAY,'金辉 MES 二期：验收款 50%','1',NOW() - INTERVAL 60 DAY,'1',NOW(),0,1),
  (102,1,326,387,1,NULL,NOW() - INTERVAL 6 DAY,1, 36000.00,7,NOW() - INTERVAL 13 DAY,'金辉电子年度维保：全年费用（已逾期）','1',NOW() - INTERVAL 30 DAY,'1',NOW(),0,1),
  (103,1,328,386,1,NULL,NOW() - INTERVAL 3 DAY,1, 71250.00,7,NOW() - INTERVAL 10 DAY,'百味食品轻量一体化：首期款 50%（已逾期）','1',NOW() - INTERVAL 25 DAY,'1',NOW(),0,1),
  (104,2,328,386,1,NULL,NOW() + INTERVAL 40 DAY,1, 71250.00,7,NOW() + INTERVAL 33 DAY,'百味食品轻量一体化：终验款 50%','1',NOW() - INTERVAL 25 DAY,'1',NOW(),0,1),
  (105,1,320,395,1,NULL,NOW() + INTERVAL 3 DAY,1, 12000.00,7,NOW() - INTERVAL 4 DAY,'宏图移动端扩展：首期款 50%（即将到期）','1',NOW() - INTERVAL 2 DAY,'1',NOW(),0,1),
  (106,2,320,395,1,NULL,NOW() + INTERVAL 90 DAY,1, 12000.00,7,NOW() + INTERVAL 83 DAY,'宏图移动端扩展：验收款 50%','1',NOW() - INTERVAL 2 DAY,'1',NOW(),0,1),
  (107,1,325,396,1,NULL,NOW() + INTERVAL 6 DAY,1, 45000.00,7,NOW() - INTERVAL 1 DAY,'盛世传媒项目协作：首期款 50%（即将到期）','1',NOW() - INTERVAL 1 DAY,'1',NOW(),0,1),
  (108,2,325,396,1,NULL,NOW() + INTERVAL 66 DAY,1, 45000.00,7,NOW() + INTERVAL 59 DAY,'盛世传媒项目协作：终验款 50%','1',NOW() - INTERVAL 1 DAY,'1',NOW(),0,1),
  (109,1,320,384,1,NULL,NOW() + INTERVAL 10 DAY,1,199500.00,7,NOW() + INTERVAL 3 DAY,'宏图智慧工地一期：签约款 50%','1',NOW() - INTERVAL 40 DAY,'1',NOW(),0,1),
  (110,2,320,384,1,NULL,NOW() + INTERVAL 100 DAY,1,199500.00,7,NOW() + INTERVAL 93 DAY,'宏图智慧工地一期：终验款 50%','1',NOW() - INTERVAL 40 DAY,'1',NOW(),0,1),
  (111,1,322,385,1,NULL,NOW() - INTERVAL 12 DAY,1,130000.00,7,NOW() - INTERVAL 19 DAY,'天诚物流 WMS 一期：首期款 50%（已逾期）','1',NOW() - INTERVAL 35 DAY,'1',NOW(),0,1)
ON DUPLICATE KEY UPDATE
  `return_time` = VALUES(`return_time`), `remind_time` = VALUES(`remind_time`),
  `receivable_id` = VALUES(`receivable_id`), `price` = VALUES(`price`),
  `owner_user_id` = VALUES(`owner_user_id`), `update_time` = NOW();

-- -------------------------------------------------------------
-- 6. 跨对象跟进记录 18 条：让线索/商机/合同/回款都有真实时间线
--    （写回类意图演示效果取决于此：客户点开任意对象都能看到 AI 与人工的记录）
-- -------------------------------------------------------------
INSERT INTO `crm_follow_up_record`
  (`id`,`biz_type`,`biz_id`,`type`,`content`,`next_time`,`creator`,`create_time`,`updater`,`update_time`,`deleted`,`tenant_id`)
VALUES
  -- 线索（biz_type=1）6 条
  (600,1,300,1,'初次电话：客户暂无明确信息化预算，关注点停留在"门店盘点效率"，先培育。',NOW() + INTERVAL 5 DAY,'1',NOW() - INTERVAL 10 DAY,'1',NOW() - INTERVAL 10 DAY,0,1),
  (601,1,302,1,'微信沟通：已发送标准版产品资料与两个物流行业案例，客户反馈价格敏感。',NOW() + INTERVAL 9 DAY,'150',NOW() - INTERVAL 6 DAY,'150',NOW() - INTERVAL 6 DAY,0,1),
  (602,1,305,2,'上门拜访：会员营销功能演示完成，客户希望补充连锁药房的会员分层案例。',NOW() + INTERVAL 12 DAY,'151',NOW() - INTERVAL 2 DAY,'151',NOW() - INTERVAL 2 DAY,0,1),
  (603,1,306,1,'电话未接通，改发短信与微信消息，未得到回复。',NOW() + INTERVAL 15 DAY,'1',NOW() - INTERVAL 8 DAY,'1',NOW() - INTERVAL 8 DAY,0,1),
  (604,1,308,2,'线上演示：项目管理模块演示 40 分钟，客户技术与业务双线参与，评价积极。',NOW() + INTERVAL 20 DAY,'150',NOW() - INTERVAL 25 DAY,'150',NOW() - INTERVAL 25 DAY,0,1),
  (605,1,310,1,'电话跟进：确认今年无采购计划，转为长期培育，约定季度性回访。',NULL,'150',NOW() - INTERVAL 30 DAY,'150',NOW() - INTERVAL 30 DAY,0,1),
  -- 商机（biz_type=4）6 条
  (606,4,360,2,'方案汇报：智慧工地二期方案（移动端巡检 + 数据看板）向客户信息中心汇报，客户要求补充与一期系统的对接方案。',NOW() + INTERVAL 3 DAY,'1',NOW() - INTERVAL 35 DAY,'1',NOW() - INTERVAL 35 DAY,0,1),
  (607,4,360,1,'电话催办：客户信息中心反馈预算已列入下半年计划，等分管院长签字。',NOW() + INTERVAL 10 DAY,'1',NOW() - INTERVAL 20 DAY,'1',NOW() - INTERVAL 20 DAY,0,1),
  (608,4,361,1,'需求澄清：天诚物流 WMS 二期范围确认（库位优化 + 与 TMS 对接），待客户确认接口清单。',NOW() + INTERVAL 6 DAY,'150',NOW() - INTERVAL 12 DAY,'150',NOW() - INTERVAL 12 DAY,0,1),
  (609,4,363,2,'方案评审：会员营销中台方案通过客户市场部评审，进入商务报价环节。',NOW() + INTERVAL 8 DAY,'151',NOW() - INTERVAL 9 DAY,'151',NOW() - INTERVAL 9 DAY,0,1),
  (610,4,365,3,'商务谈判：项目协作平台价格达成一致，客户要求增加报表定制 2 项，已确认可满足。',NOW() + INTERVAL 2 DAY,'1',NOW() - INTERVAL 5 DAY,'1',NOW() - INTERVAL 5 DAY,0,1),
  (611,4,370,1,'需求确认：进销存管理范围与预算已明确，客户内部走流程，暂缓推进。',NOW() + INTERVAL 14 DAY,'1',NOW() - INTERVAL 42 DAY,'1',NOW() - INTERVAL 42 DAY,0,1),
  -- 合同（biz_type=5）4 条
  (612,5,384,2,'合同签署：智慧工地一期合同完成双方盖章，扫描件已归档，启动实施排期。',NOW() + INTERVAL 15 DAY,'1',NOW() - INTERVAL 8 DAY,'1',NOW() - INTERVAL 8 DAY,0,1),
  (613,5,387,3,'服务启动：年度维保服务生效，已建立客户专属服务群，季度巡检排期已发送。',NOW() + INTERVAL 30 DAY,'1',NOW() - INTERVAL 20 DAY,'1',NOW() - INTERVAL 20 DAY,0,1),
  (614,5,390,1,'续约沟通：向客户提出续约方案，客户希望保持原价并增加 2 次现场支持。',NOW() + INTERVAL 5 DAY,'1',NOW() - INTERVAL 12 DAY,'1',NOW() - INTERVAL 12 DAY,0,1),
  (615,5,391,1,'续约提醒：运维合同即将到期，客户 IT 负责人确认续约意向，待走采购流程。',NOW() + INTERVAL 7 DAY,'1',NOW() - INTERVAL 6 DAY,'1',NOW() - INTERVAL 6 DAY,0,1),
  -- 回款（biz_type=7）2 条
  (616,7,400,1,'催收沟通：MES 二期签约款已逾期，客户财务反馈月底前安排付款。',NOW() + INTERVAL 4 DAY,'1',NOW() - INTERVAL 5 DAY,'1',NOW() - INTERVAL 5 DAY,0,1),
  (617,7,402,1,'回款跟进：锐锋体育项目款已确认到账流程，等待客户出纳操作。',NOW() + INTERVAL 3 DAY,'150',NOW() - INTERVAL 2 DAY,'150',NOW() - INTERVAL 2 DAY,0,1)
ON DUPLICATE KEY UPDATE
  `content` = VALUES(`content`), `next_time` = VALUES(`next_time`), `update_time` = NOW();

-- 客户/商机最近联系口径同步（详情页与意图取数一致）
UPDATE `crm_customer` SET `contact_last_content` = '续约沟通：客户认可一期交付质量，希望年度服务续约保持原价并增加 2 次现场支持。'
WHERE `id` = 320 AND `contact_last_time` IS NOT NULL;

-- -------------------------------------------------------------
-- 7. 数据权限：新记录补齐（先删后插，保证可重复执行）
-- -------------------------------------------------------------
DELETE FROM `crm_permission` WHERE (`biz_type` = 5 AND `biz_id` IN (395,396))
   OR (`biz_type` = 8 AND `biz_id` BETWEEN 100 AND 111)
   OR (`biz_type` = 1 AND `biz_id` IN (300,302,305,306,308,310))
   OR (`biz_type` = 4 AND `biz_id` IN (360,361,363,365,370))
   OR (`biz_type` = 5 AND `biz_id` IN (384,387,390,391))
   OR (`biz_type` = 2 AND `biz_id` IN (320,321,325,327))
   OR (`biz_type` = 7 AND `biz_id` IN (400,402))
   OR (`biz_type` = 6 AND `biz_id` BETWEEN 200 AND 209);

INSERT IGNORE INTO `crm_permission` (`biz_type`,`biz_id`,`user_id`,`level`,`creator`,`create_time`,`tenant_id`)
SELECT t.biz_type, t.biz_id, u.uid, CASE WHEN t.owner = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM (
  SELECT 5 AS biz_type, id AS biz_id, `owner_user_id` AS owner FROM `crm_contract` WHERE `id` IN (384,387,390,391,395,396)
  UNION ALL SELECT 8, id, `owner_user_id` FROM `crm_receivable_plan` WHERE `id` BETWEEN 100 AND 111
  UNION ALL SELECT 2, id, `owner_user_id` FROM `crm_customer` WHERE `id` IN (320,321,325,327)
  UNION ALL SELECT 4, id, `owner_user_id` FROM `crm_business` WHERE `id` IN (360,361,363,365,370)
  UNION ALL SELECT 1, id, `owner_user_id` FROM `crm_clue` WHERE `id` IN (300,302,305,306,308,310)
  UNION ALL SELECT 7, id, `owner_user_id` FROM `crm_receivable` WHERE `id` IN (400,402)
  UNION ALL SELECT 6, id, `owner_user_id` FROM `crm_product` WHERE `id` BETWEEN 200 AND 209
) t
CROSS JOIN (SELECT 1 AS uid UNION SELECT 150 UNION SELECT 151) u;

-- -------------------------------------------------------------
-- 8. 演示前自检（人工核对，与页面数字应一致）
-- -------------------------------------------------------------
-- 8.1 admin 的待办口径
SELECT '我负责的·待跟进客户' AS metric, COUNT(*) AS n FROM `crm_customer`
  WHERE `follow_up_status` = b'0' AND `owner_user_id` = 1 AND `deleted` = 0
UNION ALL SELECT '我负责的·待跟进线索', COUNT(*) FROM `crm_clue`
  WHERE `follow_up_status` = b'0' AND `transform_status` = b'0' AND `owner_user_id` = 1 AND `deleted` = 0
UNION ALL SELECT '我负责的·合同待审核', COUNT(*) FROM `crm_contract`
  WHERE `audit_status` = 10 AND `owner_user_id` = 1 AND `deleted` = 0
UNION ALL SELECT '我负责的·回款计划逾期', COUNT(*) FROM `crm_receivable_plan`
  WHERE `receivable_id` IS NULL AND `return_time` < CURDATE() AND `remind_time` < CURDATE()
    AND `owner_user_id` = 1 AND `deleted` = 0
UNION ALL SELECT '我负责的·商机停滞>30天', COUNT(*) FROM `crm_business`
  WHERE `end_status` IS NULL AND `update_time` < NOW() - INTERVAL 30 DAY AND `owner_user_id` = 1 AND `deleted` = 0;

-- 8.2 金额闭环核对（商机 vs 合同 vs 回款计划）
SELECT c.`id` AS contract_id, c.`total_price` AS contract_amount,
       (SELECT COALESCE(SUM(p.`price`),0) FROM `crm_receivable_plan` p WHERE p.`contract_id` = c.`id` AND p.`deleted` = 0) AS plan_amount
FROM `crm_contract` c WHERE c.`id` IN (380,384,386,387,395,396) AND c.`deleted` = 0;
