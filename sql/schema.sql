-- ============================================================
-- FIX-ING · 建库建表脚本（M1 真实项目版）
-- 硬化三件套（每张业务表统一）：
--   审计: create_by/create_time/update_by/update_time（MetaObjectHandler 自动填）
--   租户: tenant_id 预留（恒为1，多租户时补逻辑不动表）
--   软删: del_flag（MyBatis-Plus @TableLogic 接管，数据永不物理删除）
-- M1 新增：software_instance、contract_equipment/part/software（颗粒化绑定）、
--          ticket_charge（按次结算）、sys_role_perm（权限字符串）
-- ============================================================
CREATE DATABASE IF NOT EXISTS fixing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fixing;

-- 公共列模板（每张表末尾重复出现）：
--   create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
--   tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0'

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(32)  NOT NULL UNIQUE,
  password    VARCHAR(64)  NOT NULL COMMENT 'BCrypt 散列，永不存明文',
  role        VARCHAR(16)  NOT NULL COMMENT 'CUSTOMER/ADMIN/ENGINEER',
  real_name   VARCHAR(32),
  customer_id BIGINT COMMENT '客户角色所属单位（数据隔离的根）',
  status      CHAR(1) NOT NULL DEFAULT '0' COMMENT '0正常 1停用（人事管理）',
  resident_customer_id BIGINT COMMENT '工程师驻场客户（派单优先推荐，受平台开关控制）',
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0'
) COMMENT '系统用户';

DROP TABLE IF EXISTS sys_role_perm;
CREATE TABLE sys_role_perm (
  id   BIGINT PRIMARY KEY AUTO_INCREMENT,
  role VARCHAR(16) NOT NULL COMMENT 'CUSTOMER/ADMIN/ENGINEER',
  perm VARCHAR(64) NOT NULL COMMENT '权限字符串 如 maint:ticket:assign',
  UNIQUE KEY uk_role_perm (role, perm)
) COMMENT '角色-权限映射（改权限=改数据，不改代码）';

DROP TABLE IF EXISTS sys_feature;
CREATE TABLE sys_feature (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  feature_key VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(64) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  remark VARCHAR(255),
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0'
) COMMENT '功能开关（平台端一键启停，不发版）';

DROP TABLE IF EXISTS sys_param;
CREATE TABLE sys_param (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  param_key VARCHAR(64) NOT NULL UNIQUE,
  param_value VARCHAR(255),
  name VARCHAR(64),
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0'
) COMMENT '业务参数（收费标准/提醒天数等，平台端可改即刻生效）';

DROP TABLE IF EXISTS sys_dict;
CREATE TABLE sys_dict (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_type  VARCHAR(64) NOT NULL COMMENT 'customer_type/equipment_type/part_category',
  dict_value VARCHAR(64) NOT NULL,
  dict_label VARCHAR(64) NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_dict_type (dict_type)
) COMMENT '数据字典（可配置平台的核心：行业差异交给配置）';

DROP TABLE IF EXISTS sys_oper_log;
CREATE TABLE sys_oper_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT, user_name VARCHAR(32),
  method VARCHAR(8), uri VARCHAR(255),
  status INT, cost_ms BIGINT,
  create_time DATETIME,
  INDEX idx_ol_time (create_time)
) COMMENT '操作日志（业务追踪：全部写操作自动记录）';

DROP TABLE IF EXISTS customer;
CREATE TABLE customer (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(64)  NOT NULL,
  customer_type VARCHAR(32)  NOT NULL DEFAULT 'HOSPITAL',
  contact_name  VARCHAR(32), contact_phone VARCHAR(20), address VARCHAR(255),
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0'
) COMMENT '客户台账';

DROP TABLE IF EXISTS equipment;
CREATE TABLE equipment (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id    BIGINT       NOT NULL,
  equipment_type VARCHAR(32)  NOT NULL COMMENT '备件动态阈值的匹配键',
  model          VARCHAR(64),
  serial_no      VARCHAR(64)  NOT NULL UNIQUE,
  location       VARCHAR(128),
  status         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
  delivered_at   DATE COMMENT '运送/交付日期（查询筛选维度）',
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_eq_customer (customer_id), INDEX idx_eq_serial (serial_no), INDEX idx_eq_delivered (delivered_at)
) COMMENT '设备台账';

