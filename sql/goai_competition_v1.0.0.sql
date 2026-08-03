-- GOAI Competition 1.0.0 deterministic AIOps fixture.
-- Contains no production credential, kubeconfig, host password, or customer data.

START TRANSACTION;

INSERT INTO xnet_aiops_clm_cluster
  (uid, cluster_name, cluster_code, description, cluster_type, status,
   total_hosts, running_services, created_by, created_at)
VALUES
  ('cluster_goai_risk_prod', 'GOAI 风险推理生产集群', 'goai-risk-prod',
   'GOAI 1.0.0 模型契约漂移演示环境', 'k8s', 'running', 1, 1, 'goai-fixture',
   '2026-08-03 01:30:00')
ON DUPLICATE KEY UPDATE
  cluster_name = VALUES(cluster_name), description = VALUES(description),
  status = VALUES(status), total_hosts = VALUES(total_hosts),
  running_services = VALUES(running_services);

SET @goai_cluster = (
  SELECT id FROM xnet_aiops_clm_cluster WHERE uid = 'cluster_goai_risk_prod'
);

INSERT INTO xnet_aiops_hom_host
  (uid, cluster_id, hostname, ip_address, ssh_port, ssh_user, auth_type,
   os_type, os_version, cpu_arch, cpu_cores, total_mem_gb, total_disk_gb,
   used_mem_gb, used_disk_gb, cpu_usage, status, agent_status, last_heartbeat)
VALUES
  ('host_goai_risk_01', @goai_cluster, 'risk-worker-01', '192.0.2.41', 22,
   'goai-disabled', 'privateKey', 'linux', 'GOAI fixture', 'x86_64', 16,
   64.00, 500.00, 28.00, 172.00, 41.20, 'online', 'running',
   '2026-08-03 02:00:00')
ON DUPLICATE KEY UPDATE
  cluster_id = VALUES(cluster_id), hostname = VALUES(hostname),
  status = VALUES(status), last_heartbeat = VALUES(last_heartbeat),
  encrypted_password = NULL, private_key = NULL;

SET @goai_host = (
  SELECT id FROM xnet_aiops_hom_host WHERE uid = 'host_goai_risk_01'
);

INSERT INTO xnet_aiops_svm_framework
  (frame_name, frame_code, frame_version, description)
VALUES
  ('GOAI Model Serving', 'goai-model-serving', '1.0.0',
   'GOAI 竞赛模型推理服务 Fixture')
ON DUPLICATE KEY UPDATE
  frame_name = VALUES(frame_name), frame_version = VALUES(frame_version),
  description = VALUES(description);

SET @goai_framework = (
  SELECT id FROM xnet_aiops_svm_framework WHERE frame_code = 'goai-model-serving'
);

INSERT INTO xnet_aiops_svm_service_def
  (framework_id, service_name, service_label, service_version, description,
   dependencies, package_name, config_json, sort_order)
SELECT @goai_framework, 'RiskInference', '风险推理服务', '18',
       'GOAI v18 输入契约漂移演示服务', '[]', 'risk-inference:v18',
       '{"namespace":"risk-prod","workload":"risk-inference"}', 1
WHERE NOT EXISTS (
  SELECT 1 FROM xnet_aiops_svm_service_def
  WHERE framework_id = @goai_framework AND service_name = 'RiskInference'
);

SET @goai_service_def = (
  SELECT id FROM xnet_aiops_svm_service_def
  WHERE framework_id = @goai_framework AND service_name = 'RiskInference'
  ORDER BY id LIMIT 1
);

INSERT INTO xnet_aiops_svm_role_def
  (service_def_id, role_name, role_type, cardinality, jmx_port, log_file)
SELECT @goai_service_def, 'InferenceServer', 'worker', '1+', NULL,
       '/var/log/risk-inference/server.log'
WHERE NOT EXISTS (
  SELECT 1 FROM xnet_aiops_svm_role_def
  WHERE service_def_id = @goai_service_def AND role_name = 'InferenceServer'
);

SET @goai_role_def = (
  SELECT id FROM xnet_aiops_svm_role_def
  WHERE service_def_id = @goai_service_def AND role_name = 'InferenceServer'
  ORDER BY id LIMIT 1
);

INSERT INTO xnet_aiops_svm_service_instance
  (uid, cluster_id, service_def_id, service_name, status, config_json,
   config_version, need_restart, created_at)
