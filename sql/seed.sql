-- ============================================================
-- FIX-ING · 演示种子数据（M1）
-- 数据故事线（为演示颗粒化计费而设计）：
--   市一医院：合同保 [叫号主机(设备1)]，免费件只有 [打印头(备件1)]，保 [叫号系统]
--     → 设备2(叫号屏) 不在保 → 报修按次收费（上门+配件+维修）
--     → 设备1 报修在保，但换液晶屏(备件2)不在免费清单 → 只收配件费
--   康泰诊所：合同保 [取号机(设备3)]，年底到期
-- 可重复执行（仅限演示库）
-- ============================================================
USE fixing;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE part_request; TRUNCATE TABLE ticket_charge; TRUNCATE TABLE spare_part_usage; TRUNCATE TABLE ticket_log;
TRUNCATE TABLE ticket; TRUNCATE TABLE contract_software; TRUNCATE TABLE contract_part;
TRUNCATE TABLE contract_equipment; TRUNCATE TABLE contract; TRUNCATE TABLE invoice;
TRUNCATE TABLE software_instance; TRUNCATE TABLE spare_part; TRUNCATE TABLE equipment;
TRUNCATE TABLE customer; TRUNCATE TABLE sys_role_perm; TRUNCATE TABLE sys_user;
SET FOREIGN_KEY_CHECKS = 1;

-- 用户（密码均 123456 的 BCrypt 散列）
INSERT INTO sys_user (id, username, password, role, real_name, customer_id, create_time) VALUES
  (1, 'admin',       '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'ADMIN',    '李运营(平台管理员)', NULL, NOW()),
  (2, 'engineer_zh', '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'ENGINEER', '张工(硬件工程师)', NULL, NOW()),
  (3, 'engineer_li', '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'ENGINEER', '李工(软件工程师)', NULL, NOW()),
  (4, 'hospital_it', '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'CUSTOMER', '王信息(市一医院)', 1, NOW()),
  (5, 'clinic_it',   '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'CUSTOMER', '刘主任(康泰诊所)', 2, NOW());

-- 角色-权限映射（权限字符串化的种子；改权限=改这里，不改代码）
INSERT INTO sys_role_perm (role, perm) VALUES
  -- 客户：报修 + 看自己的单 + 确认/驳回/取消 + 看自己设备
  ('CUSTOMER', 'maint:ticket:list'), ('CUSTOMER', 'maint:ticket:add'), ('CUSTOMER', 'maint:ticket:confirm'),
  ('CUSTOMER', 'maint:equipment:list'),
  -- 工程师：看自己的单 + 接单/换件/完工 + 备件余量
  ('ENGINEER', 'maint:ticket:list'), ('ENGINEER', 'maint:ticket:handle'), ('ENGINEER', 'maint:part:list'),
  -- 工程师上门需要客户地址/设备位置：给只读台账权限（页面自动多出"客户与设备"页签——RBAC 改库不改码）
  ('ENGINEER', 'maint:customer:list'), ('ENGINEER', 'maint:equipment:list'),
  ('ENGINEER', 'maint:part:request'),
  -- 管理员在代码里短路放行，但仍插一份全量（permsOf(ADMIN) 取 DISTINCT 给前端画页签）
  ('ADMIN', 'maint:ticket:list'), ('ADMIN', 'maint:ticket:add'), ('ADMIN', 'maint:ticket:assign'),
  ('ADMIN', 'maint:ticket:confirm'),
  ('ADMIN', 'maint:part:list'), ('ADMIN', 'maint:part:edit'), ('ADMIN', 'maint:part:request'),
  ('ADMIN', 'maint:customer:list'), ('ADMIN', 'maint:customer:edit'),
  ('ADMIN', 'maint:equipment:list'), ('ADMIN', 'maint:equipment:edit'),
  ('ADMIN', 'maint:contract:list'), ('ADMIN', 'maint:contract:edit'),
  ('ADMIN', 'maint:invoice:list'), ('ADMIN', 'maint:invoice:edit'),
  ('ADMIN', 'maint:dashboard:view');

INSERT INTO customer (id, name, customer_type, contact_name, contact_phone, address, create_time) VALUES
  (1, '市第一人民医院', 'HOSPITAL', '王信息', '13800000001', '示例市中心大道1号', NOW()),
  (2, '康泰社区诊所',   'CLINIC',   '刘主任', '13900000002', '示例市幸福路88号', NOW());

INSERT INTO equipment (id, customer_id, equipment_type, model, serial_no, location, status, create_time) VALUES
  (1, 1, '叫号主机', 'QM-2000', 'SN-QM2000-0001', '门诊大厅一楼', 'NORMAL', NOW()),
  (2, 1, '叫号屏',   'QS-55A',  'SN-QS55A-0007',  '内科二诊室门口', 'NORMAL', NOW()),
  (3, 2, '取号机',   'QT-100',  'SN-QT100-0033',  '诊所大厅', 'NORMAL', NOW());

INSERT INTO software_instance (id, customer_id, equipment_id, name, version, create_time) VALUES
  (1, 1, 1, '叫号系统', 'v3.2.1', NOW()),
  (2, 2, 3, '取号系统', 'v1.8.0', NOW());

-- 备件：打印头适用叫号主机(在保1台×2→动态阈值3取人工)；液晶屏适用叫号屏(叫号屏不在保→阈值=人工1)
INSERT INTO spare_part (id, name, category, equipment_type, per_device_qty, stock_qty, low_stock_threshold, unit_price, create_time) VALUES
  (1, '80mm 热敏打印头', 'PART',       '叫号主机', 2, 10, 3,  120.00, NOW()),
  (2, '55寸液晶屏总成',  'COMPONENT',  '叫号屏',   1,  5, 1, 1500.00, NOW()),
  (3, '热敏打印纸(卷)',  'CONSUMABLE', NULL,       0, 50, 20,   3.50, NOW());

-- 合同：医院合同 5 天后到期（演示到期提醒）；只保设备1、免费件只有打印头、保叫号系统
INSERT INTO contract (id, customer_id, name, scope, start_date, end_date, billing_type, amount, status, create_time) VALUES
  (1, 1, '2025-2026年度叫号系统维保合同', '叫号主机硬件维保+叫号系统软件服务（叫号屏未纳保）', '2025-08-01', DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'YEARLY', 36000.00, 'ACTIVE', NOW()),
  (2, 2, '取号机维保协议', '取号机硬件维保', '2026-01-01', '2026-12-31', 'YEARLY', 8000.00, 'ACTIVE', NOW());

-- 颗粒化绑定：这就是"医院↔合同↔设备/配件/软件"的落点
INSERT INTO contract_equipment (contract_id, equipment_id) VALUES (1, 1), (2, 3);
INSERT INTO contract_part     (contract_id, part_id)      VALUES (1, 1);
INSERT INTO contract_software (contract_id, software_instance_id) VALUES (1, 1);

INSERT INTO invoice (id, invoice_no, customer_id, contract_id, title, amount, status, issued_at, paid_at, create_time) VALUES
  (1, 'INV-2026-0001', 1, 1, '2026年度维保费（上半年）', 18000.00, 'PAID',   '2026-01-15', '2026-02-01', NOW()),
  (2, 'INV-2026-0002', 1, 1, '2026年度维保费（下半年）', 18000.00, 'ISSUED', '2026-06-30', NULL, NOW());