DROP TABLE IF EXISTS software_instance;
CREATE TABLE software_instance (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id  BIGINT      NOT NULL,
  equipment_id BIGINT COMMENT '装在哪台设备（纯远程软件可空）',
  name         VARCHAR(64) NOT NULL,
  version      VARCHAR(32),
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_si_customer (customer_id)
) COMMENT '软件实例（M1新增）';

DROP TABLE IF EXISTS spare_part;
CREATE TABLE spare_part (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  name                VARCHAR(64) NOT NULL,
  category            VARCHAR(16) NOT NULL COMMENT 'PART/COMPONENT/CONSUMABLE',
  equipment_type      VARCHAR(32) COMMENT '适用设备类型（空=通用）',
  per_device_qty      INT NOT NULL DEFAULT 1 COMMENT '每台在保设备建议备货数',
  stock_qty           INT NOT NULL DEFAULT 0,
  low_stock_threshold INT NOT NULL DEFAULT 5 COMMENT '人工阈值（动态阈值取较大者）',
  unit_price          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  CONSTRAINT chk_stock_non_negative CHECK (stock_qty >= 0)
) COMMENT '备件库存';

DROP TABLE IF EXISTS contract;
CREATE TABLE contract (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id  BIGINT       NOT NULL,
  name         VARCHAR(128) NOT NULL,
  scope        VARCHAR(255),
  start_date   DATE NOT NULL,
  end_date     DATE NOT NULL,
  billing_type VARCHAR(16) NOT NULL DEFAULT 'YEARLY' COMMENT 'YEARLY/PER_CASE/MIXED',
  amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/TERMINATED',
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_ct_customer (customer_id)
) COMMENT '维保合同（保什么看三张绑定明细）';

-- 颗粒化绑定三张明细（M1 核心）：合同精确保"哪几台/哪些件免费/哪些软件"
DROP TABLE IF EXISTS contract_equipment;
CREATE TABLE contract_equipment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL, equipment_id BIGINT NOT NULL,
  UNIQUE KEY uk_ce (contract_id, equipment_id), INDEX idx_ce_eq (equipment_id)
) COMMENT '合同↔设备：在保判定的依据';

DROP TABLE IF EXISTS contract_part;
CREATE TABLE contract_part (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL, part_id BIGINT NOT NULL,
  UNIQUE KEY uk_cp (contract_id, part_id)
) COMMENT '合同↔备件：免费更换清单';

DROP TABLE IF EXISTS contract_software;
CREATE TABLE contract_software (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL, software_instance_id BIGINT NOT NULL,
  UNIQUE KEY uk_cs (contract_id, software_instance_id)
) COMMENT '合同↔软件：保哪些软件的修复升级';

DROP TABLE IF EXISTS ticket;
CREATE TABLE ticket (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_no            VARCHAR(32) NOT NULL UNIQUE,
  customer_id          BIGINT      NOT NULL,
  equipment_id         BIGINT COMMENT '硬件维修/移机必填',
  software_instance_id BIGINT COMMENT '软件维修/装软件关联',
  type                 VARCHAR(24) NOT NULL COMMENT '五类 HARDWARE/SOFTWARE/INSTALL/RELOCATE/SOFTWARE_INSTALL',
  title                VARCHAR(128) NOT NULL,
  description          TEXT,
  photos               JSON COMMENT '故障图片/视频（维修类客户报修必传）',
  priority             VARCHAR(4)  NOT NULL DEFAULT 'P3',
  status               VARCHAR(16) NOT NULL DEFAULT 'PENDING_ASSIGN',
  assigned_engineer_id BIGINT,
  covered              TINYINT(1) COMMENT '在保快照（报修时点固化，计费以此为准）',
  contract_id          BIGINT COMMENT '在保时命中的合同（免费件清单按它查）',
  contact_name         VARCHAR(32), contact_phone VARCHAR(20),
  assigned_at DATETIME, started_at DATETIME, completed_at DATETIME,
  confirmed_at DATETIME, closed_at DATETIME,
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_tk_status (status), INDEX idx_tk_engineer (assigned_engineer_id), INDEX idx_tk_customer (customer_id)
) COMMENT '工单（核心）';

DROP TABLE IF EXISTS ticket_log;
CREATE TABLE ticket_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id     BIGINT      NOT NULL,
  from_status   VARCHAR(16),
  to_status     VARCHAR(16) NOT NULL,
  action        VARCHAR(32) NOT NULL,
  operator_id   BIGINT      NOT NULL,
  operator_role VARCHAR(16) NOT NULL,
  operator_name VARCHAR(32) COMMENT '冗余昵称，历史永远可读',
  remark        VARCHAR(255),
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_tl_ticket (ticket_id)
) COMMENT '工单流转记录（审计流水）';

