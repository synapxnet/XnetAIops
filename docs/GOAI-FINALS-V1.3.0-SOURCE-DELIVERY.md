<!-- Copyright (C) 2026 Synapxnet. All rights reserved.
This file is Synapxnet Proprietary and Confidential. It is strictly forbidden to copy,
distribute, or use without explicit authorization.
用途：记录决赛源码组合、构建和真实部署边界。 Purpose: Record finals source integration, builds and deployment boundaries.
Author: maoyo | Department: 研发部 | Date: 2026-09-17 | Version: 1.3.0
Security Level: INTERNAL | Maintainer: maoyo | Email: synapxnet@gmail.com -->

# XnetAIOps GOAI V1.3.0 源码交付

本提交从 GitHub `GOAI-Competition` 现有历史继续，产品与 Maven parent/module 构建版本为 `1.3.0`。接口 v1、快照 schemaVersion=1 与历史部署制品摘要保持原语义；本次提交不代表线上服务已重新部署。

## 源码组成

| 来源 | 纳入范围 |
| --- | --- |
| `goai-finals-aiops/backend` 优化基线 | 完整原生业务模块、组织权限、运行保障读取、终端授权、UI reader、真实 Workload 证据、资源数值及规格单位修复 |
| `aiops-governed-persistence-candidate/source` | 审批摘要验证、规范资源版本和幂等 tracker、推理治理 Controller、迁移、持久检查点及回归测试 |
| 原平台驻场声明 | `docs/resident-agent-v1.3.0.yaml`，仅含环境变量与凭据引用，无真实凭据 |

公共契约保留较新的读取配置，并补入 `GovernedApprovalVerifier` Bean；UI reader 或 operations-only 模式下不注册推理治理 Controller，避免只读实例初始化或消费执行状态。此边界新增 Spring 上下文回归测试。

原生 Workload 读取以实际 Kubernetes 资源为准。保留较新实现对缺失资源的明确失败、无 selector 时不扩大 Pod 范围，以及未接入精确监控数据时返回缺失值的行为；不恢复较旧候选中的沙盘快照降级。

## 构建

使用 JDK 17 与 Maven 3.9：

```sh
mvn -B clean test
mvn -B -DskipTests package
```

Dockerfile 使用 `target/<module>-1.3.0.jar`。Compose 的默认镜像标签为 `1.3.0`，前端资源路径指向 `XnetAIops-web/apps/web-antd/dist`。数据库口令、Kubernetes 加密密钥和委托凭据须由部署环境提供。

## 服务职责及配置

- 原生 Kubernetes reader：`OPENXNET_K8S_UI_READER=true`；其 HTTP、WebSocket 与数据库拒写保护保持。它不提供治理写接口。
- 独立运行保障读取服务：`OPENXNET_OPERATIONS_READ_ONLY_SERVICE=true`；只允许经过组织与资源授权的指定证据路径。
- 独立治理实例：上面两个只读选项均关闭；明确配置委托密钥、审批服务地址/内部凭据，按既有规范提供 `goai.resource-state-directory`、经审核 bootstrap 文件及 SHA-256。不得删除 consumed 标记或重放过期快照来绕过恢复检查。
- 三种实例通过部署路由隔离。现有生产 Nginx、数据库和运行状态不随源码替换；不要直接在现有生产目录执行通用 Compose 来覆盖已验证的服务分工。

驻场 Agent 是独立 Node 服务，固定来源为 [OpenXnet c841ef84 的 platform-resident-agent](https://github.com/synapxnet/OpenXnet/tree/c841ef841da8477fc312e27cd390aecac8ed2d7e/services/platform-resident-agent)。由平台网关代理 `/api/resident/v1/`，模型配置在服务端加密存储。此仓库不复制运行数据、模型密钥或线上访问码。

## 事实边界

推理治理 Controller 的容量变更维护已知的治理状态模型，其指标来自该模型；不能以此声称实际 Kubernetes/GPU 扩容。真实 Kubernetes Workload 工具与此模型分开取证。持久检查点只覆盖这些已审阅的纯内存领域变更；未来接入真实外部写动作必须另建动作前日志、结果回读与补偿机制。

历史部署的 reader 与 checkpoint 制品沿用发布时的 SHA 和旧组件版本，本仓库不会把它们改写为“已部署 1.3.0”。升级需重新构建、保全最新状态、验证契约并受控切流；不得直接回退到陈旧进程内存。

## 本轮检查

2026-09-17 本轮最终组合测试发现 154 项：151 项通过，0 失败、0 错误，3 项因未提供私有真实迁移/恢复证据而按条件跳过。包含 contract 29、SVM 2、MON 2、USR 4、K8s 117（含上述 3 项跳过）；新增的三项只读/治理实例隔离测试全部通过。测试使用本地 mocks、临时目录或隔离 Spring 上下文，不访问生产数据库、不操作 Kubernetes，不进行本轮线上演示。

运行命令：`mvn -B -o -pl aiops-agent-contract,aiops-k8s-service,aiops-mon-service,aiops-svm-service,aiops-usr-service -am clean test`。Linux 文件系统探针保留在 test 源码目录，不打入生产 JAR；本轮 Windows 单测不冒充 Linux 目录同步验收。

排除并移除旧仓库已跟踪的 `target`、IDE 缓存和二进制构建产物；所有源代码、公开配置、SQL 迁移及正式测试保留。

完整九模块 `mvn -B -o -DskipTests package` 构建通过；检查最终 K8s JAR 包含 `aiops-agent-contract-1.3.0.jar`、检查点实现和 reader 防护，不含测试文件系统探针。该步骤仅验证构建，不运行线上服务。
