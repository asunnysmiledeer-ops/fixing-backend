# FIX-ING · 通用设备维保平台

> 开源、可配置的通用设备售后/维保管理系统。平台不绑定行业：**医院 = 客户的一种，叫号机 = 设备的一种**。
> 参考中州养老（若依）的架构思想，运行在现代技术栈上：Spring Boot 3.5 / Java 17 / MyBatis-Plus / MySQL / Redis / **Vue 3 + Element Plus**。

> 📦 **想直接部署？** 看 [`docs/部署安装教程.md`](docs/部署安装教程.md)，或从 [Releases](../../releases) 下载 `fixing-vX.X-deploy.zip` 开箱即用（jar + SQL + 启动脚本）。

## 架构（Maven 多模块单体）

```
common ← system ← framework ← { maint-platform, admin }

fixing-common          统一返回 Result / 业务异常 / BaseEntity(审计+租户+软删)
fixing-system          用户与角色 / sys_role_perm 权限映射 / 平台域(功能开关/参数/字典/日志)
fixing-framework       JWT / 鉴权拦截器 / @RequirePerm / Redis令牌黑名单 / 文件上传 / 全局异常
fixing-maint-platform  全部维保业务：工单·台账·库存·合同·发票·看板·订单（按业务分包）
fixing-admin           启动类 + 系统级 Controller + 配置 + 前端构建产物(static/)
fixing-ui              Vue 3 + Vite + Element Plus 前端源码（四端共用单 SPA，构建产物打进 admin）
```

## 核心能力

- **五类工单**：硬件/软件维修（客户报修必传故障图/视频）+ 添加机器/移动机器/安装软件（服务申请），共用一套状态机：`待派单→已派单→处理中→待确认→已完成`（含改派/驳回/取消）
- **合同颗粒化绑定**：合同精确绑定"保哪几台设备(contract_equipment)、哪些备件免费换(contract_part)、保哪些软件(contract_software)"；报修时点固化在保快照
- **按次计费**：不在保工单完工自动生成结算单 = 上门费(标准可配) + 配件费(领料×单价快照，免费件剔除) + 维修费(工程师报价或默认)；可转开发票
- **库存动态阈值**：预警阈值 = max(人工阈值, 每台备货数 × 在保签约设备数)，签约变化自动调整
- **权限字符串化（RBAC）**：`@RequirePerm("maint:ticket:assign")` + sys_role_perm 表；前端页签由登录返回的 perms 驱动 —— **改权限=改数据库，不改代码**
- **真实项目硬化**：全表审计四件套(自动填充)/tenant_id 预留/del_flag 软删除；Redis 令牌黑名单（登出真退出）；合同到期 7 天提醒(横幅+每日 9 点任务)；到期后报修不拦截、转按次收费

## 快速开始（从源码本机跑）

```bash
# 依赖：JDK 17+ / MySQL / Redis（brew services start mysql redis）
mysql -uroot -p < sql/schema.sql && mysql -uroot -p < sql/seed.sql
mvn -DskipTests package                    # 已含前端构建产物，无需单独装 Node
java -jar fixing-admin/target/fixing-admin-0.3.0.jar
open http://localhost:8080                 # 接口文档: /swagger-ui.html
```

> 改前端（fixing-ui）时才需要 Node：`cd fixing-ui && npm install && npm run dev`（开发用 /api 代理到 8080），
> 或 `npm run build` 把产物打进 admin 后重新 `mvn package`。
> **一键部署包**（不用编译）见 [`docs/部署安装教程.md`](docs/部署安装教程.md)。

演示账号（密码均 `123456`）：

| 账号 | 角色 | 数据故事线 |
|---|---|---|
| super | 平台超管 | 人事/权限矩阵/功能开关/字典/操作日志/经营总览 |
| admin | 运营管理员 | 看板/工单派单/库存/台账/合同/发票/订单派发 |
| engineer_zh / engineer_li | 工程师 | 只看自己的派单；换件自动判免费/计费；申请配件 |
| hospital_it | 客户·市一医院 | 合同将到期(演示提醒)；只保叫号主机+打印头免费；**叫号屏未纳保→按次收费** |
| clinic_it | 客户·康泰诊所 | 验证数据隔离（互相看不见工单） |

## 技术要点（学习备注）

- 密码 BCrypt / JWT 无状态 + Redis 黑名单吊销 / ThreadLocal UserContext（afterCompletion 必清理）
- 原子扣库存 `UPDATE ... WHERE stock_qty >= ?`（数据库就是锁，不需要分布式锁）
- 金额三原则：BigDecimal / 单价存快照 / 在保判定报修时点固化
- MyBatis-Plus：@TableLogic 软删、MetaObjectHandler 审计自动填充、JSON TypeHandler(autoResultMap)
- 前端：Vue 3 + Pinia + Element Plus，四端共用单 SPA，菜单/按钮由登录 perms 驱动；Element Plus CSS 变量覆盖为苹果风 tokens（fixing-ui/src/styles/apple.css）

## v0.3 新增（全端 Vue3 换装 + 一键部署）

- **前端全部 Vue3 化**（fixing-ui）：Vite + Element Plus + Pinia + Vue Router；四端（客户/工程师/管理员/超管）共用一个 SPA，菜单由权限驱动；苹果风主题定制；构建产物打进 admin，仍是单 jar 部署
- **一键部署**：配置全面环境变量化（DB/Redis/端口/密钥可覆盖，不改包）；Release 附 `deploy.zip`（jar + SQL + 启动脚本），配 [部署安装教程](docs/部署安装教程.md)

## v0.2（平台管理端 + 订单供货链）

- **平台管理端**（超管 `super/123456`，最大权限）：人事管理（账号CRUD/停用即刻踢出/重置密码/**驻场工程师**配置）· **权限矩阵**（勾选即生效）· 配置中心（**功能开关**/业务参数/**数据字典**）· 业务追踪（操作日志自动记录 + 经营总览）
- **设备档案与查询**：按序列号/**运送时间**/类型筛选；档案含适用部件/已装软件/**维修次数与原因**/换件历史；工程师在工单里一键查看整机信息
- **订单→组装→派发**：超管录销售订单 → 管理员**组装**（配件→整机，消耗留痕）→ **派发**（扣整机库存、生成客户设备档案、**自动创建安装工单**）；管理员职责修正为入库审批（申请配件归工程师）

## 路线图

- **M1（当前）**：多模块 + 权限字符串 + 五类工单 + 颗粒化合同/按次计费 + 动态阈值 + 苹果风轻端 ✅
- M2：管理端 Vue3 + Element Plus（苹果风主题定制）
- M3：sys_dict 字典配置（M14 第一步）/ 通知模板 / 操作日志 AOP / 分页规范
- M4：部署加固（Docker/备份/限流）、多租户逻辑、微信/短信真实对接
