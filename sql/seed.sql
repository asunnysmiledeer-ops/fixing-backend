-- ============================================================
-- FIX-ING Demo v0 · 演示种子数据（G 阶段）
-- 1 个客户、2 台设备、3 个备件、3 个用户（客户/管理员/工程师各一）
-- 可重复执行：先清空再插入（仅限演示库！生产脚本绝不这么写）
-- ============================================================
USE fixing;

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE spare_part_usage;
TRUNCATE TABLE ticket_log;
TRUNCATE TABLE ticket;
TRUNCATE TABLE spare_part;
TRUNCATE TABLE equipment;
TRUNCATE TABLE customer;
TRUNCATE TABLE sys_user;
SET FOREIGN_KEY_CHECKS = 1;

-- 三个角色各一人（id 固定：1=客户 2=管理员 3=工程师，演示时好记）
INSERT INTO sys_user (id, username, password, role, real_name) VALUES
  (1, 'hospital_it', '123456', 'CUSTOMER', '王信息(医院信息科)'),
  (2, 'admin',       '123456', 'ADMIN',    '李运营(平台管理员)'),
  (3, 'engineer_zh', '123456', 'ENGINEER', '张工(硬件工程师)');

-- 示范客户：医院只是 customer_type 的一种取值
INSERT INTO customer (id, name, customer_type, contact_name, contact_phone, address) VALUES
  (1, '市第一人民医院', 'HOSPITAL', '王信息', '13800000001', '示例市中心大道1号');

-- 两台叫号设备
INSERT INTO equipment (id, customer_id, equipment_type, model, serial_no, location, status) VALUES
  (1, 1, '叫号主机', 'QM-2000', 'SN-QM2000-0001', '门诊大厅一楼', 'NORMAL'),
  (2, 1, '叫号屏',   'QS-55A',  'SN-QS55A-0007',  '内科二诊室门口', 'NORMAL');

-- 三种备件（覆盖三个分类）
INSERT INTO spare_part (id, name, category, stock_qty, unit_price) VALUES
  (1, '80mm 热敏打印头',   'PART',       10, 120.00),
  (2, '55寸液晶屏总成',    'COMPONENT',   2, 1500.00),
  (3, '热敏打印纸(卷)',    'CONSUMABLE', 50, 3.50);
