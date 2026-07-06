-- ============================================================
-- FIX-ING Demo · 演示种子数据（v0.3）
-- 2 家客户（验证数据隔离）、3 台设备、3 备件、5 用户、2 合同、2 发票
-- 可重复执行：先清空再插入（仅限演示库！生产脚本绝不这么写）
-- ============================================================
USE fixing;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE invoice;
TRUNCATE TABLE contract;
TRUNCATE TABLE spare_part_usage;
TRUNCATE TABLE ticket_log;
TRUNCATE TABLE ticket;
TRUNCATE TABLE spare_part;
TRUNCATE TABLE equipment;
TRUNCATE TABLE customer;
TRUNCATE TABLE sys_user;
SET FOREIGN_KEY_CHECKS = 1;

-- 两家客户：用第二家验证"客户只能看到自己医院的数据"
INSERT INTO customer (id, name, customer_type, contact_name, contact_phone, address) VALUES
  (1, '市第一人民医院', 'HOSPITAL', '王信息', '13800000001', '示例市中心大道1号'),
  (2, '康泰社区诊所',   'CLINIC',   '刘主任', '13900000002', '示例市幸福路88号');

-- 用户：密码统一 123456（存 BCrypt 散列）。客户角色必须带 customer_id（数据隔离的根）
INSERT INTO sys_user (id, username, password, role, real_name, customer_id) VALUES
  (1, 'hospital_it', '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'CUSTOMER', '王信息(市一医院)', 1),
  (2, 'admin',       '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'ADMIN',    '李运营(平台管理员)', NULL),
  (3, 'engineer_zh', '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'ENGINEER', '张工(硬件工程师)', NULL),
  (4, 'clinic_it',   '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'CUSTOMER', '刘主任(康泰诊所)', 2),
  (5, 'engineer_li', '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'ENGINEER', '李工(软件工程师)', NULL);

-- 设备：1、2 属市一医院，3 属康泰诊所
INSERT INTO equipment (id, customer_id, equipment_type, model, serial_no, location, status) VALUES
  (1, 1, '叫号主机', 'QM-2000', 'SN-QM2000-0001', '门诊大厅一楼', 'NORMAL'),
  (2, 1, '叫号屏',   'QS-55A',  'SN-QS55A-0007',  '内科二诊室门口', 'NORMAL'),
  (3, 2, '取号机',   'QT-100',  'SN-QT100-0033',  '诊所大厅', 'NORMAL');

-- 备件：阈值各不相同 —— 液晶屏(2<3)开局就触发低库存预警，方便演示
INSERT INTO spare_part (id, name, category, stock_qty, low_stock_threshold, unit_price) VALUES
  (1, '80mm 热敏打印头',   'PART',       10, 3,  120.00),
  (2, '55寸液晶屏总成',    'COMPONENT',   2, 3, 1500.00),
  (3, '热敏打印纸(卷)',    'CONSUMABLE', 50, 20,   3.50);

-- 合同：市一医院年费合同"即将到期"（今天+5天，触发"到期前一周"提醒），康泰按次合同正常
INSERT INTO contract (id, customer_id, name, scope, start_date, end_date, billing_type, amount, status) VALUES
  (1, 1, '2025-2026年度叫号系统维保合同', '叫号主机/叫号屏 硬件维保 + 软件升级', '2025-08-01', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'YEARLY', 36000.00, 'ACTIVE'),
  (2, 2, '取号机按次维保协议', '取号机上门维修，按次计费', '2026-01-01', '2026-12-31', 'PER_CASE', 0.00, 'ACTIVE');

-- 发票：一张已回款、一张未回款（Dashboard 的应收统计有东西可看）
INSERT INTO invoice (id, invoice_no, customer_id, contract_id, title, amount, status, issued_at, paid_at) VALUES
  (1, 'INV-2026-0001', 1, 1, '2026年度维保费（上半年）', 18000.00, 'PAID',   '2026-01-15', '2026-02-01'),
  (2, 'INV-2026-0002', 1, 1, '2026年度维保费（下半年）', 18000.00, 'ISSUED', '2026-06-30', NULL);
