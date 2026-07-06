-- ============================================================
-- FIX-ING Demo v0 · 建库建表脚本
-- 七张表，按"被依赖的先建"顺序（demo-v0-最小开发清单 §2）
-- 心法：约束放数据层 —— NOT NULL / UNIQUE / DEFAULT 能表达的，
--       绝不只靠 Java 代码校验（注释不是约束，Java 校验挡不住绕过应用的写入）
-- ============================================================

CREATE DATABASE IF NOT EXISTS fixing DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fixing;

-- 1. 系统用户：v0 一个 role 字段代替完整 RBAC
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(32)  NOT NULL UNIQUE,
  password    VARCHAR(64)  NOT NULL,                 -- BCrypt 散列（60字符），永不存明文
  role        VARCHAR(16)  NOT NULL,                 -- CUSTOMER / ADMIN / ENGINEER
  real_name   VARCHAR(32),
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '系统用户（v0 简版，无登录，靠 operatorId 标识操作人）';

-- 2. 客户：医院只是客户的一种（customer_type 不写死行业）
CREATE TABLE IF NOT EXISTS customer (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  name          VARCHAR(64)  NOT NULL,
  customer_type VARCHAR(32)  NOT NULL DEFAULT 'HOSPITAL',  -- v1 变成可配置字典
  contact_name  VARCHAR(32),
  contact_phone VARCHAR(20),
  address       VARCHAR(255),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) COMMENT '客户台账';

-- 3. 设备：叫号机只是设备的一种（equipment_type 不写死）
CREATE TABLE IF NOT EXISTS equipment (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id    BIGINT       NOT NULL,              -- FK → customer.id
  equipment_type VARCHAR(32)  NOT NULL,
  model          VARCHAR(64),
  serial_no      VARCHAR(64)  NOT NULL UNIQUE,       -- 设备"身份证"，扫码报修的定位键
  location       VARCHAR(128),
  status         VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',   -- NORMAL/FAULT/SCRAPPED
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_equipment_customer (customer_id)         -- 常按客户查设备列表
) COMMENT '设备台账';

-- 4. 备件：分类库存
CREATE TABLE IF NOT EXISTS spare_part (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  name       VARCHAR(64)  NOT NULL,
  category   VARCHAR(16)  NOT NULL,                  -- PART配件/COMPONENT部件/CONSUMABLE耗材
  stock_qty  INT          NOT NULL DEFAULT 0,
  unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,    -- 金额用 DECIMAL，绝不用 FLOAT/DOUBLE
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_stock_non_negative CHECK (stock_qty >= 0)  -- 数据库兜底：库存不可为负
) COMMENT '备件库存';

-- 5. 工单（核心表）
CREATE TABLE IF NOT EXISTS ticket (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_no            VARCHAR(32) NOT NULL UNIQUE,   -- 业务单号，UNIQUE 兜底并发撞号
  customer_id          BIGINT      NOT NULL,          -- FK → customer.id
  equipment_id         BIGINT,                        -- FK → equipment.id（硬件单必填，应用层校验）
  type                 VARCHAR(16) NOT NULL,          -- HARDWARE / SOFTWARE
  title                VARCHAR(128) NOT NULL,
  description          TEXT,
  priority             VARCHAR(4)  NOT NULL DEFAULT 'P3',            -- P0..P4
  status               VARCHAR(16) NOT NULL DEFAULT 'PENDING_ASSIGN',-- 见状态机
  assigned_engineer_id BIGINT,                        -- FK → sys_user.id
  contact_name         VARCHAR(32),
  contact_phone        VARCHAR(20),
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  assigned_at  DATETIME,
  started_at   DATETIME,
  completed_at DATETIME,
  confirmed_at DATETIME,
  closed_at    DATETIME,
  INDEX idx_ticket_status   (status),                 -- 看板按状态聚合
  INDEX idx_ticket_engineer (assigned_engineer_id),   -- 工程师查"我的工单"
  INDEX idx_ticket_customer (customer_id)             -- 客户查自己的工单
) COMMENT '工单（核心）';

-- 6. 工单流转记录：审计流水，只增不改
CREATE TABLE IF NOT EXISTS ticket_log (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  ticket_id     BIGINT      NOT NULL,                 -- FK → ticket.id
  from_status   VARCHAR(16),                          -- 新建时为 NULL
  to_status     VARCHAR(16) NOT NULL,
  action        VARCHAR(32) NOT NULL,                 -- create/assign/accept/...
  operator_id   BIGINT      NOT NULL,
  operator_role VARCHAR(16) NOT NULL,
  remark        VARCHAR(255),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_log_ticket (ticket_id)                    -- 查某工单的时间线
) COMMENT '工单流转记录（审计）';

-- 7. 领料记录：用量统计与成本核算的根（想统计先记细）
CREATE TABLE IF NOT EXISTS spare_part_usage (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  part_id      BIGINT NOT NULL,                       -- FK → spare_part.id
  qty          INT    NOT NULL,
  engineer_id  BIGINT NOT NULL,                       -- FK → sys_user.id
  ticket_id    BIGINT NOT NULL,                       -- FK → ticket.id
  equipment_id BIGINT,                                -- FK → equipment.id
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_usage_ticket   (ticket_id),
  INDEX idx_usage_engineer (engineer_id)
) COMMENT '领料记录';
