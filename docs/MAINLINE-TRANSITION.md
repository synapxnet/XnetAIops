<!-- Copyright (C) 2026 Synapxnet. All rights reserved.
Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0 | Security Level: INTERNAL
Maintainer: maoyo | Email: synapxnet@gmail.com -->
# AIOps 主线迁移与验收

主线合并以 `display` 的最新文档和完整 1.3.0 功能版本为基础，保留驻场 Agent、跨平台证据、审批、独立验证、资源版本、幂等与补偿。比赛发布分支、历史材料和既有审计记录保持可追溯；本次不部署线上服务、不清理线上数据库。

## 保留和退出范围

保留 CLM、HOM、SVM、MON、K8S、REG、USR 原生功能、三维总览、组织权限、驻场聊天/任务/模型配置、知识/Skill/工具能力工作台、跨域恢复与运行保障。内部 `com.synapxnet.goai` 包名和已发布的工具路径属于兼容契约，不按名称删除可复用功能。

默认产品不再创建比赛账号、团队、组织成员关系，不接受固定手机号白名单或固定验证码，不跳转固定比赛域名，不将未配置真实目标的模拟成功当作执行结果。通用角色继续初始化；账号、组织和实际平台地址由部署方配置。

验证码通过企业短信网关实际发送，Redis 原子脚本控制 60 秒间隔、300 秒有效期、最多 5 次错误和一次性消费。网关缺失、发送失败、身份停用和已注销的 JWT 均拒绝继续。全局用户与角色管理要求管理员身份；普通用户仅可修改自己的密码。

离线演练仍可显式设置 `OPENXNET_REHEARSAL_ENABLED=true`，默认关闭。证据来源必须为 `REAL_CPU_SYNTHETIC_STAGING + synthetic=true` 或 `PRODUCTION + synthetic=false`；未知来源、不一致组合、跨事件和跨工作空间证据被拒绝。来源标识不替代服务端授权和资源隔离。

## 数据库初始化与升级

1. 新环境仅在空数据库使用 `XnetAIops.sql`，随后在同一数据库执行 `devops-tables.sql`。主初始化文件包含建表语句，不能作为存量数据库的重复迁移执行。
2. 存量数据库先独立备份，再按实际表结构应用 `database/migrations/20261023-mainline-organization-access.sql`。组织成员的 `data_access_enabled` 默认为 `0`，管理员须明确授予团队数据访问权；升级不自动扩权。该迁移可重复执行。
3. 首个真实管理员由有数据库管理权限的部署人员初始化，设置唯一 UID、真实手机号、`user_type=admin` 和 `status=active`；密码字段应保存新生成的 BCrypt 摘要。配置短信网关后使用实际短信登录，随后在用户与角色管理中创建其他账号。没有公共管理员、固定登录码或匿名初始化接口。
4. 存量身份清理是可选维护，须先完成组织访问字段迁移，再使用 `database/maintenance/archive-competition-identities.sql` 预览。

归档脚本默认只预览，不随应用启动执行。候选同时匹配旧种子 UID、用户名、手机号和原始密码摘要；改过密码的真实账号不会命中。显式确认应用后，先归档匹配记录再撤销其会话/权限并停用账号。已被其他成员使用的团队、账号审计主体、任务、审批、资源、通用角色和租户/部门保留；归档冲突阻止变更。脚本仅在隔离本机测试库执行过，没有对存量线上库应用。

## 本轮发现并修复的问题

| 严重度 | 复现入口 | 修复前行为与风险 | 修复后验收 |
| --- | --- | --- | --- |
| 高 | 未登录或普通用户调用用户/角色管理接口 | 缺少统一管理员校验，可能越权管理身份 | 无身份 401、普通用户 403、注销后旧令牌拒绝；真实 HTTP 与过滤器测试通过 |
| 高 | 新 MySQL 8 库运行 DevOps 表扩展 | `ADD COLUMN IF NOT EXISTS` 语法不受支持，初始化中断 | 使用 information_schema 判断及准备语句；初始化与重复执行验证 |
| 中 | 登录后读取组织树 | Mapper 读取不存在的 `data_access_enabled` 字段 | 初始化和幂等迁移补齐，默认不授权；真实组织树读取通过 |
| 中 | 集群管理创建后重新读取 | CLM 下划线映射关闭，名称/编码读取为空 | 启用正确映射；真实创建、读取、修改、变量写入/删除及集群删除通过 |
| 中 | Jenkins 节点/密钥入口 | 前端路径与原生后端不一致 | 对齐已存在的原生路径；静态方法/路径契约核对通过 |
| 中 | 流水线重放、查看日志 | 缺少重放端点；前端未解包日志对象 | 补齐同工程、同流水线、已结束 Run 的重放；原始参数校验；前端显示实际日志字段 |
| 中 | 手动归档旧种子身份 | MySQL 同一语句多次打开临时表报错 | 拆分顺序校验并保持全部条件通过后才变更；真实 MySQL 归档边界测试通过 |

## 可复核的验证结果

验证日期：2026-10-23。所有数据库与 HTTP 写入均限于随机命名的本机测试 schema，使用专用 Redis 和回环短信接收器，没有真实短信发送、线上模型或生产基础设施操作。

- `mvn -B package -Dmainline.integration=true -Dmainline.dependencies.file=<private-config>`：176 项测试，173 通过、3 跳过、0 失败/错误；7 个服务和共享契约包均构建成功。
- `MainlineDependencyIntegrationTest`：真实 Redis 验证限流/过期/尝试次数/一次性消费；真实 MySQL 执行归档预览和应用，检查同手机号真实身份、共用团队、修改密码和归档冲突边界。
- `tests/mainline-http-smoke.py`：40 项全部通过。真实打包 JAR、本机 MySQL/Redis 和短信接收器覆盖数据库迁移及重复执行、登录、旧固定码拒绝、码复用拒绝、身份/权限、用户/角色读取、组织树、集群 CRUD/变量、7 服务列表读取和注销；逐项结果见 `tests/mainline-http-results.json`。
- 前端配套的 93 页面/279 状态检查、6 项交互和 417 项接口路径核对见前端 `docs/MAINLINE-TRANSITION.md`、`docs/MAINLINE-PAGE-MATRIX.md`。

3 项跳过来自既有历史迁移回归，要求未提供的实际迁移导出及两个固定摘要的冻结快照。未伪造历史输入，也未把跳过项算作通过。常规生成状态的恢复、审批、幂等和补偿回归已通过。

Kubernetes/SSH/Jenkins/Harbor/Helm、真实模型、实际短信厂商和独立部署的驻场服务需要各自真实连接配置。本地通过的基础 CRUD、页面容错和契约检查不等同于这些外部设施的端到端执行验收。
