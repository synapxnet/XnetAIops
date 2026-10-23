<!-- Copyright (C) 2026 Synapxnet. All rights reserved.
Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0 | Security Level: INTERNAL -->
# XnetAIOps

[English](README.en-US.md) · [日本語](README.ja-JP.md)

XnetAIOps 提供基础设施运维与受控 Agent 执行能力。主线保留集群、主机、服务、监控、Kubernetes、制品仓库、组织权限，以及驻场 Agent、跨平台证据、审批、独立验证和恢复能力。

主线开发分支为 `main`；历史发布版本继续保留独立分支和标签。

## 服务模块

| 模块 | 职责 |
| --- | --- |
| CLM / HOM | 集群、主机、机架与基础组件生命周期 |
| SVM / MON | 服务操作、命令、监控与告警 |
| K8S / REG | Kubernetes、Helm、流水线、镜像和制品 |
| USR | 身份、角色、组织与登录验证 |
| Agent contract | 委托鉴权、任务证据、审批绑定、资源版本、幂等与补偿 |

驻场 Agent 与 AgentTeams 按独立服务配置接入。OpenXnet 负责跨平台协同；本平台保留自身运维入口与执行边界。

## 构建和测试

需要 JDK 17、Maven 3.9，以及运行时的 MySQL 8 和 Redis 7。

```bash
mvn -B test
mvn -B -DskipTests package
```

各服务产物位于 `target/`。通用 Compose 运行原生平台服务，驻场 Agent、AgentTeams、审批和执行运行时需按部署环境另行配置。

## 配置与登录

1. 从 `.env.example` 创建部署配置，设置数据库、Redis、`JWT_SECRET`、`K8S_ENCRYPTION_KEY` 等必需值。
2. 空数据库执行 `XnetAIops.sql`，随后在同一数据库执行 `devops-tables.sql`。存量库先备份，再按需执行 `database/migrations/20261023-mainline-organization-access.sql`；不要向存量库重复执行完整建表文件。组织数据访问默认关闭，需管理员明确授权。
3. 首次安装由有数据库权限的部署管理员创建首个真实管理员，填写唯一 UID、真实手机号、新 BCrypt 密码摘要，并设置 `user_type=admin`、`status=active`；登录后再管理其他用户与角色。登录不提供公共账号、固定验证码或匿名初始化接口。组织、部门、团队与成员关系须按实际企业建立。
4. 设置 `XNET_SMS_GATEWAY_URL` 与 `XNET_SMS_GATEWAY_TOKEN`。网关接受带 Bearer 鉴权的 POST JSON `{phone, code, purpose: "login", expiresInSeconds: 300}`，以 HTTP 2xx 和 `{"accepted": true}` 确认送达。
5. 将 `WEB_DIST_PATH` 指向配套 Web 构建目录，再检查 Compose 配置与启动状态。

登录码 5 分钟过期、60 秒发送间隔、最多 5 次错误，成功后一次性消费；未配置短信网关则明确拒绝发送。停用账号不能通过旧 JWT 获取身份或权限。

## 执行与证据

工具调用继续受身份、工作空间、审批、资源版本与幂等约束。生产证据为 `PRODUCTION` 且 `synthetic=false`；隔离演练来源明确标识。`OPENXNET_REHEARSAL_ENABLED` 默认关闭，未接入真实执行目标时不会返回模拟成功。

存量预置身份可通过 [归档维护脚本](database/maintenance/archive-competition-identities.sql) 先预览，独立备份后再维护。脚本保留业务记录和审计主体，不按手机号或名称批量删除数据。

[主线迁移说明](docs/MAINLINE-TRANSITION.md) · [前端仓库](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet](https://openxnet.synapxnet.com)

## 许可

参见 [LICENSE](LICENSE)。文件中的版权和授权声明同样适用。
