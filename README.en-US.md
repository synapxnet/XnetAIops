<div align="center">

[简体中文](./README.md) | **English** | [日本語](./README.ja-JP.md)

# XnetAIops

**Open-source AIOps for infrastructure, services, and Kubernetes**

[![Version](https://img.shields.io/badge/version-1.0.0-1677ff.svg)](https://www.xnetaiops.synapxnet.cn)
[![Java](https://img.shields.io/badge/Java-17-e76f00.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-6db33f.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg)](./LICENSE)

[Live Demo](https://www.xnetaiops.synapxnet.cn) · [Frontend: XnetAIops-web](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet](https://openxnet.synapxnet.com) · [License](./LICENSE)

</div>

![XnetAIops 3D cluster overview](./docs/images/xnetaiops-overview-2026.png)

## Product Tour

| Demo login | About |
| --- | --- |
| ![Demo login](./docs/images/xnetaiops-login.png) | ![About XnetAIops](./docs/images/xnetaiops-about.png) |
| Cluster management | Host onboarding |
| ![Cluster management](./docs/images/xnetaiops-clusters.png) | ![Host onboarding](./docs/images/xnetaiops-host-add.png) |
| Kubernetes | Service orchestration |
| ![Kubernetes](./docs/images/xnetaiops-kubernetes.png) | ![Service orchestration](./docs/images/xnetaiops-service.png) |
| Monitoring and alerts | Image registry |
| ![Monitoring](./docs/images/xnetaiops-monitor-2026.png) | ![Image registry](./docs/images/xnetaiops-registry.png) |
| Multi-tenant users | Kubernetes nodes |
| ![Users](./docs/images/xnetaiops-users.png) | ![Kubernetes nodes](./docs/images/xnetaiops-k8s-nodes.png) |
| Kubernetes namespaces | Kubernetes workloads |
| ![Kubernetes namespaces](./docs/images/xnetaiops-k8s-namespaces.png) | ![Kubernetes workloads](./docs/images/xnetaiops-workloads-2026.png) |

## Overview

XnetAIops is an open-source operations platform maintained by the **SynapXnet team**. It brings servers, middleware, services, Kubernetes clusters, observability, and image registries into one operational workspace.

This repository contains the backend. Together with [XnetAIops-web](https://github.com/synapxnet/XnetAIops-web), it forms an enterprise-grade, multi-tenant, frontend/backend-separated system. Its modular microservices can be adopted as a complete platform or integrated by domain.

## GOAI Competition 1.0.0

The `GOAI-Competition` branch adds read-only Agent evidence tools for alerts, Kubernetes workloads, and service health. Every call uses the shared `ToolResponse 1.0.0` envelope, Workspace/Incident/Trace context, and a short-lived single-tool delegation token. The versioned fixture reproduces the v18 degradation without UID-specific branches in application code.

[Handoff, fixture, API examples, and verification](./docs/goai-handoff/HANDOFF-GOAI-COMPETITION-1.0.0.md) · [Companion evidence UI](https://github.com/synapxnet/XnetAIops-web/tree/GOAI-Competition)

## Why XnetAIops

- **Enterprise multi-tenancy:** users, roles, teams, and resource boundaries for multiple organizations.
- **Frontend/backend separation:** independent delivery and integration with existing gateways and infrastructure.
- **Modular operations:** clear service boundaries for infrastructure, services, Kubernetes, monitoring, and registries.
- **Container-ready delivery:** Maven and Docker Compose workflows for repeatable deployment.
- **Continuous development:** ongoing improvements to automation, observability, security, and documentation.

## Modules

| Module | Service | Responsibility |
| --- | --- | --- |
| CLM | `aiops-clm-service` | Cluster lifecycle plus MySQL, Redis, Hadoop, and Jenkins deployment records |
| HOM | `aiops-hom-service` | Hosts, racks, SSH connectivity, and capacity inventory |
| SVM | `aiops-svm-service` | Service definitions, role allocation, commands, and lifecycle operations |
| MON | `aiops-mon-service` | Monitoring dashboard, alert rules, history, and notification groups |
| K8S | `aiops-k8s-service` | Clusters, resources, Helm, monitoring, alerts, app catalog, and delivery pipelines |
| REG | `aiops-reg-service` | Registries, projects, repositories, tags, replication, and deployment history |
| USR | `aiops-usr-service` | Authentication, users, roles, and platform access control |

## Quick Start

Requirements: JDK 17+, Maven 3.9+, Docker Compose, MySQL 8.x, and Redis 7.x.

```bash
mvn -DskipTests package
cp .env.example .env
docker compose up -d --build
docker compose ps
```

The optional showcase dataset is in `sql/xnet_aiops_demo.sql`. It uses non-routable addresses and placeholder credentials, and can be safely re-applied without overwriting user-created records.

## Demo Access

- URL: <https://www.xnetaiops.synapxnet.cn>
- Phone: `12345678900`
- Verification code: `000000`

The fixed verification code is for the public showcase only. Production deployments must use a secure authentication provider.

## Community and License

XnetAIops is part of the [OpenXnet](https://openxnet.synapxnet.com) open-source ecosystem. Issues and pull requests are welcome.

Released under the [MIT License](./LICENSE). Copyright © 2026 SynapXnet.
