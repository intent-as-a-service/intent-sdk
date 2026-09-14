-- =============================================================
-- 内嵌 AI 意图 SDK · CRM 演示数据链（POC 用）
-- 线索 → 客户 → 联系人 → 商机 → 合同 → 回款 → 跟进记录
-- 每个实体 ≥10 条，时间线与金额逻辑自洽；依赖 dump 完整库
-- =============================================================

-- 0. 商机阶段补充（挂在已存在的"标准流程"type=6 下）
INSERT IGNORE INTO `crm_business_status` (`id`, `type_id`, `name`, `percent`, `sort`, `creator`, `create_time`, `tenant_id`)
VALUES (20, 6, '谈判定价', 60.000000, 4, '1', NOW(), 1),
       (21, 6, '赢单', 100.000000, 5, '1', NOW(), 1);

-- 1. 产品（10 条）
INSERT IGNORE INTO `crm_product` (`id`, `name`, `no`, `unit`, `price`, `status`, `category_id`, `description`, `owner_user_id`, `creator`, `create_time`, `tenant_id`)
VALUES (200, '企业管理平台 标准版授权', 'CP-2026-001', '套', 80000.00, 0, (SELECT MIN(id) FROM crm_product_category), '包含 OA/CRM 基础模块，10 账号内', 1, '1', NOW(), 1),
       (201, '企业管理平台 专业版授权', 'CP-2026-002', '套', 158000.00, 0, (SELECT MIN(id) FROM crm_product_category), '含 BPM 工作流与全部业务模块，50 账号', 1, '1', NOW(), 1),
       (202, '企业管理平台 旗舰版授权', 'CP-2026-003', '套', 298000.00, 0, (SELECT MIN(id) FROM crm_product_category), '全模块 + 微服务集群部署', 150, '1', NOW(), 1),
       (203, '实施服务包（远程）', 'CP-2026-004', '次', 20000.00, 0, (SELECT MIN(id) FROM crm_product_category), '远程实施 10 人天', 150, '1', NOW(), 1),
       (204, '实施服务包（驻场）', 'CP-2026-005', '次', 60000.00, 0, (SELECT MIN(id) FROM crm_product_category), '驻场实施 20 人天', 151, '1', NOW(), 1),
       (205, '年度维保服务', 'CP-2026-006', '年', 36000.00, 0, (SELECT MIN(id) FROM crm_product_category), '7×12 小时响应，季度巡检', 1, '1', NOW(), 1),
       (206, '报表定制开发', 'CP-2026-007', '项', 15000.00, 0, (SELECT MIN(id) FROM crm_product_category), '按需求定制报表与驾驶舱', 150, '1', NOW(), 1),
       (207, '数据迁移服务', 'CP-2026-008', '次', 28000.00, 0, (SELECT MIN(id) FROM crm_product_category), '老系统数据清洗与迁移', 151, '1', NOW(), 1),
       (208, '开放 API 扩展包', 'CP-2026-009', '套', 42000.00, 0, (SELECT MIN(id) FROM crm_product_category), '开放平台 + 50 个 API 点位', 1, '1', NOW(), 1),
       (209, '移动端授权（20 账号）', 'CP-2026-010', '套', 24000.00, 0, (SELECT MIN(id) FROM crm_product_category), '配套移动 App / 钉钉 / 企微', 150, '1', NOW(), 1);

