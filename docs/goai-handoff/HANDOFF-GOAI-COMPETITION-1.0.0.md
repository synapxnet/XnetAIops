# GOAI Competition 1.0.0 后端交接

- 仓库：`synapxnet/XnetAIops`
- 任务：`AIOPS-BE-01`、`AIOPS-BE-02`
- Worktree：`D:\synapxnet\.codex-build\goai-competition-1.0.0\XnetAIops`
- 分支：`GOAI-Competition`
- 基线提交：`272ea3818c8db1e5ad0b30e631e92f5af268275f`
- 结束提交：见 `GOAI-Competition` 分支候选 HEAD
- 产品版本：`1.0.0`
- 契约版本：`1.0.0`
- MCP 版本：`2026-07-28`（由 OpenXnet Gateway 提供）

## 交付范围

| 工具 | 服务 | 事实来源 |
| --- | --- | --- |
| `aiops.alert.get` | MON | 告警历史、规则和结构化 `alert_info` |
| `aiops.k8s.workload.get` | K8s | Kubernetes Deployment、Pod、Event 和指标 |
| `aiops.service.health` | SVM | 服务实例、角色实例和健康结论 |

公共 `aiops-agent-contract` 负责 ToolRequest/ToolResponse、Header/Body 上下文一致性、MDC、错误包络、Evidence ID 和 HS256 单工具委托令牌校验。`contracts/` 是随仓库发布的 canonical 1.0.0 快照。

## 配置键

只配置名称，不在仓库保存值：

- `openxnet.agent.delegation-secret`：至少 32 字节的内部委托密钥。
- `openxnet.agent.audience`：默认 `openxnet-agent-adapter`。
- 各服务原有数据库和 Kubernetes 连接配置。

生产环境必须从 Secret Manager、容器 Secret 或进程环境注入。禁止把外部 MCP Bearer Token 直接交给平台服务。

## Fixture 与回退

`sql/goai_competition_v1.0.0.sql` 是可重复执行的演示 Fixture，包含：

- `alert_risk_error_rate`：错误率 18%、基线 0.8%、P95 2600ms。
- `service_risk_inference`：v18 服务状态与 `risk-inference` 资源关联。
- 不含密码、私钥或 kubeconfig；演示 K8s 记录保持 `inactive`。

执行前备份目标测试库。回退时在停机窗口按外键逆序删除 `alert_risk_error_rate`、`rule_risk_error_rate`、`role_risk_inference_01`、`service_risk_inference`、`host_goai_risk_01`、`cluster_goai_risk_prod` 等固定 Fixture，或直接恢复备份；不要在生产库执行 Fixture。

## 调用样例

```bash
curl -X POST 'https://<aiops-mon>/api/agent/v1/tools/aiops.alert.get:invoke' \
  -H 'Authorization: Bearer <short-lived-delegation-token>' \
  -H 'Content-Type: application/json' \
  -H 'X-OpenXnet-Workspace-Id: ws_goai_demo' \
  -H 'X-OpenXnet-Incident-Id: inc_model_contract_001' \
  -H 'X-OpenXnet-Trace-Id: trace_model_contract_001' \
  -H 'X-OpenXnet-Tool-Name: aiops.alert.get' \
  -d '{"requestId":"req_alert_001","toolName":"aiops.alert.get","arguments":{"alertUid":"alert_risk_error_rate"},"dryRun":false}'
```

同一上下文下继续调用：

- `POST /api/agent/v1/tools/aiops.k8s.workload.get:invoke`
- `POST /api/agent/v1/tools/aiops.service.health:invoke`

## 验证记录

```powershell
mvn -pl aiops-mon-service,aiops-k8s-service,aiops-svm-service -am test
```

结果：`BUILD SUCCESS`；4 项新增测试通过（委托令牌 2、告警证据装配 2），0 失败、0 错误。K8s/SVM 的真实外部连接需在受控演示环境做集成验收。

## GOAI Competition 1.1.0 三场景扩展

比赛环境入口为 `https://goai.xnetaiops.synapxnet.online`，当前解析到比赛专用服务器 `150.109.120.15`。域名和 IP 只属于部署环境，代码必须继续通过配置注入端点。

XnetAIops 当前负责 11 个固定工具：原有告警、服务健康、Kubernetes Workload，加上推理指标、恢复状态、GPU 容量保障、运行时调优、容量扩展、流量切换、队列感知弹性策略和稳态收敛。6 个写工具全部要求计划级审批、参数摘要、资源版本、职责分离、幂等和补偿标记；Dry Run 使用隔离虚拟版本，真实执行使用实际资源版本。

本轮定向测试：Agent Contract 9 项、Kubernetes 6 项、Service 3 项、Monitoring 5 项通过。真实 HTTPS Live 已覆盖推荐容量处置、量化与风控场景的 AIOps 取证/验证调用，并在测试后恢复 K3s `3/3` 初态。

权威工具契约位于 `contracts/goai-tools.v1.json`，由 `D:\synapxnet\scripts\Sync-GoaiCompetitionContracts.cjs` 从 OpenXnet 注册表生成。不得手工删除新增工具或恢复旧的 10 工具清单。

## 已知限制与后续注意

- Fixture 不提供 kubeconfig，K8s 工具必须连接已授权测试集群。
- 指标不可用时返回 warning/不可用字段，不得伪造为 0。
- 发布前应以无权限 Workspace、缺 Header、错误 audience 和超时上游执行负向集成测试。
- 前端深链为 `XnetAIops-web` 的 `/agent/incidents/:incidentId`。
