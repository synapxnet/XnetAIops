<div align="center">

[简体中文](./README.md) | **English** | [日本語](./README.ja-JP.md)

# XnetAIops

**Open-source AIOps for infrastructure, services, and Kubernetes**

[![GOAI release](https://img.shields.io/badge/GOAI%20release-1.3.0-1677ff.svg)](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0)
[![Java](https://img.shields.io/badge/Java-17-e76f00.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-6db33f.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg)](./LICENSE)

[Live Demo](https://www.xnetaiops.synapxnet.cn) · [Frontend: XnetAIops-web](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet](https://openxnet.synapxnet.com) · [License](./LICENSE)

</div>

## GOAI finals release · v1.3.0

[Release & checksums](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0) · [Download source ZIP](https://github.com/synapxnet/XnetAIops/releases/download/v1.3.0/XnetAIops-v1.3.0-0ccbeab1-source.zip) · [v1.3.0 source](https://github.com/synapxnet/XnetAIops/tree/v1.3.0) · [Companion XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/releases/tag/v1.3.0) · [OpenXnet desktop](https://github.com/synapxnet/OpenXnet/releases/tag/v1.3.0)

> This default `display` branch retains historical code. The badge points to the GOAI release; use the `v1.3.0` tag or release assets for that version.

The platform resident Agent contributes infrastructure evidence to OpenXnet and AgentTeams collaboration. Approval verification, resource versions, idempotency and checkpoints define the governed execution boundary; reader services remain separate from execution.

See [source delivery, setup and actual test results](https://github.com/synapxnet/XnetAIops/blob/0ccbeab11ea0903c9e1eec55ad36543f726d56e6/docs/GOAI-FINALS-V1.3.0-SOURCE-DELIVERY.md) for deployment dependencies and known limits. This repository release does not indicate that a live service has been redeployed.

> Historical screenshots below illustrate earlier layouts and are not evidence of the v1.3.0 UI.

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
