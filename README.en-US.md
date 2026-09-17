<div align="center">

[简体中文](./README.md) | **English** | [日本語](./README.ja-JP.md)

# XnetAIops

**Open-source AIOps for infrastructure, services, and Kubernetes**

[![GOAI release](https://img.shields.io/badge/GOAI%20release-1.3.0-1677ff.svg)](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0)
[![Java](https://img.shields.io/badge/Java-17-e76f00.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.6-6db33f.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/license-MIT-2ea44f.svg)](./LICENSE)

[Live Demo](https://goai.xnetaiops.synapxnet.online) · [Frontend: XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/tree/v1.3.0) · [OpenXnet](https://openxnet.synapxnet.com) · [License](./LICENSE)

</div>

## GOAI finals release · v1.3.0

[Release & checksums](https://github.com/synapxnet/XnetAIops/releases/tag/v1.3.0) · [Download source ZIP](https://github.com/synapxnet/XnetAIops/releases/download/v1.3.0/XnetAIops-v1.3.0-source.zip) · [v1.3.0 source](https://github.com/synapxnet/XnetAIops/tree/v1.3.0) · [Companion XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/releases/tag/v1.3.0) · [OpenXnet desktop](https://github.com/synapxnet/OpenXnet/releases/tag/v1.3.0)

> This default `display` branch retains historical code. The badge points to the GOAI release; use the `v1.3.0` tag or release assets for that version.

The platform resident Agent contributes infrastructure evidence to OpenXnet and AgentTeams collaboration. Approval verification, resource versions, idempotency and checkpoints define the governed execution boundary; reader services remain separate from execution.

See [program verification baseline, setup and delivery notes](https://github.com/synapxnet/XnetAIops/blob/0ccbeab11ea0903c9e1eec55ad36543f726d56e6/docs/GOAI-FINALS-V1.3.0-SOURCE-DELIVERY.md) for deployment dependencies and known limits. This repository release does not indicate that a live service has been redeployed.

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

This repository contains the backend. Together with [XnetAIops-web](https://github.com/synapxnet/XnetAIops-web/tree/v1.3.0), it forms an enterprise-grade, multi-tenant, frontend/backend-separated system. Its modular microservices can be adopted as a complete platform or integrated by domain.

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

## Get and Build v1.3.0

Use JDK 17, Maven 3.9, MySQL 8.x and Redis 7.x. The backend uses Spring Boot 3.4.6 and MyBatis. Select the fixed GOAI tag instead of the historical default `display` branch:

```bash
git clone --branch v1.3.0 --depth 1 https://github.com/synapxnet/XnetAIops.git
cd XnetAIops
mvn -B -DskipTests package
```

Artifacts are module `target/*-1.3.0.jar` files. This command builds only; use the delivery notes for tests, skips and limits.

### Deployment prerequisites

- Provide external MySQL and Redis. Review `XnetAIops.sql`, K8s/DevOps tables and `database/migrations` against the target database; back up before initialization or migration. A fully initialized database image is not included.
- Copy `.env.example` to `.env` and configure database/Redis access and `K8S_ENCRYPTION_KEY`. USR also requires `JWT_SECRET`; generic Compose does not pass it, so supply it to `aiops-usr-service` through an override or existing orchestration. Copying `.env` alone is insufficient.
- Build the companion frontend at `v1.3.0`; point `WEB_DIST_PATH` at `apps/web-antd/dist`. Generic Web port is `81`; internal USR is `9185`, with CLM/HOM/SVM/MON/K8S/REG on `9181/9182/9183/9184/9186/9187`. Public access uses the HTTPS gateway.
- Generic Compose excludes the independent resident Agent, AgentTeams, approval service and reader/checkpoint separation. Deploy the pinned OpenXnet resident service separately and route `/api/resident/v1/` through the gateway. A single generic Compose command does not reproduce the complete finals environment.

After configuration, run `docker compose up -d --build` and `docker compose ps` in an isolated environment. Do not overwrite an existing competition deployment with generic Compose.

## Demo Access

- Current GOAI entry: <https://goai.xnetaiops.synapxnet.online/#/auth/login>.
- Demo phone: `17870171303`; demo verification code: `000000` (six digits, demo environment only).
- Sign in with a phone number and code, not the OpenXnet desktop password. This screen does not send SMS; the project supplies the demo code.
- Verified on 2026-09-18: login as `goai_operator` / `OPERATOR`; page title `XnetAIops`; resident status `platform=aiops`, `agentVersion=1.3.0`, `ONLINE`.
- This code is separate from an AgentTeams demo access code, Live execution authorization and model API keys. Those credentials are not interchangeable and are not published here.

The same-origin API gateway is `https://goai.xnetaiops.synapxnet.online`. Login: `POST /api/usr/login`; identity: `GET /api/usr/user/info`; resident status: `GET /api/resident/v1/status`. The last two require the platform Bearer token. Business prefixes are `/api/clm`, `/api/hom`, `/api/svm`, `/api/mon`, `/api/k8s` and `/api/reg`.

This check performed login and read-only identity/resident requests, without business changes or model calls. `modelConfigured=true` means configuration exists, not that inference was tested. Public demo authentication must not be used for production.

## Community and License

XnetAIops is part of the [OpenXnet](https://openxnet.synapxnet.com) open-source ecosystem. Issues and pull requests are welcome.

Released under the [MIT License](./LICENSE). Copyright © 2026 SynapXnet.
