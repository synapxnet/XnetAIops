<div align="center">

**简体中文** | [English](./README.en-US.md) | [日本語](./README.ja-JP.md)

# XnetAIops

**面向基础设施、服务与 Kubernetes 的智能运维平台**

[![Version](https://img.shields.io/badge/version-1.0.0-1677ff.svg)](https://www.xnetaiops.synapxnet.cn)
[![Java](https://img.shields.io/badge/Java-17-e76f00.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-6db33f.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg)](./LICENSE)

[在线体验](https://www.xnetaiops.synapxnet.cn) · [前端仓库 XnetAIops-web](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet 开源社区](https://openxnet.synapxnet.com) · [查看许可](./LICENSE)

</div>

![XnetAIops 集群 3D 概览](./docs/images/xnetaiops-overview-2026.png)

## 页面预览

| 演示登录 | 关于项目 |
| --- | --- |
| ![XnetAIops 演示登录](./docs/images/xnetaiops-login.png) | ![XnetAIops 关于项目](./docs/images/xnetaiops-about.png) |
| 集群管理 | 主机接入 |
| ![XnetAIops 集群管理](./docs/images/xnetaiops-clusters.png) | ![XnetAIops 主机接入](./docs/images/xnetaiops-host-add.png) |
| Kubernetes 集群 | 服务编排 |
| ![XnetAIops Kubernetes 集群](./docs/images/xnetaiops-kubernetes.png) | ![XnetAIops 服务编排](./docs/images/xnetaiops-service.png) |
| 监控告警 | 镜像仓库 |
| ![XnetAIops 监控告警](./docs/images/xnetaiops-monitor-2026.png) | ![XnetAIops 镜像仓库](./docs/images/xnetaiops-registry.png) |
| 多租户用户 | Kubernetes 节点 |
| ![XnetAIops 多租户用户](./docs/images/xnetaiops-users.png) | ![XnetAIops Kubernetes 节点](./docs/images/xnetaiops-k8s-nodes.png) |
| Kubernetes 命名空间 | Kubernetes 工作负载 |
| ![XnetAIops Kubernetes 命名空间](./docs/images/xnetaiops-k8s-namespaces.png) | ![XnetAIops Kubernetes 工作负载](./docs/images/xnetaiops-workloads-2026.png) |

## 项目简介

XnetAIops 是由 **SynapXnet 团队**开源的智能运维平台，面向服务器、基础软件、业务服务、Kubernetes 集群与镜像仓库等运维对象，提供从资源纳管、部署交付到监控告警的统一工作台。

本仓库是平台后端，与 [XnetAIops-web](https://github.com/synapxnet/XnetAIops-web) 前端仓库共同组成企业级、多租户、前后端分离系统。项目采用模块化微服务架构，将集群生命周期、主机资产、服务编排、可观测性、租户权限控制等能力拆分为独立服务，便于按场景组合、扩展和二次开发。

## GOAI Competition 1.0.0

`GOAI-Competition` 分支新增面向 OpenXnet Agent 的只读运维证据接口：告警事实、Kubernetes 工作负载和服务健康度共享 `ToolResponse 1.0.0`、Workspace/Incident/Trace 上下文及短期单工具委托令牌。固定演示 Fixture 还原 v18 上线后错误率从 0.8% 升至 18%、P95 达到 2.6 秒的可复现场景，不在业务代码中按 UID 伪造结果。

[查看交接、Fixture、调用样例和验证记录](./docs/goai-handoff/HANDOFF-GOAI-COMPETITION-1.0.0.md) · [配套事件证据页](https://github.com/synapxnet/XnetAIops-web/tree/GOAI-Competition)

## 项目优势

- **企业多租户**：通过用户、角色、权限与资源边界服务不同组织和团队。
- **前后端分离**：Web 控制台和后端服务独立演进，可按现有基础设施灵活集成。
- **模块化交付**：各业务服务边界清晰，支持整体部署或按场景扩展。
- **工程化部署**：提供 Maven 与 Docker Compose 工作流，便于本地验证和容器化交付。
- **持续更新**：SynapXnet 团队会持续完善自动化、可观测性、安全性与文档。

## 核心能力

| 模块 | 服务目录 | 说明 |
| --- | --- | --- |
| 3D 总览 | Web 展示层 | 以三维视图呈现集群、节点及拓扑关系，快速建立基础设施全局视角 |
| CLM 集群管理 | `aiops-clm-service` | 管理集群生命周期，支持 MySQL、Redis、Hadoop、Jenkins 等基础组件的部署与运维 |
| HOM 主机管理 | `aiops-hom-service` | 管理主机、机架、SSH 连通性与基础设施资产，为自动化部署提供资源底座 |
| SVM 服务管理 | `aiops-svm-service` | 提供服务概览、命令中心、框架管理及服务启停、部署和生命周期管理 |
| MON 监控告警 | `aiops-mon-service` | 汇聚监控看板、告警历史与告警规则，帮助定位基础设施和服务异常 |
| K8S 容器平台 | `aiops-k8s-service` | 管理集群、节点、命名空间、工作负载、存储、网络、RBAC、Helm、流水线与部署计划 |
| REG 仓库管理 | `aiops-reg-service` | 管理镜像仓库、项目、制品标签、同步任务、仓库端点与部署日志 |
| USR 系统管理 | `aiops-usr-service` | 提供登录认证、用户、角色与访问权限管理，支撑平台级权限控制 |

## 技术架构

```mermaid
flowchart LR
    Web["XnetAIops Web"] --> USR["USR 认证与权限"]
    Web --> OPS["CLM / HOM / SVM / MON"]
    Web --> K8S["K8S 容器平台"]
    Web --> REG["REG 镜像仓库"]
    OPS --> DB[(MySQL)]
    USR --> DB
    USR --> Cache[(Redis)]
    K8S --> Cluster["Kubernetes / Helm"]
    REG --> Registry["Harbor / OCI Registry"]
    OPS --> Infra["主机 / Hadoop / Jenkins"]
```

后端基于 Java 17、Spring Boot 3.4.6 与 MyBatis 构建，使用 Maven 管理多模块工程，并通过 Docker Compose 组织前端代理和各微服务容器。

## 目录结构

```text
XnetAIops/
├── aiops-clm-service/    # 集群生命周期
├── aiops-hom-service/    # 主机与机架资产
├── aiops-svm-service/    # 服务管理
├── aiops-mon-service/    # 监控告警
├── aiops-usr-service/    # 用户与权限
├── aiops-k8s-service/    # Kubernetes 管理
├── aiops-reg-service/    # 镜像仓库管理
├── nginx/                # 反向代理配置
├── XnetAIops.sql         # 数据库初始化脚本
└── docker-compose.yml    # 容器编排
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Docker 与 Docker Compose
- MySQL 8.x、Redis 7.x

### 构建后端

```bash
mvn -DskipTests package
```

### 容器启动

复制环境变量模板并配置数据库、Redis、`JWT_SECRET` 及外部基础设施连接；如需同时启动 Web，请先构建同级的 `XnetAIops-web` 仓库，并确认 `WEB_DIST_PATH` 指向前端产物。

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

公开展示数据位于 `sql/xnet_aiops_demo.sql`。脚本仅使用不可路由的演示地址和无效占位凭据，并通过 `demo-aiops-` 标记实现可重复执行，不会覆盖用户自行创建的数据。请在完成 `XnetAIops.sql` 初始化后执行：

```bash
mysql -h "${DB_HOST}" -u root -p XnetAIops < sql/xnet_aiops_demo.sql
```

生产环境请使用独立的密钥、强密码和受限网络策略，不要沿用演示环境配置。

## 在线体验

- 访问地址：<https://www.xnetaiops.synapxnet.cn>
- 演示手机号：`17870171303`
- 演示验证码：`000000`

固定验证码仅用于开源项目展示，不应作为生产环境认证方案。

## SynapXnet 开源生态

XnetAIops 是 SynapXnet 开源体系的一部分。更多团队项目、技术方向与社区动态请访问 [OpenXnet](https://openxnet.synapxnet.com)。

## 参与贡献

欢迎通过 Issue 提交缺陷、需求和改进建议。提交代码前，请保持模块边界清晰，并为关键业务变更补充必要的测试与说明。

## 开源许可

本项目基于 [MIT License](./LICENSE) 开源。你可以自由使用、修改和分发本项目，但须保留原始版权与许可声明。
