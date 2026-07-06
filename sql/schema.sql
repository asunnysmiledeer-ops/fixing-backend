-- ============================================================
-- FIX-ING Demo · 建库建表脚本（v0.3）
-- 心法：约束放数据层 —— NOT NULL / UNIQUE / DEFAULT 能表达的，
--       绝不只靠 Java 代码校验（注释不是约束）
-- v0.3 新增：sys_user.customer_id（客户账号归属）、ticket.photos（报修图片/视频）、
--           spare_part.low_stock_threshold（低库存阈值）、contract 合同、invoice 发票
-- ============================================================

CREATE DATABASE IF NOT EXISTS fixing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fixing;

-- 1. 系统用户
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(32)  NOT NULL UNIQUE,
  password    VARCHAR(64)  NOT NULL,                 -- BCrypt 散列（60字符），永不存明文
  role        VARCHAR(16)  NOT NULL,                 -- CUSTOMER / ADMIN / ENGINEER
  real_name   VARCHAR(32),
  customer_id BIGINT,                                -- 客户角色必填：这个账号属于哪家客户（数据隔离的根）
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '系统用户';

-- 2. 客户：医院只是客户的一种（customer_type 不写死行业）
CREATE TABLE IF NOT EXISTS customer (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(64)  NOT NULL,
  customer_type VARCHAR(32)  NOT NULL DEFAULT 'HOSPITAL',
  contact_name  VARCHAR(32),
  contact_phone VARCHAR(20),
  address       VARCHAR(255),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '客户台账';

-- 3. 设备
CREATE TABLE IF NOT EXISTS equipment (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id    BIGINT       NOT NULL,
  equipment_type VARCHAR(32)  NOT NULL,
  model          VARCHAR(64),
  serial_no      VARCHAR(64)  NOT NULL UNIQUE,
  location       VARCHAR(128),
  status         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_equipment_customer (customer_id)
) COMMENT '设备台账';

-- 4. 备件：v0.3 加低库存阈值（余量 < 阈值 → 预警）
CREATE TABLE IF NOT EXISTS spare_part (
  id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
  name                VARCHAR(64)  NOT NULL,
  category            VARCHAR(16)  NOT NULL,          -- PART/COMPONENT/CONSUMABLE
  stock_qty           INT          NOT NULL DEFAULT 0,
  low_stock_threshold INT          NOT NULL DEFAULT 5, -- 低于此值触发预警（每种备件可单独设）
  unit_price          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_stock_non_negative CHECK (stock_qty >= 0)
) COMMENT '备件库存';

-- 5. 工单（核心表）：v0.3 加 photos（报修必传的故障图片/视频，JSON 数组存 URL）
CREATE TABLE IF NOT EXISTS ticket (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_no            VARCHAR(32) NOT NULL UNIQUE,
  customer_id          BIGINT      NOT NULL,
  equipment_id         BIGINT,
  type                 VARCHAR(16) NOT NULL,
  title                VARCHAR(128) NOT NULL,
  description          TEXT,
  photos               JSON,                          -- ["/files/xxx.jpg", "/files/yyy.mp4"]
  priority             VARCHAR(4)  NOT NULL DEFAULT 'P3',
  status               VARCHAR(16) NOT NULL DEFAULT 'PENDING_ASSIGN',
  assigned_engineer_id BIGINT,
  contact_name         VARCHAR(32),
  contact_phone        VARCHAR(20),
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  assigned_at  DATETIME,
  started_at   DATETIME,
  completed_at DATETIME,
  confirmed_at DATETIME,
  closed_at    DATETIME,
  INDEX idx_ticket_status   (status),
  INDEX idx_ticket_engineer (assigned_engineer_id),
  INDEX idx_ticket_customer (customer_id)
) COMMENT '工单（核心）';

-- 6. 工单流转记录
CREATE TABLE IF NOT EXISTS ticket_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id     BIGINT      NOT NULL,
  from_status   VARCHAR(16),
  to_status     VARCHAR(16) NOT NULL,
  action        VARCHAR(32) NOT NULL,
  operator_id   BIGINT      NOT NULL,
  operator_role VARCHAR(16) NOT NULL,
  remark        VARCHAR(255),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_log_ticket (ticket_id)
) COMMENT '工单流转记录（审计）';

-- 7. 领料记录
CREATE TABLE IF NOT EXISTS spare_part_usage (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_id      BIGINT NOT NULL,
  qty          INT    NOT NULL,
  engineer_id  BIGINT NOT NULL,
  ticket_id    BIGINT NOT NULL,
  equipment_id BIGINT,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_usage_ticket   (ticket_id),
  INDEX idx_usage_engineer (engineer_id)
) COMMENT '领料记录';

-- 8. 合同（M3 最小版）：范围/起止/计费方式，管理端专用
CREATE TABLE IF NOT EXISTS contract (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id  BIGINT       NOT NULL,
  name         VARCHAR(128) NOT NULL,                -- 合同名，如 "2026年度叫号系统维保合同"
  scope        VARCHAR(255),                         -- 服务范围描述
  start_date   DATE NOT NULL,
  end_date     DATE NOT NULL,                        -- 到期提醒按它算（30天内→即将到期）
  billing_type VARCHAR(16) NOT NULL DEFAULT 'YEARLY',-- YEARLY 年费 / PER_CASE 按次 / MIXED 混合
  amount       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',-- ACTIVE / TERMINATED
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_contract_customer (customer_id)
) COMMENT '维保合同';

-- 9. 发票（M9 应收最小版）：管理端开票与回款跟踪
CREATE TABLE IF NOT EXISTS invoice (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  invoice_no  VARCHAR(32)  NOT NULL UNIQUE,          -- 发票号
  customer_id BIGINT       NOT NULL,
  contract_id BIGINT,                                -- 可关联合同（年费票）；合同外维修可为空
  title       VARCHAR(128) NOT NULL,                 -- 开票项目
  amount      DECIMAL(12,2) NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'ISSUED', -- ISSUED 已开票 / PAID 已回款
  issued_at   DATE,
  paid_at     DATE,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_invoice_customer (customer_id)
) COMMENT '发票（应收）';