DROP TABLE IF EXISTS spare_part_usage;
CREATE TABLE spare_part_usage (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_id      BIGINT NOT NULL,
  qty          INT    NOT NULL,
  engineer_id  BIGINT NOT NULL,
  ticket_id    BIGINT NOT NULL,
  equipment_id BIGINT,
  billable     TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否计费（合同免费件=0）',
  unit_price   DECIMAL(10,2) COMMENT '领用时单价快照（调价不影响历史账）',
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_spu_ticket (ticket_id), INDEX idx_spu_engineer (engineer_id)
) COMMENT '领料记录（含计费快照）';

DROP TABLE IF EXISTS ticket_charge;
CREATE TABLE ticket_charge (
  id        BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id BIGINT      NOT NULL,
  item_type VARCHAR(16) NOT NULL COMMENT 'VISIT上门费/PART配件费/LABOR维修费',
  item_name VARCHAR(128),
  amount    DECIMAL(12,2) NOT NULL,
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_tc_ticket (ticket_id)
) COMMENT '工单结算明细（不在保完工自动生成，M1新增）';

DROP TABLE IF EXISTS machine_stock;
CREATE TABLE machine_stock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  equipment_type VARCHAR(32) NOT NULL,
  model VARCHAR(64) NOT NULL UNIQUE,
  qty INT NOT NULL DEFAULT 0 COMMENT '可派发整机数',
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  CONSTRAINT chk_machine_qty CHECK (qty >= 0)
) COMMENT '整机库存（入=组装 出=订单派发）';

DROP TABLE IF EXISTS assembly_record;
CREATE TABLE assembly_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  machine_stock_id BIGINT NOT NULL,
  qty INT NOT NULL,
  remark VARCHAR(255),
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0'
) COMMENT '组装记录（配件→整机）';

DROP TABLE IF EXISTS assembly_part;
CREATE TABLE assembly_part (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  assembly_id BIGINT NOT NULL,
  part_id BIGINT NOT NULL,
  qty INT NOT NULL,
  unit_price DECIMAL(10,2) COMMENT '消耗时单价快照（组装成本）',
  INDEX idx_ap_assembly (assembly_id)
) COMMENT '组装消耗明细';

DROP TABLE IF EXISTS sales_order;
CREATE TABLE sales_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no VARCHAR(32) NOT NULL UNIQUE,
  customer_id BIGINT NOT NULL,
  order_type VARCHAR(16) NOT NULL COMMENT 'MACHINE/SOFTWARE',
  model VARCHAR(64) COMMENT '整机订单机型',
  software_name VARCHAR(64), software_version VARCHAR(32),
  qty INT NOT NULL DEFAULT 1,
  remark VARCHAR(255),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待派发/DISPATCHED已派发',
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_so_status (status)
) COMMENT '销售订单（超管录入，管理员派发）';

DROP TABLE IF EXISTS part_request;
CREATE TABLE part_request (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_id     BIGINT NOT NULL,
  qty         INT    NOT NULL,
  engineer_id BIGINT NOT NULL COMMENT '申请人',
  ticket_id   BIGINT COMMENT '关联工单(选填)',
  reason      VARCHAR(255) COMMENT '申请理由',
  status      VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  approve_remark VARCHAR(255) COMMENT '审批意见',
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_pr_engineer (engineer_id), INDEX idx_pr_status (status)
) COMMENT '配件申请（工程师申领→管理员审批→通过自动入库）';

DROP TABLE IF EXISTS invoice;
CREATE TABLE invoice (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  invoice_no  VARCHAR(32)  NOT NULL UNIQUE,
  customer_id BIGINT       NOT NULL,
  contract_id BIGINT,
  ticket_id   BIGINT COMMENT '按次维修的结算转开票时关联',
  title       VARCHAR(128) NOT NULL,
  amount      DECIMAL(12,2) NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED/PAID',
  issued_at   DATE, paid_at DATE,
  create_by BIGINT NULL, create_time DATETIME, update_by BIGINT NULL, update_time DATETIME,
  tenant_id BIGINT NOT NULL DEFAULT 1, del_flag CHAR(1) NOT NULL DEFAULT '0',
  INDEX idx_inv_customer (customer_id)
) COMMENT '发票（应收）';