-- 2. 线索（12 条：300-311；其中 301/304/307/310 已转化）
INSERT IGNORE INTO `crm_clue` (`id`, `name`, `follow_up_status`, `contact_last_time`, `contact_last_content`, `contact_next_time`, `owner_user_id`, `transform_status`, `customer_id`, `mobile`, `email`, `industry_id`, `level`, `source`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (300, '线索-蓝海连锁便利店', b'0', NOW() - INTERVAL 3 DAY,  '初次电话沟通，客户暂无明确需求', NOW() + INTERVAL 5 DAY,  1,   0, NULL, '13900000300', 'lh@bhls.com',    1, 3, 1, '待培育', '1', NOW() - INTERVAL 10 DAY, 1),
       (301, '线索-宏图建筑设计院',   b'1', NOW() - INTERVAL 20 DAY, '需求确认完毕，已转为正式客户跟进', NULL,                 150, 1, 320, '13900000301', 'ht@htsj.cn',     3, 2, 4, '已转化', '150', NOW() - INTERVAL 40 DAY, 1),
       (302, '线索-天诚物流园',       b'0', NOW() - INTERVAL 6 DAY,  ' sent 样品资料，等待反馈', NOW() + INTERVAL 9 DAY, 150, 0, NULL, '13900000302', 'tc@tcwl.com',    3, 3, 2, '价格敏感', '150', NOW() - INTERVAL 15 DAY, 1),
       (303, '线索-绿源农业科技',     b'1', NOW() - INTERVAL 25 DAY, '需求匹配度高，已转为客户', NULL,                 151, 1, 323, '13900000303', 'ly@lvyn.com',    4, 2, 3, '已转化', '151', NOW() - INTERVAL 50 DAY, 1),
       (304, '线索-金辉电子厂',       b'1', NOW() - INTERVAL 30 DAY, '已签约转化', NULL,                                  1,   1, 326, '13900000304', 'jh@jhdz.com',    3, 1, 2, '已转化', '1', NOW() - INTERVAL 70 DAY, 1),
       (305, '线索-康和连锁药房',     b'0', NOW() - INTERVAL 2 DAY,  '微信沟通中，关注会员营销功能', NOW() + INTERVAL 12 DAY, 151, 0, NULL, '13900000305', 'kh@khlyf.com', 1, 2, 3, '会员营销意向', '151', NOW() - INTERVAL 12 DAY, 1),
       (306, '线索-盛世广告传媒',     b'0', NOW() - INTERVAL 8 DAY,  '需求模糊，持续跟进', NOW() + INTERVAL 15 DAY, 1,   0, NULL, '13900000306', 'ss@sscm.com',    2, 3, 1, '培育中', '1', NOW() - INTERVAL 20 DAY, 1),
       (307, '线索-华宇置业集团',     b'1', NOW() - INTERVAL 28 DAY, '已转化，进入商机阶段', NULL,                     150, 1, 329, '13900000307', 'hy@hyzy.com',    1, 1, 4, '已转化', '150', NOW() - INTERVAL 65 DAY, 1),
       (308, '线索-四方五金加工厂',   b'0', NULL, NULL, NULL, 151, 0, NULL, '13900000308', NULL, 3, 3, 2, '新获取线索', '151', NOW() - INTERVAL 5 DAY, 1),
       (309, '线索-百味食品加工',     b'0', NOW() - INTERVAL 4 DAY,  '电话沟通一次，索要报价单', NOW() + INTERVAL 6 DAY, 1, 0, NULL, '13900000309', 'bw@bwsp.com', 3, 3, 1, '报价中', '1', NOW() - INTERVAL 9 DAY, 1),
       (310, '线索-锐锋体育用品',     b'1', NOW() - INTERVAL 35 DAY, '已转化客户，合同已签订', NULL,                    150, 1, 331, '13900000310', 'rf@tysp.com',    2, 2, 4, '已转化', '150', NOW() - INTERVAL 80 DAY, 1),
       (311, '线索-朗润医疗器械',     b'0', NOW() - INTERVAL 1 DAY,  '展会获取，待首访', NOW() + INTERVAL 4 DAY,  151, 0, NULL, '13900000311', 'lr@ylqx.com',    1, 2, 4, '展会线索', '151', NOW() - INTERVAL 2 DAY, 1);

-- 3. 客户（12 条：320-331；与线索转化关系对应）
INSERT IGNORE INTO `crm_customer` (`id`, `name`, `follow_up_status`, `contact_last_time`, `contact_last_content`, `contact_next_time`, `owner_user_id`, `owner_time`, `lock_status`, `deal_status`, `mobile`, `telephone`, `email`, `area_id`, `detail_address`, `industry_id`, `level`, `source`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (320, '宏图建筑设计院有限公司', b'1', NOW() - INTERVAL 20 DAY, '确认智慧工地管理系统需求，预算 40 万以内', NOW() + INTERVAL 8 DAY,  150, NOW() - INTERVAL 20 DAY, b'0', b'0', '13700000320', '021-66003200', 'contact@htsj.cn',   310100, '上海市浦东新区张江高科 5 号楼',   3, 2, 4, '由线索 301 转化', '150', NOW() - INTERVAL 20 DAY, 1),
       (321, '蓝海连锁便利有限公司',   b'0', NULL, NULL, NULL, 1,   NULL, b'0', b'0', '13700000321', '010-66003211', 'lh@bhls.com',       110100, '北京市朝阳区望京 SOHO T1',        1, 3, 1, '连锁零售，门店 200+', '1', NOW() - INTERVAL 10 DAY, 1),
       (322, '天诚智慧物流园区',       b'1', NOW() - INTERVAL 6 DAY,  '物流园区数字化改造需求，关注 WMS 与 TMS', NOW() + INTERVAL 9 DAY,  150, NOW() - INTERVAL 15 DAY, b'0', b'0', '13700000322', '0512-66003222', 'tc@tcwl.com',  320500, '苏州市工业园区 88 号',            3, 2, 2, '园区二期扩建中', '150', NOW() - INTERVAL 15 DAY, 1),
       (323, '绿源农业科技有限公司',   b'1', NOW() - INTERVAL 25 DAY, '农产品溯源系统需求明确，倾向 SaaS 部署', NOW() + INTERVAL 5 DAY, 151, NOW() - INTERVAL 25 DAY, b'0', b'1', '13700000323', '020-66003233', 'ly@lvyn.com',  440100, '广州市天河区智慧城 8 栋',         4, 2, 3, '线索 303 转化', '151', NOW() - INTERVAL 25 DAY, 1),
       (324, '康和医药连锁有限公司',   b'1', NOW() - INTERVAL 2 DAY,  '会员营销与私域运营需求沟通中', NOW() + INTERVAL 12 DAY, 151, NOW() - INTERVAL 12 DAY, b'0', b'0', '13700000324', '020-66003244', 'kh@khlyf.com', 440100, '广州市越秀区中山五路 2 号',       1, 2, 3, '连锁药房 80 家门店', '151', NOW() - INTERVAL 12 DAY, 1),
       (325, '盛世广告传媒有限公司',   b'0', NOW() - INTERVAL 8 DAY,  '项目协作管理需求初步调研', NOW() + INTERVAL 15 DAY, 1,   NOW() - INTERVAL 8 DAY,  b'0', b'0', '13700000325', '010-66003255', 'ss@sscm.com',  110100, '北京市朝阳区广渠路 36 号',        2, 3, 1, '创意型公司，决策链短', '1', NOW() - INTERVAL 20 DAY, 1),
       (326, '金辉电子科技有限公司',   b'1', NOW() - INTERVAL 30 DAY, 'MES 数据采集模块验收通过，尾款待收', NOW() - INTERVAL 5 DAY, 1, NOW() - INTERVAL 30 DAY, b'0', b'1', '13700000326', '0755-66003266', 'jh@jhdz.com', 440300, '深圳市宝安区西乡大道 5 号',       3, 1, 2, '线索 304 转化，已签约', '1', NOW() - INTERVAL 70 DAY, 1),
       (327, '朗润医疗器械有限公司',   b'0', NULL, NULL, NULL, 151, NULL, b'0', b'0', '13700000327', '021-66003277', 'lr@ylqx.com',  310100, '上海市闵行区莘庄工业区 12 号',    1, 2, 4, '医疗器械经销', '151', NOW() - INTERVAL 2 DAY, 1),
       (328, '百味食品加工有限公司',   b'1', NOW() - INTERVAL 4 DAY,  '已发送报价单，等待采购部反馈', NOW() + INTERVAL 6 DAY,  1,   NOW() - INTERVAL 9 DAY,  b'0', b'0', '13700000328', '0571-66003288', 'bw@bwsp.com',  330100, '杭州市余杭区食品工业园 6 号',     3, 3, 1, '预算 15 万以内', '1', NOW() - INTERVAL 9 DAY, 1),
       (329, '华宇置业集团有限公司',   b'1', NOW() - INTERVAL 28 DAY, '地产项目管理系统选型，进入方案阶段', NOW() + INTERVAL 20 DAY, 150, NOW() - INTERVAL 28 DAY, b'0', b'0', '13700000329', '025-66003299', 'hy@hyzy.com',  320100, '南京市建邺区江东中路 100 号',     1, 1, 4, '线索 307 转化，标杆客户候选', '150', NOW() - INTERVAL 65 DAY, 1),
       (330, '四方五金制品有限公司',   b'0', NULL, NULL, NULL, 151, NULL, b'0', b'0', '13700000330', '0512-66003300', NULL,           320500, '苏州市昆山市精密制造园 3 号',     3, 3, 2, '小型加工厂，10 账号规模', '151', NOW() - INTERVAL 5 DAY, 1),
       (331, '锐锋体育用品有限公司',   b'1', NOW() - INTERVAL 35 DAY, '产供销一体化项目签约落地', NOW() + INTERVAL 30 DAY, 150, NOW() - INTERVAL 35 DAY, b'0', b'1', '13700000331', '0577-66003311', 'rf@tysp.com',  330300, '温州市瓯海区经济开发区 18 号',     3, 2, 4, '线索 310 转化，已签约', '150', NOW() - INTERVAL 80 DAY, 1);

-- 4. 联系人（14 条：340-353）
INSERT IGNORE INTO `crm_contact` (`id`, `name`, `customer_id`, `owner_user_id`, `mobile`, `email`, `sex`, `master`, `post`, `detail_address`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (340, '王建国', 320, 150, '13600003401', 'wangjg@htsj.cn',    1, b'1', '信息中心主任', '上海市浦东新区', '决策关键人，关注数据安全', '150', NOW() - INTERVAL 20 DAY, 1),
       (341, '李晓芸', 320, 150, '13600003411', 'lixy@htsj.cn',      2, b'0', '采购专员',     '上海市浦东新区', '执行层对接人',           '150', NOW() - INTERVAL 20 DAY, 1),
       (342, '赵天成', 321, 1,   '13600003421', 'zhaotc@bhls.com',   1, b'1', '运营总监',     '北京市朝阳区',   '关注门店巡检效率',       '1', NOW() - INTERVAL 10 DAY, 1),
       (343, '钱多多', 322, 150, '13600003431', 'qiandd@tcwl.com',   2, b'1', '园区信息部经理', '苏州市工业园区', '对 WMS 模块兴趣浓厚',   '150', NOW() - INTERVAL 15 DAY, 1),
       (344, '孙丽丽', 323, 151, '13600003441', 'sunll@lvyn.com',    2, b'1', '副总经理',     '广州市天河区',   '决策人，倾向 SaaS',     '151', NOW() - INTERVAL 25 DAY, 1),
       (345, '周文斌', 324, 151, '13600003451', 'zhouwb@khlyf.com',  1, b'1', '信息化负责人', '广州市越秀区',   '关注会员营销方案',       '151', NOW() - INTERVAL 12 DAY, 1),
       (346, '吴桂芳', 325, 1,   '13600003461', 'wugf@sscm.com',     2, b'1', '行政经理',     '北京市朝阳区',   '初次对接',               '1', NOW() - INTERVAL 20 DAY, 1),
       (347, '郑国强', 326, 1,   '13600003471', 'zhenggq@jhdz.com',  1, b'1', '生产总监',     '深圳市宝安区',   '验收负责人',             '1', NOW() - INTERVAL 70 DAY, 1),
       (348, '冯小刚', 327, 151, '13600003481', 'fengxg@ylqx.com',   1, b'1', '总经理',       '上海市闵行区',   '决策人',                 '151', NOW() - INTERVAL 2 DAY, 1),
       (349, '陈静怡', 328, 1,   '13600003491', 'chenjy@bwsp.com',   2, b'1', '采购主管',     '杭州市余杭区',   '比价中',                 '1', NOW() - INTERVAL 9 DAY, 1),
       (350, '沈万三', 329, 150, '13600003501', 'shensan@hyzy.com',  1, b'1', '集团 CIO',     '南京市建邺区',   '集团级决策人，重视 ROI', '150', NOW() - INTERVAL 65 DAY, 1),
       (351, '韩梅梅', 330, 151, '13600003511', 'hanmm@sifang.com',  2, b'1', '厂长助理',     '苏州市昆山市',   '日常联系人',             '151', NOW() - INTERVAL 5 DAY, 1),
       (352, '杨过儿', 331, 150, '13600003521', 'yangge@tysp.com',   1, b'1', '供应链总监',   '温州市瓯海区',   '项目负责人',             '150', NOW() - INTERVAL 80 DAY, 1),
       (353, '林妹妹', 323, 151, '13600003531', 'linmm@lvyn.com',    2, b'0', 'IT 专员',      '广州市天河区',   '技术对接人',             '151', NOW() - INTERVAL 24 DAY, 1);

-- 5. 商机（12 条：360-371；status_type=6 标准流程；360-362 已赢单）
INSERT IGNORE INTO `crm_business` (`id`, `name`, `customer_id`, `follow_up_status`, `owner_user_id`, `status_type_id`, `status_id`, `end_status`, `end_remark`, `deal_time`, `total_product_price`, `discount_percent`, `total_price`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (360, '宏图设计院·智慧工地管理系统',       320, b'1', 150, 6, 10, NULL, NULL, NULL,                  420000.00, 5,  399000.00, '含报表定制 1 项',        '150', NOW() - INTERVAL 18 DAY, 1),
       (361, '天诚物流园·WMS 仓储管理系统',      322, b'1', 150, 6, 9,  NULL, NULL, NULL,                  260000.00, 0,  260000.00, '园区二期',               '150', NOW() - INTERVAL 14 DAY, 1),
       (362, '绿源农业·农产品溯源 SaaS',         323, b'1', 151, 6, 11, NULL, NULL, NULL,                  180000.00, 10, 162000.00, 'SaaS 三年订阅',          '151', NOW() - INTERVAL 24 DAY, 1),
       (363, '康和药房·会员营销中台',            324, b'1', 151, 6, 9,  NULL, NULL, NULL,                  150000.00, 0,  150000.00, '80 家门店',              '151', NOW() - INTERVAL 11 DAY, 1),
       (364, '华宇置业·地产项目管理系统',        329, b'1', 150, 6, 11, NULL, NULL, NULL,                  780000.00, 8,  717600.00, '标杆客户候选',           '150', NOW() - INTERVAL 60 DAY, 1),
       (365, '盛世传媒·项目协作管理',            325, b'0', 1,   6, 9,  NULL, NULL, NULL,                  90000.00,  0,  90000.00,  '小规模试点',             '1', NOW() - INTERVAL 18 DAY, 1),
       (366, '蓝海便利·门店巡检小程序',          321, b'0', 1,   6, 9,  NULL, NULL, NULL,                  60000.00,  0,  60000.00,  '200 家门店',             '1', NOW() - INTERVAL 9 DAY, 1),
       (367, '百味食品·产供销一体化（轻量）',    328, b'1', 1,   6, 20, NULL, NULL, NULL,                  150000.00, 5,  142500.00, '进入谈判定价',           '1', NOW() - INTERVAL 8 DAY, 1),
       (368, '金辉电子·MES 二期扩展',            326, b'1', 1,   6, 21, 1, '验收通过顺利赢单', NOW() - INTERVAL 30 DAY, 320000.00, 3, 310400.00, '已签约', '1', NOW() - INTERVAL 68 DAY, 1),
       (369, '锐锋体育·产供销一体化',            331, b'1', 150, 6, 21, 1, '顺利签约', NOW() - INTERVAL 35 DAY,        286000.00, 2, 280280.00, '已签约',                 '150', NOW() - INTERVAL 78 DAY, 1),
       (370, '朗润医疗·进销存管理',              327, b'0', 151, 6, 9,  NULL, NULL, NULL,                  80000.00,  0,  80000.00,  '初步商机',               '151', NOW() - INTERVAL 2 DAY, 1),
       (371, '宏图设计院·移动端扩展',            320, b'0', 150, 6, 9,  NULL, NULL, NULL,                  24000.00,  0,  24000.00,  '一期延伸机会',           '150', NOW() - INTERVAL 5 DAY, 1);

-- 6. 商机产品关联（business_product）
INSERT IGNORE INTO `crm_business_product` (`id`, `business_id`, `product_id`, `product_price`, `count`, `total_price`)
VALUES (1, 360, 201, 158000.00, 1, 158000.00), (2, 360, 206, 15000.00, 1, 15000.00), (3, 360, 203, 20000.00, 1, 20000.00),
       (4, 362, 202, 298000.00, 1, 298000.00), (5, 369, 202, 298000.00, 1, 298000.00), (6, 368, 201, 158000.00, 1, 158000.00),
       (7, 368, 208, 42000.00, 1, 42000.00), (8, 367, 200, 80000.00, 1, 80000.00), (9, 367, 207, 28000.00, 1, 28000.00),
       (10, 364, 202, 298000.00, 1, 298000.00), (11, 364, 204, 60000.00, 1, 60000.00), (12, 365, 200, 80000.00, 1, 80000.00);

-- 7. 合同（10 条：380-389；audit_status 2=审批通过；与赢单商机衔接）
INSERT IGNORE INTO `crm_contract` (`id`, `name`, `no`, `customer_id`, `business_id`, `owner_user_id`, `process_instance_id`, `audit_status`, `order_date`, `start_time`, `end_time`, `total_product_price`, `discount_percent`, `total_price`, `sign_user_id`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (380, '金辉电子 MES 二期扩展合同',     'HT-2026-0388', 326, 368, 1,   NULL, 2, NOW() - INTERVAL 32 DAY, NOW() - INTERVAL 30 DAY, NOW() + INTERVAL 335 DAY, 320000.00, 3, 310400.00, 1, '含开放 API 扩展包', '1', NOW() - INTERVAL 32 DAY, 1),
       (381, '锐锋体育产供销一体化合同',     'HT-2026-0389', 331, 369, 150, NULL, 2, NOW() - INTERVAL 36 DAY, NOW() - INTERVAL 35 DAY, NOW() + INTERVAL 330 DAY, 286000.00, 2, 280280.00, 150, '产供销一体化一期', '150', NOW() - INTERVAL 36 DAY, 1),
       (382, '康和药房会员营销中台合同',     'HT-2026-0390', 324, NULL, 151, NULL, 2, NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 8 DAY,  NOW() + INTERVAL 355 DAY, 150000.00, 0, 150000.00, 151, '80 家门店分批上线', '151', NOW() - INTERVAL 10 DAY, 1),
       (383, '绿源农业溯源系统 SaaS 合同',   'HT-2026-0391', 323, 362, 151, NULL, 2, NOW() - INTERVAL 23 DAY, NOW() - INTERVAL 22 DAY, NOW() + INTERVAL 343 DAY, 162000.00, 10, 162000.00, 151, 'SaaS 三年', '151', NOW() - INTERVAL 23 DAY, 1),
       (384, '宏图设计院智慧工地一期合同',   'HT-2026-0392', 320, 360, 150, NULL, 2, NOW() - INTERVAL 6 DAY,  NOW() - INTERVAL 5 DAY,  NOW() + INTERVAL 360 DAY, 399000.00, 5, 399000.00, 150, '一期建设', '150', NOW() - INTERVAL 6 DAY, 1),
       (385, '天诚物流园 WMS 一期合同',      'HT-2026-0393', 322, 361, 150, NULL, 1, NOW() - INTERVAL 3 DAY,  NOW() - INTERVAL 2 DAY,  NOW() + INTERVAL 363 DAY, 260000.00, 0, 260000.00, 150, '审批中', '150', NOW() - INTERVAL 3 DAY, 1),
       (386, '百味食品轻量一体化合同',       'HT-2026-0394', 328, 367, 1,   NULL, 0, NOW() - INTERVAL 1 DAY,  NOW(),                    NOW() + INTERVAL 180 DAY, 142500.00, 5, 142500.00, 1, '谈判定价后草签', '1', NOW() - INTERVAL 1 DAY, 1),
       (387, '金辉电子年度维保合同',         'HT-2026-0395', 326, NULL, 1,   NULL, 2, NOW() - INTERVAL 28 DAY, NOW() - INTERVAL 25 DAY, NOW() + INTERVAL 340 DAY, 36000.00, 0, 36000.00, 1, '年度维保', '1', NOW() - INTERVAL 28 DAY, 1),
       (388, '锐锋体育数据迁移服务合同',     'HT-2026-0396', 331, NULL, 150, NULL, 2, NOW() - INTERVAL 33 DAY, NOW() - INTERVAL 33 DAY, NOW() + INTERVAL 30 DAY, 28000.00, 0, 28000.00, 150, '一次性服务', '150', NOW() - INTERVAL 33 DAY, 1),
       (389, '康和药房实施服务合同',         'HT-2026-0397', 324, NULL, 151, NULL, 2, NOW() - INTERVAL 9 DAY,  NOW() - INTERVAL 8 DAY,  NOW() + INTERVAL 60 DAY, 60000.00, 0, 60000.00, 151, '驻场实施', '151', NOW() - INTERVAL 9 DAY, 1);

-- 7.1 即将到期的合同（5 条：390-394；用于演示意图目录的"动态建议"：我负责的 + 审核通过 + 提醒窗口内到期）
--     注意：audit_status 必须是 20（CrmAuditStatusEnum.APPROVE），否则不进入待办口径
INSERT IGNORE INTO `crm_contract` (`id`, `name`, `no`, `customer_id`, `business_id`, `owner_user_id`, `process_instance_id`, `audit_status`, `order_date`, `start_time`, `end_time`, `total_product_price`, `discount_percent`, `total_price`, `sign_user_id`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (390, '宏图设计院年度服务续约合同', 'HT-2026-0398', 320, NULL, 1, NULL, 20, NOW() - INTERVAL 360 DAY, NOW() - INTERVAL 358 DAY, NOW() + INTERVAL 3 DAY, 158000.00, 0, 158000.00, 1, '临近到期，待续约', '1', NOW() - INTERVAL 360 DAY, 1),
       (391, '金辉电子 MES 运维续约合同',   'HT-2026-0399', 326, NULL, 1, NULL, 20, NOW() - INTERVAL 300 DAY, NOW() - INTERVAL 298 DAY, NOW() + INTERVAL 6 DAY, 80000.00, 0, 80000.00, 1, '运维服务即将到期', '1', NOW() - INTERVAL 300 DAY, 1),
       (392, '天诚物流 TMS 年度运维合同',   'HT-2026-0400', 322, NULL, 1, NULL, 20, NOW() - INTERVAL 330 DAY, NOW() - INTERVAL 328 DAY, NOW() + INTERVAL 1 DAY, 96000.00, 0, 96000.00, 1, 'TMS 运维即将到期', '1', NOW() - INTERVAL 330 DAY, 1),
       (393, '绿源农业 SaaS 订阅续费合同',  'HT-2026-0401', 323, NULL, 1, NULL, 20, NOW() - INTERVAL 200 DAY, NOW() - INTERVAL 198 DAY, NOW() + INTERVAL 4 DAY, 68000.00, 0, 68000.00, 1, '订阅即将到期', '1', NOW() - INTERVAL 200 DAY, 1),
       (394, '华宇置业智能楼宇运维合同',    'HT-2026-0402', 329, NULL, 1, NULL, 20, NOW() - INTERVAL 270 DAY, NOW() - INTERVAL 268 DAY, NOW() + INTERVAL 5 DAY, 128000.00, 0, 128000.00, 1, '维保服务即将到期', '1', NOW() - INTERVAL 270 DAY, 1);

-- 8. 合同产品关联（contract_product）
INSERT IGNORE INTO `crm_contract_product` (`id`, `contract_id`, `product_id`, `product_price`, `count`, `total_price`)
VALUES (1, 380, 201, 158000.00, 1, 158000.00), (2, 380, 208, 42000.00, 1, 42000.00), (3, 380, 200, 80000.00, 1, 80000.00),
       (4, 381, 202, 298000.00, 1, 298000.00), (5, 383, 202, 298000.00, 1, 298000.00), (6, 384, 201, 158000.00, 1, 158000.00),
       (7, 384, 206, 15000.00, 1, 15000.00), (8, 384, 203, 20000.00, 1, 20000.00), (9, 387, 205, 36000.00, 1, 36000.00),
       (10, 388, 207, 28000.00, 1, 28000.00), (11, 389, 204, 60000.00, 1, 60000.00), (12, 382, 200, 80000.00, 1, 80000.00);

-- 9. 回款（12 条：400-411；与合同分期对应）
INSERT IGNORE INTO `crm_receivable` (`id`, `no`, `customer_id`, `contract_id`, `owner_user_id`, `return_time`, `return_type`, `price`, `audit_status`, `remark`, `creator`, `create_time`, `tenant_id`)
VALUES (400, 'HK-2026-0401', 326, 380, 1,   NOW() - INTERVAL 28 DAY, 1, 100000.00, 2, '首付款 30%',        '1', NOW() - INTERVAL 28 DAY, 1),
       (401, 'HK-2026-0402', 326, 380, 1,   NOW() - INTERVAL 10 DAY, 1, 120000.00, 2, '交付款 40%',        '1', NOW() - INTERVAL 10 DAY, 1),
       (402, 'HK-2026-0403', 331, 381, 150, NOW() - INTERVAL 30 DAY, 1, 140000.00, 2, '首付款 50%',        '150', NOW() - INTERVAL 30 DAY, 1),
       (403, 'HK-2026-0404', 324, 382, 151, NOW() - INTERVAL 8 DAY,  1, 75000.00,  2, '首付款 50%',        '151', NOW() - INTERVAL 8 DAY, 1),
       (404, 'HK-2026-0405', 323, 383, 151, NOW() - INTERVAL 21 DAY, 2, 80000.00,  2, 'SaaS 年费首年',     '151', NOW() - INTERVAL 21 DAY, 1),
       (405, 'HK-2026-0406', 320, 384, 150, NOW() - INTERVAL 4 DAY,  1, 200000.00, 1, '一期预付款，审批中', '150', NOW() - INTERVAL 4 DAY, 1),
       (406, 'HK-2026-0407', 331, 388, 150, NOW() - INTERVAL 31 DAY, 3, 28000.00,  2, '数据迁移服务费',     '150', NOW() - INTERVAL 31 DAY, 1),
       (407, 'HK-2026-0408', 324, 389, 151, NOW() - INTERVAL 7 DAY,  3, 60000.00,  2, '驻场实施费',         '151', NOW() - INTERVAL 7 DAY, 1),
       (408, 'HK-2026-0409', 326, 387, 1,   NOW() - INTERVAL 25 DAY, 4, 36000.00,  2, '年度维保费',         '1', NOW() - INTERVAL 25 DAY, 1),
       (409, 'HK-2026-0410', 324, 382, 151, NOW() - INTERVAL 6 DAY,  1, 75000.00,  1, '二期款，审批中',     '151', NOW() - INTERVAL 6 DAY, 1),
       (410, 'HK-2026-0411', 328, 386, 1,   NOW() + INTERVAL 10 DAY, 1, 50000.00,  0, '预付款计划',         '1', NOW() - INTERVAL 1 DAY, 1),
       (411, 'HK-2026-0412', 322, 385, 150, NOW() + INTERVAL 20 DAY, 1, 130000.00, 0, '一期预付款计划',     '150', NOW() - INTERVAL 3 DAY, 1);

-- 10. 跟进记录（14 条：500-513；内容与客户 contact_last_content 保持一致）
INSERT IGNORE INTO `crm_follow_up_record` (`id`, `biz_type`, `biz_id`, `type`, `content`, `next_time`, `creator`, `create_time`, `tenant_id`)
VALUES (500, 2, 320, 1, '电话沟通：确认智慧工地管理系统需求，预算 40 万以内，希望本月出方案。', NOW() + INTERVAL 8 DAY, '150', NOW() - INTERVAL 20 DAY, 1),
       (501, 2, 320, 4, '方案宣讲：演示了智慧工地一期蓝图，客户对报表定制模块提出细化要求。', NOW() + INTERVAL 15 DAY, '150', NOW() - INTERVAL 10 DAY, 1),
       (502, 2, 322, 3, '上门拜访：调研物流园区二期，WMS 与 TMS 对接是核心诉求。', NOW() + INTERVAL 9 DAY, '150', NOW() - INTERVAL 15 DAY, 1),
       (503, 2, 323, 2, '邮件确认：SaaS 三年订阅方案与数据迁移范围已达成一致。', NULL, '151', NOW() - INTERVAL 24 DAY, 1),
       (504, 2, 324, 1, '电话沟通：会员营销中台方案获认可，门店分批上线计划确定。', NOW() + INTERVAL 12 DAY, '151', NOW() - INTERVAL 11 DAY, 1),
       (505, 2, 325, 1, '初次电话：了解项目协作管理痛点，客户决策链短，需要案例支撑。', NOW() + INTERVAL 15 DAY, '1', NOW() - INTERVAL 18 DAY, 1),
       (506, 2, 326, 3, '现场验收：MES 数据采集模块验收通过，尾款流程已启动。', NOW() - INTERVAL 5 DAY, '1', NOW() - INTERVAL 30 DAY, 1),
       (507, 2, 327, 1, '初次接触：进销存管理需求初步沟通，约下周演示。', NOW() + INTERVAL 4 DAY, '151', NOW() - INTERVAL 2 DAY, 1),
       (508, 2, 328, 1, '报价跟进：已发送轻量一体化方案报价单，等待采购反馈。', NOW() + INTERVAL 6 DAY, '1', NOW() - INTERVAL 4 DAY, 1),
       (509, 2, 329, 4, '方案评审：地产项目管理系统方案通过 IT 委员会初审，进入商务谈判。', NOW() + INTERVAL 20 DAY, '150', NOW() - INTERVAL 28 DAY, 1),
       (510, 2, 331, 3, '签约回访：产供销一体化项目签约，启动实施与数据迁移。', NOW() + INTERVAL 30 DAY, '150', NOW() - INTERVAL 35 DAY, 1),
       (511, 2, 321, 1, '需求挖掘：200 家门店巡检场景沟通，小程序形态获认可。', NOW() + INTERVAL 7 DAY, '1', NOW() - INTERVAL 9 DAY, 1),
       (512, 2, 324, 3, '门店走访：抽样 5 家门店调研会员运营现状，输出调研纪要。', NOW() + INTERVAL 20 DAY, '151', NOW() - INTERVAL 6 DAY, 1),
       (513, 2, 322, 2, '邮件跟进：WMS 一期合同审批中，客户希望月底前启动实施。', NOW() + INTERVAL 20 DAY, '150', NOW() - INTERVAL 3 DAY, 1);

-- 11. 数据权限（每条记录：负责人=1 级；其余演示用户=2 只读，保证三账号都可见）
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 2, c.id, u.uid, CASE WHEN c.owner_user_id = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM crm_customer c CROSS JOIN (SELECT 1 uid UNION SELECT 150 UNION SELECT 151) u
WHERE c.id BETWEEN 320 AND 331;
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 3, ct.id, u.uid, CASE WHEN ct.owner_user_id = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM crm_contact ct CROSS JOIN (SELECT 1 uid UNION SELECT 150 UNION SELECT 151) u
WHERE ct.id BETWEEN 340 AND 353;
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 4, b.id, u.uid, CASE WHEN b.owner_user_id = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM crm_business b CROSS JOIN (SELECT 1 uid UNION SELECT 150 UNION SELECT 151) u
WHERE b.id BETWEEN 360 AND 371;
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 5, ct.id, u.uid, CASE WHEN ct.owner_user_id = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM crm_contract ct CROSS JOIN (SELECT 1 uid UNION SELECT 150 UNION SELECT 151) u
WHERE ct.id BETWEEN 380 AND 389;
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 7, r.id, u.uid, CASE WHEN r.owner_user_id = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM crm_receivable r CROSS JOIN (SELECT 1 uid UNION SELECT 150 UNION SELECT 151) u
WHERE r.id BETWEEN 400 AND 411;
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 1, cl.id, u.uid, CASE WHEN cl.owner_user_id = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM crm_clue cl CROSS JOIN (SELECT 1 uid UNION SELECT 150 UNION SELECT 151) u
WHERE cl.id BETWEEN 300 AND 311;
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 6, p.id, u.uid, CASE WHEN p.owner_user_id = u.uid THEN 1 ELSE 2 END, '1', NOW(), 1
FROM crm_product p CROSS JOIN (SELECT 1 uid UNION SELECT 150 UNION SELECT 151) u
WHERE p.id BETWEEN 200 AND 209;

-- 12. 完整库演示客户统一归口 admin（我负责的场景可见）
UPDATE `crm_customer` SET `owner_user_id` = 1, `owner_time` = NOW() WHERE `id` BETWEEN 12 AND 17 AND (`owner_user_id` IS NULL OR `owner_user_id` <> 1);
INSERT IGNORE INTO `crm_permission` (`biz_type`, `biz_id`, `user_id`, `level`, `creator`, `create_time`, `tenant_id`)
SELECT 2, c.id, 1, 1, '1', NOW(), 1 FROM crm_customer c WHERE c.id BETWEEN 12 AND 17;

-- 13. 数据完整性兜底：有负责人必须有 owner_time（公海天数计算依赖）
UPDATE `crm_customer` SET `owner_time` = NOW() WHERE `owner_time` IS NULL AND `owner_user_id` IS NOT NULL;
