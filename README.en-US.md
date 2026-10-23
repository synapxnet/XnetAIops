<!-- Copyright (C) 2026 Synapxnet. All rights reserved.
Synapxnet Proprietary and Confidential. No copying, distribution or use without authorization.
Author: maoyo | Department: 研发部 | Date: 2026-10-23 | Version: 1.3.0 | Security Level: INTERNAL -->
# XnetAIOps

[简体中文](README.md) · [日本語](README.ja-JP.md)

XnetAIOps combines infrastructure operations with governed Agent execution. The mainline retains clusters, hosts, services, monitoring, Kubernetes, registries, organizational access, resident Agents, cross-platform evidence, approvals, independent verification and recovery.

Active development uses `main`; historical release branches and tags remain available.

## Modules

| Module | Responsibility |
| --- | --- |
| CLM / HOM | Clusters, hosts, racks and infrastructure lifecycle |
| SVM / MON | Services, commands, monitoring and alerts |
| K8S / REG | Kubernetes, Helm, pipelines, images and artifacts |
| USR | Identity, roles, organizations and login verification |
| Agent contract | Delegation, evidence, bound approvals, versions, idempotency and compensation |

OpenXnet coordinates platforms while each platform retains its own operation and permission boundaries. Resident Agents and AgentTeams are configured as separate services.

## Build and test

Use JDK 17 and Maven 3.9. Running services require MySQL 8 and Redis 7.

```bash
mvn -B test
mvn -B -DskipTests package
```

Service packages are generated under `target/`. The standard Compose configuration covers native platform services; resident Agents, AgentTeams, approvals and execution runtimes require their own deployment configuration.

## Configuration and login

Create deployment settings from `.env.example`, including database and Redis endpoints, `JWT_SECRET` and `K8S_ENCRYPTION_KEY`. Back up the database and review `XnetAIops.sql`, K8s/DevOps schemas and `database/migrations` before applying the required initialization or migrations. Set `WEB_DIST_PATH` to the matching frontend build.

Administrators provision accounts and organization memberships. No shared account or fixed login code is provided. Configure `XNET_SMS_GATEWAY_URL` and `XNET_SMS_GATEWAY_TOKEN`: the gateway accepts a Bearer-authenticated POST with `{phone, code, purpose: "login", expiresInSeconds: 300}` and acknowledges delivery with HTTP 2xx and `{"accepted": true}`.

On an empty database, apply `XnetAIops.sql` followed by `devops-tables.sql` in that database. For an existing database, back it up and apply `database/migrations/20261023-mainline-organization-access.sql` as needed; do not rerun the complete initialization. Organization data access defaults to disabled and must be granted explicitly. A deployment administrator with database access provisions the first real administrator with a unique UID, a real phone number, a newly generated BCrypt password hash, `user_type=admin` and `status=active`; subsequent accounts can be managed after SMS login. There is no anonymous bootstrap endpoint.

Codes expire after five minutes, are limited to one send per minute and five failed attempts, and are consumed on success. Missing gateway configuration blocks delivery. Disabled accounts cannot use an old JWT to obtain identity or permissions.

## Governed execution

Identity, workspace, approval, resource-version and idempotency checks remain enforced. Production evidence requires `PRODUCTION` with `synthetic=false`; isolated rehearsal evidence is labeled separately. `OPENXNET_REHEARSAL_ENABLED` defaults to false. A missing real execution target cannot produce simulated success.

The [identity archival script](database/maintenance/archive-competition-identities.sql) defaults to preview. Review candidates and take an independent backup before maintenance; it retains audit principals and business history.

[Transition notes](docs/MAINLINE-TRANSITION.md) · [Frontend](https://github.com/synapxnet/XnetAIops-web) · [OpenXnet](https://openxnet.synapxnet.com)

## License

See [LICENSE](LICENSE) and the notices in individual files.