VALUES
  ('service_risk_inference', @goai_cluster, @goai_service_def,
   'RiskInference', 'running',
   '{"namespace":"risk-prod","workload":"risk-inference","revision":18}',
   18, TRUE, '2026-08-03 01:45:00')
ON DUPLICATE KEY UPDATE
  cluster_id = VALUES(cluster_id), service_def_id = VALUES(service_def_id),
  status = VALUES(status), config_json = VALUES(config_json),
  config_version = VALUES(config_version), need_restart = VALUES(need_restart);

SET @goai_service = (
  SELECT id FROM xnet_aiops_svm_service_instance WHERE uid = 'service_risk_inference'
);

INSERT INTO xnet_aiops_svm_role_instance
  (uid, service_instance_id, role_def_id, role_name, role_type, host_id,
   hostname, status, need_restart, created_at)
VALUES
  ('role_risk_inference_01', @goai_service, @goai_role_def,
   'InferenceServer', 'worker', @goai_host, 'risk-worker-01',
   'running', TRUE, '2026-08-03 01:45:00')
ON DUPLICATE KEY UPDATE
  service_instance_id = VALUES(service_instance_id), role_def_id = VALUES(role_def_id),
  host_id = VALUES(host_id), status = VALUES(status),
  need_restart = VALUES(need_restart);

INSERT INTO xnet_aiops_mon_alert_rule
  (uid, cluster_id, rule_name, service_name, expression, compare_method,
   threshold_value, alert_level, duration_seconds, enabled, description, created_at)
VALUES
  ('rule_risk_error_rate', @goai_cluster, '风险模型推理错误率异常',
   'RiskInference',
   'sum(rate(risk_inference_errors_total[5m])) / sum(rate(risk_inference_requests_total[5m]))',
   '>', 0.0500, 'critical', 60, TRUE,
   '当前错误率超过生产阈值，需核对数据 Schema 与模型输入契约',
   '2026-08-03 01:55:00')
ON DUPLICATE KEY UPDATE
  expression = VALUES(expression), threshold_value = VALUES(threshold_value),
  alert_level = VALUES(alert_level), enabled = VALUES(enabled),
  description = VALUES(description);

SET @goai_alert_rule = (
  SELECT id FROM xnet_aiops_mon_alert_rule WHERE uid = 'rule_risk_error_rate'
);

INSERT INTO xnet_aiops_mon_alert_history
  (uid, cluster_id, alert_rule_id, alert_name, hostname, alert_level,
   alert_info, alert_advice, status, triggered_at, resolved_at)
VALUES
  ('alert_risk_error_rate', @goai_cluster, @goai_alert_rule,
   '风险模型推理错误率异常', 'risk-inference', 'critical',
   '{"description":"当前错误率 18%，基线 0.8%，P95 延迟 2600ms；工作负载仍运行 v18。","currentErrorRate":0.18,"baselineErrorRate":0.008,"p95Ms":2600,"relatedResource":{"type":"K8S_DEPLOYMENT","uid":"risk-inference","namespace":"risk-prod","name":"risk-inference"}}',
   '核对生产特征 Schema 与 v18 输入契约；审批后回滚到已验证的 v17。',
   'open', '2026-08-03 02:00:00', NULL)
ON DUPLICATE KEY UPDATE
  cluster_id = VALUES(cluster_id), alert_rule_id = VALUES(alert_rule_id),
  alert_info = VALUES(alert_info), alert_advice = VALUES(alert_advice),
  status = VALUES(status), triggered_at = VALUES(triggered_at), resolved_at = NULL;

INSERT INTO xnet_aiops_k8s_cluster
  (uid, name, description, api_server_url, kubeconfig_content, version,
   node_count, namespace_count, status, provider, network_plugin,
   container_runtime, created_at)
VALUES
  ('k8s_goai_risk_prod', 'GOAI 风险推理 K8s',
   '仅保存演示资源标识；真实 kubeconfig 必须通过安全配置另行提供。',
   NULL, NULL, '1.30', 1, 1, 'inactive', 'self-managed', 'cilium',
   'containerd', '2026-08-03 01:30:00')
ON DUPLICATE KEY UPDATE
  name = VALUES(name), description = VALUES(description), version = VALUES(version),
  kubeconfig_content = NULL;

COMMIT;

-- Verification queries:
-- SELECT uid, alert_info FROM xnet_aiops_mon_alert_history WHERE uid = 'alert_risk_error_rate';
-- SELECT uid, status, config_version, need_restart FROM xnet_aiops_svm_service_instance WHERE uid = 'service_risk_inference';
