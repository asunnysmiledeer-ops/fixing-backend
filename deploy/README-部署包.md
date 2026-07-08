# FIX-ING 部署包 · 快速开始

这个压缩包解开就能部署。**5 分钟跑起来：**

```
fixing-v0.3-deploy/
├── fixing-admin-0.3.0.jar   ← 程序本体（前端已打包在内）
├── start.sh / start.bat     ← 启动脚本（改密码后运行）
├── sql/
│   ├── schema.sql           ← 建库建表
│   └── seed.sql             ← 初始账号 + 演示数据
└── README-部署包.md          ← 本文件
```

## 三步启动

```bash
# 1) 装依赖（若已有可跳过）：JDK 17+ / MySQL / Redis
#    详见完整教程 docs/部署安装教程.md

# 2) 初始化数据库（首次部署执行一次）
mysql -u root -p < sql/schema.sql
mysql -u root -p < sql/seed.sql

# 3) 改 start.sh 里的 DB_PASSWORD，然后启动
bash start.sh          # Windows 双击 start.bat
```

打开 http://localhost:8080 ，用 `super / 123456` 登录（平台超管）。

## 演示账号（密码都是 123456）

| 账号 | 角色 |
|------|------|
| `super` | 平台超管 |
| `admin` | 运营管理员 |
| `engineer_zh` | 工程师 |
| `hospital_it` | 客户（医院） |

> ⚠️ **上线前**：登录 super → 平台·人事 → 改密码/建真实账号/停用演示账号；
> 并在 start.sh 里把 `JWT_SECRET` 换成你自己的随机长串。

完整教程（环境安装、后台常驻、systemd、排错）见仓库 `docs/部署安装教程.md`。
