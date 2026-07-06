# FIX-ING · 通用设备维保平台 — Demo v0 后端

> 开源、可配置的通用设备售后/维保管理 SaaS。平台不绑定行业：**医院 = 客户的一种，叫号机 = 设备的一种**。
> 首个示范场景：医院叫号设备维保。
>
> **v0.1 目标（垂直切片）**：一条工单打穿全栈 —— `客户报修 → 管理员派单 → 工程师接单 → 换件扣库存 → 完工 → 客户确认`，全程可追溯。

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 框架 | Spring Boot 3.5（单模块） | v0 故意不上若依/多模块，先把三层架构亲手过一遍 |
| 持久层 | MyBatis-Plus 3.5 + MySQL | `map-underscore-to-camel-case` 必开 |
| 认证 | JWT (jjwt) + BCrypt + 自写拦截器 | 刻意不上完整 Spring Security，整条鉴权链可读可懂 |
| 前端 | 纯静态 HTML + 原生 JS | 放 `resources/static/`，Spring Boot 直接托管，同源零跨域零构建；v1 再换 Vue/React |
| 构建 | Maven，Java 17+ | |

## 快速开始

```bash
# 1. 建库建表 + 灌演示数据（1客户/2设备/3备件/3用户）
mysql -uroot -p < sql/schema.sql
mysql -uroot -p < sql/seed.sql

# 2. 改 src/main/resources/application.yml 里的数据库密码，然后启动
mvn spring-boot:run

# 3. 验证
curl http://localhost:8080/ping   # → ok

# 4. 打开网页前端：先登录（三个演示账号一键填入），再进看板/报修/库存/台账
open http://localhost:8080/login.html

# 5. 或者用脚本一键跑完整演示链（含反面用例）
bash scripts/demo-flow.sh
```

演示账号（seed 数据，密码均为 `123456`，库里存 BCrypt 散列）：

| 用户名 | 角色 | 说明 |
|---|---|---|
| hospital_it | CUSTOMER | 医院信息科（报修/确认/驳回） |
| admin | ADMIN | 平台管理员（派单/改派/取消） |
| engineer_zh | ENGINEER | 硬件工程师（接单/换件/完工） |

## 认证（v0.2）

`POST /auth/login {username, password}` → 返回 JWT；之后所有业务接口都要带
`Authorization: Bearer <token>` 请求头，缺失/过期/伪造一律 401。

- 操作人身份一律从令牌认定（请求体里没有 operatorId）——"我是谁"客户端说了不算
- 密码只存 BCrypt 散列；登录失败不区分"用户名错还是密码错"
- 鉴权链路：`AuthInterceptor`(验签+查用户) → `UserContext`(ThreadLocal) → Service 取操作人
- JWT 密钥从环境变量 `JWT_SECRET` 注入（本地有开发默认值）

## 工单状态机（系统的灵魂）

```
待派单 ──派单──▶ 已派单 ──接单──▶ 处理中 ──完工──▶ 待确认 ──确认──▶ 已完成
  │               │ ▲                │ ▲               │
  │               │ └───── 改派 ─────┘ └──── 驳回 ──────┘
  └──取消──▶ 已取消 ◀──取消(管理员)──┘
```

- 合法跳转集中定义在 `TicketStatus.TRANSITIONS`，非法跳转一律拒绝（状态机就是工单的法律）。
- 每次状态变更写一条 `ticket_log`（谁/何时/从哪到哪/为什么），审计与进度查询都靠它。
- 换件只允许在"处理中"，扣库存用原子 SQL（`WHERE stock_qty >= qty`），与领料流水同事务。

## API 一览

| 方法 | 路径 | 谁能调 | 说明 |
|---|---|---|---|
| POST | `/auth/login` | 公开 | 登录换 JWT（唯一不需要令牌的业务接口） |
| GET | `/auth/me` | 已登录 | 当前用户信息（前端刷新恢复登录态） |
| POST | `/tickets` | 客户/管理员 | 提交报修（优先级由规则自动判定） |
| GET | `/tickets/{id}` · `/tickets/{id}/logs` | 所有人 | 详情 / 流转时间线 |
| GET | `/tickets?status=&priority=&engineerId=&customerId=` | 所有人 | 列表筛选 |
| POST | `/tickets/{id}/assign` | 管理员 | 派单 `{engineerId}` |
| POST | `/tickets/{id}/reassign` | 管理员 | 改派/超时重派 |
| POST | `/tickets/{id}/accept` | 被派工程师 | 接单 |
| POST | `/tickets/{id}/use-part` | 责任工程师 | 换件扣库存 `{partId, qty}` |
| POST | `/tickets/{id}/complete` | 责任工程师 | 完工提交 |
| POST | `/tickets/{id}/confirm` / `reject` | 客户 | 确认 / 驳回返工 |
| POST | `/tickets/{id}/cancel` | 客户(仅待派单)/管理员 | 取消 |
| CRUD | `/customers` `/equipments` `/spare-parts` | — | 台账与库存 |
| GET | `/equipments/{id}/tickets` | — | 设备维修历史 |
| GET | `/part-usages?ticketId=&engineerId=` | — | 领料流水 |

统一响应：`{ "code": 0|1, "message": "...", "data": ... }`（0 成功，1 业务失败）。

## 项目结构

```
com.fixing
 ├─ common/      统一返回 Result、业务异常、全局异常处理
 ├─ auth/        登录认证：JwtUtil / AuthInterceptor / UserContext / AuthController
 ├─ user/        用户与角色（一个 role 字段的简版 RBAC）
 ├─ customer/    客户台账（M2）
 ├─ equipment/   设备台账 + 维修历史（M2）
 ├─ inventory/   备件库存 + 领料流水（M8）：原子扣减
 └─ ticket/      工单中心（M4 核心）：状态机 / 动作接口 / 流转日志
     └─ priority/  PriorityDecider 接口 + 规则实现（将来换 AI，业务不改）

resources/static/   网页前端（index.html + css + js，无框架）
 · 工单看板：六状态分列，状态分布一目了然（Demo 验收点）
 · 详情弹窗：流转时间线 + 按"身份×状态"显示动作按钮
 · 前端只是少画几个按钮 —— 权限与状态机的真校验全在后端
```

## 三条贯穿设计心法

1. **抽象稳定核心**：`customer`/`equipment` 不写死 `hospital`/`叫号机`，换行业不改骨架。
2. **给易变决策留一扇门**：优先级判定是 `PriorityDecider` 接口，v0 塞关键词规则，将来换 AI（M13）只加实现类。
3. **想统计先记细**：每次领料记全"谁/哪张单/哪台设备/几个"，用量统计、成本核算都是顺手聚合。

## 路线图

- **v0.1**：工单全状态机 + 台账 + 换件扣库存 + 规则优先级 + 网页前端 ✅
- **v0.2（当前）**：登录认证（JWT + BCrypt + 拦截器），操作人从登录态认定 ✅
- v0.3：SLA 超时标记与预警、合同管理（M3）、通知 mock
- v1：可配置字典与自定义字段（M14）、Redis、迁移多模块
- v2+：报表看板、账款、AI 接入（智能报修/派单建议）、多租户

## 已知取舍（Demo 阶段故意不做）

实体直接当响应返回 · 无分页 · 通知仅打日志 · 单租户 · JWT 无服务端吊销（登出=前端删令牌）。均在代码注释中标注了演进方向。
