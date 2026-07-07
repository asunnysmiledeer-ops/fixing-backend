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
TRUNCATE TABLE sales_order; TRUNCATE TABLE assembly_part; TRUNCATE TABLE assembly_record; TRUNCATE TABLE machine_stock; TRUNCATE TABLE sys_oper_log; TRUNCATE TABLE sys_dict; TRUNCATE TABLE sys_param; TRUNCATE TABLE sys_feature; TRUNCATE TABLE part_request; TRUNCATE TABLE ticket_charge; TRUNCATE TABLE spare_part_usage; TRUNCATE TABLE ticket_log;
TRUNCATE TABLE ticket; TRUNCATE TABLE contract_software; TRUNCATE TABLE contract_part;
TRUNCATE TABLE contract_equipment; TRUNCATE TABLE contract; TRUNCATE TABLE invoice;
TRUNCATE TABLE software_instance; TRUNCATE TABLE spare_part; TRUNCATE TABLE equipment;
TRUNCATE TABLE customer; TRUNCATE TABLE sys_role_perm; TRUNCATE TABLE sys_user;
SET FOREIGN_KEY_CHECKS = 1;

-- 用户（密码均 123456 的 BCrypt 散列）
INSERT INTO sys_user (id, username, password, role, real_name, customer_id, create_time) VALUES
  (9, 'super',       '$2a$10$WIW1rEIYMRonrCmeeQ5Rx.44PJpMrDyP2joYHR.H9F3aCn6BvOS1.', 'SUPER_ADMIN', '陈总(平台超管)', NULL, NOW()),
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
  ('ADMIN', 'maint:part:list'), ('ADMIN', 'maint:part:edit'),  -- 管理员只做入库审批(part:edit)，申请配件(part:request)是工程师的事
  ('ADMIN', 'maint:customer:list'), ('ADMIN', 'maint:customer:edit'),
  ('ADMIN', 'maint:equipment:list'), ('ADMIN', 'maint:equipment:edit'),
  ('ADMIN', 'maint:contract:list'), ('ADMIN', 'maint:contract:edit'),
  ('ADMIN', 'maint:invoice:list'), ('ADMIN', 'maint:invoice:edit'),
  ('ADMIN', 'maint:dashboard:view'),
  -- 订单与整机（用户定案：超管录单，管理员执行组装与派发）
  ('ADMIN', 'maint:order:list'), ('ADMIN', 'maint:order:dispatch'),
  ('ADMIN', 'maint:machine:list'), ('ADMIN', 'maint:machine:edit'),
  ('SUPER_ADMIN', 'maint:order:edit'),
  -- 平台管理权限（挂在 SUPER_ADMIN 名下入库：超管代码短路，这些行的意义是让 permsOf 全量列表里包含它们 → 前端画平台页签）
  ('SUPER_ADMIN', 'platform:user:list'), ('SUPER_ADMIN', 'platform:user:edit'),
  ('SUPER_ADMIN', 'platform:perm:list'), ('SUPER_ADMIN', 'platform:perm:edit'),
  ('SUPER_ADMIN', 'platform:config:list'), ('SUPER_ADMIN', 'platform:config:edit'),
  ('SUPER_ADMIN', 'platform:log:list'), ('SUPER_ADMIN', 'platform:overview:view');

-- 整机库存：叫号主机现货1台（订单要2台时演示"先组装再派发"）
INSERT INTO machine_stock (equipment_type, model, qty, create_time) VALUES
  ('叫号主机', 'QM-2000', 1, NOW()),
  ('叫号屏',   'QS-55A',  2, NOW()),
  ('取号机',   'QT-100',  0, NOW());

-- 功能开关：驻场工程师默认开启（演示"平台端一键启停"）
INSERT INTO sys_feature (feature_key, name, enabled, remark, create_time) VALUES
  ('resident_engineer', '驻场工程师模式', 1, '开启后可在人事管理配置驻场，派单时驻场工程师置顶推荐', NOW());

-- 业务参数（原 yml 写死值迁入，平台端可改即刻生效）
INSERT INTO sys_param (param_key, param_value, name, create_time) VALUES
  ('charge.visit_fee',     '200', '按次收费·上门费(元)', NOW()),
  ('charge.labor_fee',     '300', '按次收费·维修费默认(元)', NOW()),
  ('contract.remind_days', '7',   '合同到期提前提醒天数', NOW());

-- 数据字典（可配置平台第一批：客户类型/备件分类/设备类型）
INSERT INTO sys_dict (dict_type, dict_value, dict_label, sort, create_time) VALUES
  ('customer_type', 'HOSPITAL', '医院', 1, NOW()),
  ('customer_type', 'CLINIC',   '诊所', 2, NOW()),
  ('customer_type', 'SCHOOL',   '学校', 3, NOW()),
  ('customer_type', 'FACTORY',  '工厂', 4, NOW()),
  ('part_category', 'PART',       '配件', 1, NOW()),
  ('part_category', 'COMPONENT',  '部件', 2, NOW()),
  ('part_category', 'CONSUMABLE', '耗材', 3, NOW()),
  ('equipment_type', '叫号主机', '叫号主机', 1, NOW()),
  ('equipment_type', '叫号屏',   '叫号屏', 2, NOW()),
  ('equipment_type', '取号机',   '取号机', 3, NOW());

INSERT INTO customer (id, name, customer_type, contact_name, contact_phone, address, create_time) VALUES
  (1, '市第一人民医院', 'HOSPITAL', '王信息', '13800000001', '示例市中心大道1号', NOW()),
  (2, '康泰社区诊所',   'CLINIC',   '刘主任', '13900000002', '示例市幸福路88号', NOW());

INSERT INTO equipment (id, customer_id, equipment_type, model, serial_no, location, status, delivered_at, create_time) VALUES
  (1, 1, '叫号主机', 'QM-2000', 'SN-QM2000-0001', '门诊大厅一楼',   'NORMAL', '2025-08-15', NOW()),
  (2, 1, '叫号屏',   'QS-55A',  'SN-QS55A-0007',  '内科二诊室门口', 'NORMAL', '2025-08-15', NOW()),
  (3, 2, '取号机',   'QT-100',  'SN-QT100-0033',  '诊所大厅',       'NORMAL', '2026-01-10', NOW());

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
