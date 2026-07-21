-- ============================================================
-- XnetAIops public showcase data
-- Safe to re-run: only rows with the demo-aiops marker are replaced.
-- Apply XnetAIops.sql before this file.
-- ============================================================

USE XnetAIops;
SET NAMES utf8mb4;

-- Optional K8S extensions used by the public showcase.
CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_app_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  display_name VARCHAR(200) NOT NULL,
  description TEXT,
  category VARCHAR(50) NOT NULL,
  icon VARCHAR(500),
  helm_repo_name VARCHAR(100) NOT NULL,
  helm_repo_url VARCHAR(500) NOT NULL,
  chart_name VARCHAR(100) NOT NULL,
  chart_version VARCHAR(50),
  default_values TEXT,
  doc_url VARCHAR(500),
  is_featured TINYINT DEFAULT 0,
  sort_order INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s application templates';

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_helm_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key VARCHAR(100) NOT NULL UNIQUE,
  config_value TEXT,
  description VARCHAR(500),
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Helm global configuration';

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_devops_project (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description TEXT,
  jenkins_url VARCHAR(500) NOT NULL,
  jenkins_user VARCHAR(100) NOT NULL,
  jenkins_token VARCHAR(500) NOT NULL,
  cluster_id BIGINT,
  status VARCHAR(20) DEFAULT 'active',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DevOps projects';

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_credential (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(30) NOT NULL,
  description TEXT,
  username VARCHAR(200),
  password VARCHAR(500),
  private_key TEXT,
  passphrase VARCHAR(200),
  token VARCHAR(500),
  kubeconfig TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES xnet_aiops_k8s_devops_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DevOps credentials';

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_pipeline (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description TEXT,
  type VARCHAR(20) DEFAULT 'pipeline',
  jenkinsfile TEXT,
  source_type VARCHAR(20),
  source_url VARCHAR(500),
  source_branch VARCHAR(100) DEFAULT 'main',
  credential_id BIGINT,
  disable_concurrent BOOLEAN DEFAULT FALSE,
  timer_trigger VARCHAR(100),
  jenkins_job_name VARCHAR(200),
  jenkins_job_path VARCHAR(500),
  status VARCHAR(20) DEFAULT 'active',
  last_run_status VARCHAR(20),
  last_run_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES xnet_aiops_k8s_devops_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DevOps pipelines';

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_pipeline_run (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pipeline_id BIGINT NOT NULL,
  run_number INT NOT NULL,
  status VARCHAR(20) DEFAULT 'pending',
  trigger_type VARCHAR(20),
  trigger_user VARCHAR(100),
  parameters TEXT,
  start_time DATETIME,
  end_time DATETIME,
  duration_ms BIGINT,
  stages_status TEXT,
  log_text LONGTEXT,
  jenkins_build_url VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (pipeline_id) REFERENCES xnet_aiops_k8s_pipeline(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DevOps pipeline runs';

CREATE TABLE IF NOT EXISTS xnet_aiops_reg_sync_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  registry_id BIGINT NOT NULL,
  source_image VARCHAR(500) NOT NULL,
  target_project VARCHAR(200),
  sync_method VARCHAR(20) NOT NULL DEFAULT 'harbor_replication',
  harbor_policy_id BIGINT,
  harbor_execution_id BIGINT,
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  status_detail TEXT,
  created_by VARCHAR(100),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_registry_id (registry_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Image sync showcase tasks';

START TRANSACTION;

-- Remove the previous showcase dataset in dependency order.
DELETE FROM xnet_aiops_k8s_pipeline_run
WHERE pipeline_id IN (
  SELECT id FROM xnet_aiops_k8s_pipeline
  WHERE project_id IN (SELECT id FROM xnet_aiops_k8s_devops_project WHERE name LIKE '演示-%')
);
DELETE FROM xnet_aiops_k8s_pipeline
WHERE project_id IN (SELECT id FROM xnet_aiops_k8s_devops_project WHERE name LIKE '演示-%');
DELETE FROM xnet_aiops_k8s_credential
WHERE project_id IN (SELECT id FROM xnet_aiops_k8s_devops_project WHERE name LIKE '演示-%');
DELETE FROM xnet_aiops_k8s_devops_project WHERE name LIKE '演示-%';

DELETE FROM xnet_aiops_reg_tag
WHERE repository_id IN (
  SELECT id FROM xnet_aiops_reg_repository
  WHERE registry_id IN (SELECT id FROM xnet_aiops_reg_registry WHERE uid LIKE 'demo-aiops-%')
);
DELETE FROM xnet_aiops_reg_repository
WHERE registry_id IN (SELECT id FROM xnet_aiops_reg_registry WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_reg_project
WHERE registry_id IN (SELECT id FROM xnet_aiops_reg_registry WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_reg_deploy_log
WHERE registry_id IN (SELECT id FROM xnet_aiops_reg_registry WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_reg_sync_task
WHERE registry_id IN (SELECT id FROM xnet_aiops_reg_registry WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_reg_registry WHERE uid LIKE 'demo-aiops-%';

DELETE FROM xnet_aiops_k8s_alert_history
WHERE cluster_id IN (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_alert_rule
WHERE cluster_id IN (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_deploy_log
WHERE plan_id IN (SELECT id FROM xnet_aiops_k8s_deploy_plan WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_deploy_node
WHERE plan_id IN (SELECT id FROM xnet_aiops_k8s_deploy_plan WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_deploy_plan WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_k8s_helm_release
WHERE cluster_id IN (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_prometheus_config
WHERE cluster_id IN (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_event_log
WHERE cluster_id IN (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_cluster_metrics_snapshot
WHERE cluster_id IN (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_cluster_component
WHERE cluster_id IN (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_k8s_cluster WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_k8s_app_template WHERE name LIKE 'demo-%';
DELETE FROM xnet_aiops_k8s_helm_repo WHERE name LIKE 'demo-%';

DELETE FROM xnet_aiops_mon_alert_history WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_mon_alert_rule WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_mon_notify_group WHERE group_name LIKE '演示-%';

DELETE FROM xnet_aiops_svm_command_host_role
WHERE command_host_id IN (
  SELECT id FROM xnet_aiops_svm_command_host
  WHERE command_id IN (SELECT id FROM xnet_aiops_svm_command WHERE uid LIKE 'demo-aiops-%')
);
DELETE FROM xnet_aiops_svm_command_host
WHERE command_id IN (SELECT id FROM xnet_aiops_svm_command WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_svm_command WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_svm_role_instance WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_svm_service_instance WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_svm_role_def
WHERE service_def_id IN (
  SELECT id FROM xnet_aiops_svm_service_def
  WHERE framework_id IN (SELECT id FROM xnet_aiops_svm_framework WHERE frame_code LIKE 'demo-%')
);
DELETE FROM xnet_aiops_svm_service_def
WHERE framework_id IN (SELECT id FROM xnet_aiops_svm_framework WHERE frame_code LIKE 'demo-%');
DELETE FROM xnet_aiops_svm_framework WHERE frame_code LIKE 'demo-%';

DELETE FROM xnet_aiops_usr_user_role_cluster
WHERE user_id IN (SELECT id FROM xnet_aiops_usr_user WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_hom_rack
WHERE cluster_id IN (SELECT id FROM xnet_aiops_clm_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_hom_host WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_clm_variable
WHERE cluster_id IN (SELECT id FROM xnet_aiops_clm_cluster WHERE uid LIKE 'demo-aiops-%');
DELETE FROM xnet_aiops_clm_mysql_instance WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_clm_redis_instance WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_clm_hadoop_cluster WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_clm_jenkins_node WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_clm_jenkins_master WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_clm_cluster WHERE uid LIKE 'demo-aiops-%';
DELETE FROM xnet_aiops_usr_user WHERE uid LIKE 'demo-aiops-%';

-- USR: public showcase users and tenant roles.
INSERT INTO xnet_aiops_usr_role (role_name, role_code, description)
VALUES
  ('管理员', 'ADMIN', '系统管理员，拥有所有演示模块权限'),
  ('运维人员', 'OPERATOR', '负责集群、主机、服务和发布操作'),
  ('观察者', 'VIEWER', '只读查看监控、资源和运行状态')
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  description = VALUES(description);

INSERT INTO xnet_aiops_usr_user
  (uid, username, password, email, phone, user_type, status, last_login_at, created_at)
VALUES
  ('demo-aiops-user-admin', 'demo_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6CQARaY1.k0YGKISbVFnTUjXS',
   'admin@demo.example', '12345678900', 'admin', 'active', NOW() - INTERVAL 5 MINUTE, NOW() - INTERVAL 120 DAY),
  ('demo-aiops-user-operator', 'demo_operator', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6CQARaY1.k0YGKISbVFnTUjXS',
   'operator@demo.example', '12345678901', 'user', 'active', NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 96 DAY),
  ('demo-aiops-user-viewer', 'demo_viewer', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6CQARaY1.k0YGKISbVFnTUjXS',
   'viewer@demo.example', '12345678902', 'user', 'active', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 70 DAY);

SET @aiops_admin = (SELECT id FROM xnet_aiops_usr_user WHERE uid = 'demo-aiops-user-admin');
SET @aiops_operator = (SELECT id FROM xnet_aiops_usr_user WHERE uid = 'demo-aiops-user-operator');
SET @aiops_viewer = (SELECT id FROM xnet_aiops_usr_user WHERE uid = 'demo-aiops-user-viewer');
SET @aiops_role_admin = (SELECT id FROM xnet_aiops_usr_role WHERE role_code = 'ADMIN');
SET @aiops_role_operator = (SELECT id FROM xnet_aiops_usr_role WHERE role_code = 'OPERATOR');
SET @aiops_role_viewer = (SELECT id FROM xnet_aiops_usr_role WHERE role_code = 'VIEWER');

-- CLM: three infrastructure environments.
INSERT INTO xnet_aiops_clm_cluster
  (uid, cluster_name, cluster_code, description, cluster_type, status, total_hosts,
   running_services, created_by, created_at)
VALUES
  ('demo-aiops-cluster-prod', '生产数据平台', 'DEMO-PROD-DATA', '承载核心数据与实时计算服务', 'hadoop', 'running', 5, 4, 'demo_admin', NOW() - INTERVAL 180 DAY),
  ('demo-aiops-cluster-realtime', '实时计算平台', 'DEMO-REALTIME', '面向流式任务与消息服务', 'custom', 'running', 3, 3, 'demo_operator', NOW() - INTERVAL 120 DAY),
  ('demo-aiops-cluster-dev', '研发验证环境', 'DEMO-DEV-LAB', '用于版本验证和自动化演练', 'custom', 'configuring', 2, 1, 'demo_operator', NOW() - INTERVAL 60 DAY);

SET @cluster_prod = (SELECT id FROM xnet_aiops_clm_cluster WHERE uid = 'demo-aiops-cluster-prod');
SET @cluster_realtime = (SELECT id FROM xnet_aiops_clm_cluster WHERE uid = 'demo-aiops-cluster-realtime');
SET @cluster_dev = (SELECT id FROM xnet_aiops_clm_cluster WHERE uid = 'demo-aiops-cluster-dev');

INSERT INTO xnet_aiops_clm_variable (cluster_id, variable_name, variable_value)
VALUES
  (@cluster_prod, 'environment', 'production'),
  (@cluster_prod, 'region', 'demo-region-a'),
  (@cluster_prod, 'maintenance_window', 'Sunday 02:00-04:00'),
  (@cluster_realtime, 'environment', 'production'),
  (@cluster_realtime, 'region', 'demo-region-b'),
  (@cluster_dev, 'environment', 'development');

INSERT INTO xnet_aiops_clm_mysql_instance
  (uid, instance_name, cluster_id, host, mysql_port, mysql_version, role, server_id,
   status, last_heartbeat, description, created_by, created_at)
VALUES
  ('demo-aiops-mysql-primary', '业务配置 MySQL 主库', @cluster_prod, '192.168.100.21', 3306, '8.4', 'master', 101,
   'running', NOW() - INTERVAL 1 MINUTE, '演示主库，不包含真实连接凭据', 'demo_admin', NOW() - INTERVAL 150 DAY),
  ('demo-aiops-mysql-replica', '业务配置 MySQL 从库', @cluster_prod, '192.168.100.22', 3306, '8.4', 'slave', 102,
   'running', NOW() - INTERVAL 2 MINUTE, '演示只读副本', 'demo_operator', NOW() - INTERVAL 148 DAY);

SET @mysql_primary = (SELECT id FROM xnet_aiops_clm_mysql_instance WHERE uid = 'demo-aiops-mysql-primary');
UPDATE xnet_aiops_clm_mysql_instance
SET master_instance_id = @mysql_primary
WHERE uid = 'demo-aiops-mysql-replica';

INSERT INTO xnet_aiops_clm_redis_instance
  (uid, instance_name, cluster_id, host, redis_port, redis_version, max_memory,
   persistence_mode, deploy_mode, role, status, last_heartbeat, description, created_by, created_at)
VALUES
  ('demo-aiops-redis-primary', '缓存 Redis 主节点', @cluster_realtime, '192.168.100.31', 6379, '7.4', 4096, 'both', 'sentinel', 'master', 'running', NOW() - INTERVAL 1 MINUTE, '实时业务缓存', 'demo_admin', NOW() - INTERVAL 110 DAY),
  ('demo-aiops-redis-replica', '缓存 Redis 副本', @cluster_realtime, '192.168.100.32', 6379, '7.4', 4096, 'aof', 'sentinel', 'slave', 'running', NOW() - INTERVAL 2 MINUTE, '高可用只读副本', 'demo_operator', NOW() - INTERVAL 109 DAY);

SET @redis_primary = (SELECT id FROM xnet_aiops_clm_redis_instance WHERE uid = 'demo-aiops-redis-primary');
UPDATE xnet_aiops_clm_redis_instance
SET master_instance_id = @redis_primary
WHERE uid = 'demo-aiops-redis-replica';

INSERT INTO xnet_aiops_clm_hadoop_version
  (version, version_type, release_date, download_url, is_latest)
VALUES
  ('3.3.6-demo', 'stable', '2023-06-18', 'https://downloads.example/hadoop-3.3.6.tar.gz', 1)
ON DUPLICATE KEY UPDATE is_latest = VALUES(is_latest);

INSERT INTO xnet_aiops_clm_hadoop_cluster
  (uid, name, description, host, port, ssh_user, hadoop_version, os_type, node_type,
   deploy_mode, components, hdfs_data_dirs, hdfs_replication, yarn_memory, yarn_cpu,
   status, created_by, created_at)
VALUES
  ('demo-aiops-hadoop-master', 'Hadoop Master 01', 'HDFS/YARN 管理节点', '192.168.100.21', 22, 'demo', '3.3.6-demo', 'ubuntu22', 'master',
   'ha', '["hdfs","yarn","mapreduce"]', '["/data/hdfs-1","/data/hdfs-2"]', 3, 32768, 16, 'running', 'demo_admin', NOW() - INTERVAL 140 DAY),
  ('demo-aiops-hadoop-worker-1', 'Hadoop Worker 01', '计算与存储工作节点', '192.168.100.22', 22, 'demo', '3.3.6-demo', 'ubuntu22', 'node',
   'ha', '["datanode","nodemanager"]', '["/data/hdfs-1","/data/hdfs-2"]', 3, 65536, 24, 'running', 'demo_operator', NOW() - INTERVAL 139 DAY),
  ('demo-aiops-hadoop-worker-2', 'Hadoop Worker 02', '计算与存储工作节点', '192.168.100.23', 22, 'demo', '3.3.6-demo', 'ubuntu22', 'node',
   'ha', '["datanode","nodemanager"]', '["/data/hdfs-1","/data/hdfs-2"]', 3, 65536, 24, 'running', 'demo_operator', NOW() - INTERVAL 139 DAY);

SET @hadoop_master = (SELECT id FROM xnet_aiops_clm_hadoop_cluster WHERE uid = 'demo-aiops-hadoop-master');
UPDATE xnet_aiops_clm_hadoop_cluster
SET master_id = @hadoop_master
WHERE uid IN ('demo-aiops-hadoop-worker-1', 'demo-aiops-hadoop-worker-2');

INSERT INTO xnet_aiops_clm_jenkins_version
  (version, version_type, release_date, download_url, is_lts, is_latest)
VALUES
  ('2.492.2-demo', 'stable', '2026-03-01', 'https://updates.example/jenkins.war', 1, 1)
ON DUPLICATE KEY UPDATE is_latest = VALUES(is_latest);

INSERT INTO xnet_aiops_clm_jenkins_master
  (uid, name, host, port, username, os_type, jenkins_port, jenkins_home,
   jenkins_version, java_version, admin_username, status, region, cpu_cores,
   ram_gb, disk_gb, tenant_uid, description, created_by, created_at)
VALUES
  ('demo-aiops-jenkins-master', '持续交付控制器', '192.168.100.41', 22, 'demo', 'linux', 8080, '/var/jenkins_home',
   '2.492.2-demo', '17', 'demo_admin', 'running', 'demo-region-a', 8, 16, 200,
   'demo-tenant-platform', '演示 Jenkins 控制器，不包含有效凭据', 'demo_admin', NOW() - INTERVAL 100 DAY);

INSERT INTO xnet_aiops_clm_jenkins_node
  (uid, name, host, port, username, os_type, region, container_type, resource_type,
   resource_spec, cpu_cores, ram_gb, gpu_memory, gpu_model, gpu_count, status,
   jenkins_url, agent_name, work_dir, java_version, python_version, agent_version,
   labels, description, created_by, created_at)
VALUES
  ('demo-aiops-jenkins-node-cpu', 'CPU 构建节点', '192.168.100.42', 22, 'demo', 'linux', 'demo-region-a', 'docker', 'cpu',
   '16C32G', 16, 32, 0, NULL, 0, 'online', 'https://jenkins.demo.example', 'builder-cpu-01', '/opt/jenkins', '17', '3.12', '1.0',
   'linux,docker,cpu', 'Java 与容器镜像构建节点', 'demo_operator', NOW() - INTERVAL 98 DAY),
  ('demo-aiops-jenkins-node-gpu', 'GPU 验证节点', '192.168.100.43', 22, 'demo', 'linux', 'demo-region-a', 'docker', 'single_gpu',
   '16C64G-A10', 16, 64, 24, 'A10', 1, 'online', 'https://jenkins.demo.example', 'builder-gpu-01', '/opt/jenkins', '17', '3.12', '1.0',
   'linux,docker,gpu', '模型镜像验证节点', 'demo_operator', NOW() - INTERVAL 90 DAY);

-- HOM: hosts and racks with realistic capacity metrics.
INSERT INTO xnet_aiops_hom_host
  (uid, cluster_id, hostname, ip_address, ssh_port, ssh_user, auth_type, os_type,
   os_version, cpu_arch, cpu_cores, total_mem_gb, total_disk_gb, used_mem_gb,
   used_disk_gb, cpu_usage, rack, node_label, status, agent_status, last_heartbeat, created_at)
VALUES
  ('demo-aiops-host-prod-1', @cluster_prod, 'prod-master-01', '192.168.100.21', 22, 'demo', 'password', 'Linux', 'Ubuntu 22.04', 'x86_64', 16, 64, 2048, 31.6, 880, 37.2, 'Rack-A1', 'master', 'online', 'running', NOW() - INTERVAL 40 SECOND, NOW() - INTERVAL 180 DAY),
  ('demo-aiops-host-prod-2', @cluster_prod, 'prod-worker-01', '192.168.100.22', 22, 'demo', 'password', 'Linux', 'Ubuntu 22.04', 'x86_64', 24, 128, 4096, 72.1, 1840, 58.4, 'Rack-A1', 'worker', 'online', 'running', NOW() - INTERVAL 35 SECOND, NOW() - INTERVAL 175 DAY),
  ('demo-aiops-host-prod-3', @cluster_prod, 'prod-worker-02', '192.168.100.23', 22, 'demo', 'password', 'Linux', 'Ubuntu 22.04', 'x86_64', 24, 128, 4096, 68.8, 1760, 52.7, 'Rack-A2', 'worker', 'online', 'running', NOW() - INTERVAL 50 SECOND, NOW() - INTERVAL 175 DAY),
  ('demo-aiops-host-rt-1', @cluster_realtime, 'realtime-01', '192.168.100.31', 22, 'demo', 'password', 'Linux', 'Rocky Linux 9', 'x86_64', 16, 64, 1024, 38.2, 490, 44.6, 'Rack-B1', 'stream', 'online', 'running', NOW() - INTERVAL 55 SECOND, NOW() - INTERVAL 120 DAY),
  ('demo-aiops-host-dev-1', @cluster_dev, 'dev-automation-01', '192.168.100.41', 22, 'demo', 'password', 'Linux', 'Ubuntu 24.04', 'x86_64', 8, 32, 512, 12.4, 188, 18.5, 'Rack-C1', 'automation', 'online', 'running', NOW() - INTERVAL 45 SECOND, NOW() - INTERVAL 60 DAY);

SET @host_prod_1 = (SELECT id FROM xnet_aiops_hom_host WHERE uid = 'demo-aiops-host-prod-1');
SET @host_prod_2 = (SELECT id FROM xnet_aiops_hom_host WHERE uid = 'demo-aiops-host-prod-2');
SET @host_prod_3 = (SELECT id FROM xnet_aiops_hom_host WHERE uid = 'demo-aiops-host-prod-3');
SET @host_rt_1 = (SELECT id FROM xnet_aiops_hom_host WHERE uid = 'demo-aiops-host-rt-1');
SET @host_dev_1 = (SELECT id FROM xnet_aiops_hom_host WHERE uid = 'demo-aiops-host-dev-1');

INSERT INTO xnet_aiops_hom_rack (cluster_id, rack_name, description)
VALUES
  (@cluster_prod, 'Rack-A1', '生产区核心计算机架'),
  (@cluster_prod, 'Rack-A2', '生产区扩展存储机架'),
  (@cluster_realtime, 'Rack-B1', '实时计算机架'),
  (@cluster_dev, 'Rack-C1', '研发验证机架');

-- SVM: frameworks, services, roles and command history.
INSERT INTO xnet_aiops_svm_framework
  (frame_name, frame_code, frame_version, description, created_at)
VALUES
  ('Hadoop 生态', 'demo-hadoop', '3.3.6', 'HDFS、YARN 与 MapReduce 服务定义', NOW() - INTERVAL 160 DAY),
  ('实时计算', 'demo-streaming', '1.19', 'Flink 与 Kafka 实时计算服务定义', NOW() - INTERVAL 130 DAY),
  ('可观测平台', 'demo-observability', '2026.1', 'Prometheus 与 Grafana 服务定义', NOW() - INTERVAL 90 DAY);

SET @fw_hadoop = (SELECT id FROM xnet_aiops_svm_framework WHERE frame_code = 'demo-hadoop');
SET @fw_stream = (SELECT id FROM xnet_aiops_svm_framework WHERE frame_code = 'demo-streaming');
SET @fw_obs = (SELECT id FROM xnet_aiops_svm_framework WHERE frame_code = 'demo-observability');

INSERT INTO xnet_aiops_svm_service_def
  (framework_id, service_name, service_label, service_version, description,
   dependencies, package_name, config_json, sort_order)
VALUES
  (@fw_hadoop, 'HDFS', '分布式文件系统', '3.3.6', '生产数据平台统一存储', '[]', 'hadoop-3.3.6.tar.gz', '{"replication":3}', 1),
  (@fw_hadoop, 'YARN', '资源调度', '3.3.6', '批处理计算资源管理', '["HDFS"]', 'hadoop-3.3.6.tar.gz', '{"scheduler":"capacity"}', 2),
  (@fw_stream, 'Flink', '流式计算', '1.19.1', '实时任务运行平台', '[]', 'flink-1.19.1.tar.gz', '{"slots":16}', 1),
  (@fw_obs, 'Prometheus', '指标采集', '2.53', '基础设施与服务指标采集', '[]', 'prometheus-2.53.tar.gz', '{"retention":"30d"}', 1);

SET @svcdef_hdfs = (SELECT id FROM xnet_aiops_svm_service_def WHERE framework_id = @fw_hadoop AND service_name = 'HDFS');
SET @svcdef_yarn = (SELECT id FROM xnet_aiops_svm_service_def WHERE framework_id = @fw_hadoop AND service_name = 'YARN');
SET @svcdef_flink = (SELECT id FROM xnet_aiops_svm_service_def WHERE framework_id = @fw_stream AND service_name = 'Flink');
SET @svcdef_prom = (SELECT id FROM xnet_aiops_svm_service_def WHERE framework_id = @fw_obs AND service_name = 'Prometheus');

INSERT INTO xnet_aiops_svm_role_def
  (service_def_id, role_name, role_type, cardinality, jmx_port, log_file)
VALUES
  (@svcdef_hdfs, 'NameNode', 'master', '1+', 9870, '/var/log/hadoop/hdfs-namenode.log'),
  (@svcdef_hdfs, 'DataNode', 'worker', '1+', 9864, '/var/log/hadoop/hdfs-datanode.log'),
  (@svcdef_yarn, 'ResourceManager', 'master', '1', 8088, '/var/log/hadoop/yarn-resourcemanager.log'),
  (@svcdef_yarn, 'NodeManager', 'worker', '1+', 8042, '/var/log/hadoop/yarn-nodemanager.log'),
  (@svcdef_flink, 'JobManager', 'master', '1', 9249, '/var/log/flink/jobmanager.log'),
  (@svcdef_flink, 'TaskManager', 'worker', '1+', 9250, '/var/log/flink/taskmanager.log'),
  (@svcdef_prom, 'PrometheusServer', 'master', '1', 9090, '/var/log/prometheus/prometheus.log');

SET @role_nn = (SELECT id FROM xnet_aiops_svm_role_def WHERE service_def_id = @svcdef_hdfs AND role_name = 'NameNode');
SET @role_dn = (SELECT id FROM xnet_aiops_svm_role_def WHERE service_def_id = @svcdef_hdfs AND role_name = 'DataNode');
SET @role_rm = (SELECT id FROM xnet_aiops_svm_role_def WHERE service_def_id = @svcdef_yarn AND role_name = 'ResourceManager');
SET @role_nm = (SELECT id FROM xnet_aiops_svm_role_def WHERE service_def_id = @svcdef_yarn AND role_name = 'NodeManager');
SET @role_jm = (SELECT id FROM xnet_aiops_svm_role_def WHERE service_def_id = @svcdef_flink AND role_name = 'JobManager');
SET @role_tm = (SELECT id FROM xnet_aiops_svm_role_def WHERE service_def_id = @svcdef_flink AND role_name = 'TaskManager');

INSERT INTO xnet_aiops_svm_service_instance
  (uid, cluster_id, service_def_id, service_name, status, config_json, config_version,
   need_restart, created_at)
VALUES
  ('demo-aiops-service-hdfs', @cluster_prod, @svcdef_hdfs, 'HDFS', 'running', '{"replication":3,"ha":true}', 12, 0, NOW() - INTERVAL 150 DAY),
  ('demo-aiops-service-yarn', @cluster_prod, @svcdef_yarn, 'YARN', 'running', '{"scheduler":"capacity"}', 8, 0, NOW() - INTERVAL 145 DAY),
  ('demo-aiops-service-flink', @cluster_realtime, @svcdef_flink, 'Flink', 'running', '{"slots":16,"checkpoint":"s3"}', 6, 0, NOW() - INTERVAL 108 DAY),
  ('demo-aiops-service-prom', @cluster_prod, @svcdef_prom, 'Prometheus', 'running', '{"retention":"30d"}', 4, 1, NOW() - INTERVAL 80 DAY);

SET @svc_hdfs = (SELECT id FROM xnet_aiops_svm_service_instance WHERE uid = 'demo-aiops-service-hdfs');
SET @svc_yarn = (SELECT id FROM xnet_aiops_svm_service_instance WHERE uid = 'demo-aiops-service-yarn');
SET @svc_flink = (SELECT id FROM xnet_aiops_svm_service_instance WHERE uid = 'demo-aiops-service-flink');
SET @svc_prom = (SELECT id FROM xnet_aiops_svm_service_instance WHERE uid = 'demo-aiops-service-prom');

INSERT INTO xnet_aiops_svm_role_instance
  (uid, service_instance_id, role_def_id, role_name, role_type, host_id, hostname,
   status, need_restart, created_at)
VALUES
  ('demo-aiops-role-nn', @svc_hdfs, @role_nn, 'NameNode', 'master', @host_prod_1, 'prod-master-01', 'running', 0, NOW() - INTERVAL 150 DAY),
  ('demo-aiops-role-dn-1', @svc_hdfs, @role_dn, 'DataNode', 'worker', @host_prod_2, 'prod-worker-01', 'running', 0, NOW() - INTERVAL 150 DAY),
  ('demo-aiops-role-dn-2', @svc_hdfs, @role_dn, 'DataNode', 'worker', @host_prod_3, 'prod-worker-02', 'running', 0, NOW() - INTERVAL 150 DAY),
  ('demo-aiops-role-rm', @svc_yarn, @role_rm, 'ResourceManager', 'master', @host_prod_1, 'prod-master-01', 'running', 0, NOW() - INTERVAL 145 DAY),
  ('demo-aiops-role-nm', @svc_yarn, @role_nm, 'NodeManager', 'worker', @host_prod_2, 'prod-worker-01', 'running', 0, NOW() - INTERVAL 145 DAY),
  ('demo-aiops-role-jm', @svc_flink, @role_jm, 'JobManager', 'master', @host_rt_1, 'realtime-01', 'running', 0, NOW() - INTERVAL 108 DAY);

INSERT INTO xnet_aiops_svm_command
  (uid, cluster_id, command_name, command_type, status, progress, service_instance_id,
   created_by, started_at, finished_at, created_at)
VALUES
  ('demo-aiops-command-1', @cluster_prod, '滚动重启 HDFS DataNode', 'restart', 'success', 100, @svc_hdfs, 'demo_operator', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 11 MINUTE, NOW() - INTERVAL 2 DAY),
  ('demo-aiops-command-2', @cluster_prod, '更新 Prometheus 保留周期', 'config_update', 'success', 100, @svc_prom, 'demo_admin', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 3 MINUTE, NOW() - INTERVAL 1 DAY),
  ('demo-aiops-command-3', @cluster_realtime, '扩容 Flink TaskManager', 'install', 'running', 72, @svc_flink, 'demo_operator', NOW() - INTERVAL 8 MINUTE, NULL, NOW() - INTERVAL 8 MINUTE);

SET @command_1 = (SELECT id FROM xnet_aiops_svm_command WHERE uid = 'demo-aiops-command-1');
SET @command_3 = (SELECT id FROM xnet_aiops_svm_command WHERE uid = 'demo-aiops-command-3');
INSERT INTO xnet_aiops_svm_command_host (command_id, host_id, hostname, status, progress, result_msg)
VALUES
  (@command_1, @host_prod_2, 'prod-worker-01', 'success', 100, 'DataNode 已恢复运行'),
  (@command_1, @host_prod_3, 'prod-worker-02', 'success', 100, 'DataNode 已恢复运行'),
  (@command_3, @host_rt_1, 'realtime-01', 'running', 72, '正在拉取运行包');

SET @command_host = (SELECT id FROM xnet_aiops_svm_command_host WHERE command_id = @command_3 LIMIT 1);
INSERT INTO xnet_aiops_svm_command_host_role
  (command_host_id, role_name, role_type, status, result_msg, started_at, finished_at)
VALUES
  (@command_host, 'TaskManager', 'worker', 'running', '安装进度 72%', NOW() - INTERVAL 8 MINUTE, NULL);

-- MON: dashboard rules and alert history.
INSERT INTO xnet_aiops_mon_alert_rule
  (uid, cluster_id, rule_name, service_name, expression, compare_method,
   threshold_value, alert_level, duration_seconds, enabled, description, created_at)
VALUES
  ('demo-aiops-mon-rule-cpu', @cluster_prod, '主机 CPU 持续高负载', 'node-exporter', 'avg(rate(node_cpu_seconds_total[5m]))', '>', 80, 'warning', 300, 1, 'CPU 使用率连续五分钟超过 80%', NOW() - INTERVAL 60 DAY),
  ('demo-aiops-mon-rule-disk', @cluster_prod, 'HDFS 磁盘容量预警', 'HDFS', 'hdfs_capacity_used_percent', '>', 75, 'warning', 600, 1, '存储使用率超过 75%', NOW() - INTERVAL 55 DAY),
  ('demo-aiops-mon-rule-nn', @cluster_prod, 'NameNode 不可用', 'HDFS', 'up{job="namenode"}', '==', 0, 'critical', 60, 1, 'NameNode 健康检查失败', NOW() - INTERVAL 50 DAY),
  ('demo-aiops-mon-rule-flink', @cluster_realtime, 'Flink Checkpoint 延迟', 'Flink', 'flink_checkpoint_duration_ms', '>', 30000, 'critical', 180, 1, 'Checkpoint 持续时间超过 30 秒', NOW() - INTERVAL 40 DAY);

SET @mon_rule_cpu = (SELECT id FROM xnet_aiops_mon_alert_rule WHERE uid = 'demo-aiops-mon-rule-cpu');
SET @mon_rule_disk = (SELECT id FROM xnet_aiops_mon_alert_rule WHERE uid = 'demo-aiops-mon-rule-disk');
SET @mon_rule_flink = (SELECT id FROM xnet_aiops_mon_alert_rule WHERE uid = 'demo-aiops-mon-rule-flink');
INSERT INTO xnet_aiops_mon_alert_history
  (uid, cluster_id, alert_rule_id, alert_name, hostname, alert_level, alert_info,
   alert_advice, status, triggered_at, resolved_at)
VALUES
  ('demo-aiops-mon-alert-1', @cluster_prod, @mon_rule_cpu, '主机 CPU 持续高负载', 'prod-worker-01', 'warning', 'CPU 使用率 86.4%', '检查批处理任务并评估扩容', 'acknowledged', NOW() - INTERVAL 42 MINUTE, NULL),
  ('demo-aiops-mon-alert-2', @cluster_prod, @mon_rule_disk, 'HDFS 磁盘容量预警', 'prod-worker-02', 'warning', '磁盘使用率 78.2%', '清理历史快照或增加存储节点', 'open', NOW() - INTERVAL 2 HOUR, NULL),
  ('demo-aiops-mon-alert-3', @cluster_realtime, @mon_rule_flink, 'Flink Checkpoint 延迟', 'realtime-01', 'critical', 'Checkpoint 耗时 41.8 秒', '检查对象存储吞吐和反压', 'resolved', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 18 MINUTE);

INSERT INTO xnet_aiops_mon_notify_group (group_name, notify_type, webhook_url, created_at)
VALUES
  ('演示-平台值班组', 'webhook', 'https://alerts.demo.example/platform', NOW() - INTERVAL 80 DAY),
  ('演示-数据平台组', 'email', NULL, NOW() - INTERVAL 75 DAY);

-- K8S: database-backed cluster, monitoring, Helm, deployment and DevOps views.
INSERT INTO xnet_aiops_k8s_cluster
  (uid, name, description, api_server_url, kubeconfig_content, version, node_count,
   namespace_count, status, provider, network_plugin, container_runtime, created_at)
VALUES
  ('demo-aiops-k8s-prod', '生产 Kubernetes', '承载数据服务与平台组件的演示集群', 'https://k8s-prod.demo.example:6443', NULL, 'v1.31.4', 6, 12, 'active', 'self-managed', 'cilium', 'containerd', NOW() - INTERVAL 160 DAY),
  ('demo-aiops-k8s-edge', '边缘计算 Kubernetes', '承载采集网关和轻量服务', 'https://k8s-edge.demo.example:6443', NULL, 'v1.30.8', 3, 7, 'active', 'self-managed', 'calico', 'containerd', NOW() - INTERVAL 95 DAY);

SET @k8s_prod = (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid = 'demo-aiops-k8s-prod');
SET @k8s_edge = (SELECT id FROM xnet_aiops_k8s_cluster WHERE uid = 'demo-aiops-k8s-edge');

INSERT INTO xnet_aiops_k8s_cluster_component
  (cluster_id, component_name, component_type, status, message, checked_at)
VALUES
  (@k8s_prod, 'kube-apiserver', 'kubernetes', 'healthy', '3/3 实例运行正常', NOW() - INTERVAL 1 MINUTE),
  (@k8s_prod, 'etcd', 'kubernetes', 'healthy', '集群仲裁正常', NOW() - INTERVAL 1 MINUTE),
  (@k8s_prod, 'cilium', 'networking', 'healthy', '6/6 节点就绪', NOW() - INTERVAL 2 MINUTE),
  (@k8s_prod, 'prometheus', 'monitoring', 'healthy', '指标采集正常', NOW() - INTERVAL 2 MINUTE),
  (@k8s_edge, 'kube-apiserver', 'kubernetes', 'healthy', '控制面运行正常', NOW() - INTERVAL 2 MINUTE);

INSERT INTO xnet_aiops_k8s_cluster_metrics_snapshot
  (cluster_id, cpu_capacity, cpu_used, memory_capacity, memory_used, pod_capacity,
   pod_used, storage_capacity, storage_used, snapshot_time)
VALUES
  (@k8s_prod, 96, 58.4, 549755813888, 356482285568, 660, 238, 8796093022208, 4837851162214, NOW() - INTERVAL 2 MINUTE),
  (@k8s_edge, 24, 9.6, 137438953472, 64424509440, 330, 72, 2199023255552, 769658139443, NOW() - INTERVAL 3 MINUTE);

INSERT INTO xnet_aiops_k8s_event_log
  (cluster_id, namespace, kind, name, event_type, reason, message, source_component,
   first_timestamp, last_timestamp, count, created_at)
VALUES
  (@k8s_prod, 'data-platform', 'Deployment', 'data-api', 'Normal', 'ScalingReplicaSet', '副本数由 4 扩展到 6', 'deployment-controller', NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 2 HOUR, 1, NOW() - INTERVAL 2 HOUR),
  (@k8s_prod, 'monitoring', 'Pod', 'prometheus-0', 'Warning', 'HighMemory', '内存使用率短时超过 80%', 'metrics-controller', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 8 MINUTE, 3, NOW() - INTERVAL 1 DAY),
  (@k8s_edge, 'gateway', 'DaemonSet', 'collector-agent', 'Normal', 'SuccessfulCreate', '已在 3 个节点创建采集实例', 'daemonset-controller', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 3 HOUR, 3, NOW() - INTERVAL 3 HOUR);

INSERT INTO xnet_aiops_k8s_prometheus_config
  (cluster_id, prometheus_url, auth_type, auth_token, username, password, status, created_at)
VALUES
  (@k8s_prod, 'https://prometheus.demo.example', 'none', NULL, NULL, NULL, 'active', NOW() - INTERVAL 150 DAY),
  (@k8s_edge, 'https://prometheus-edge.demo.example', 'none', NULL, NULL, NULL, 'active', NOW() - INTERVAL 90 DAY);

INSERT INTO xnet_aiops_k8s_helm_repo
  (name, url, description, auth_type, status, last_synced_at)
VALUES
  ('demo-bitnami', 'https://charts.example/bitnami', '演示 Bitnami 镜像仓库', 'none', 'active', NOW() - INTERVAL 12 MINUTE),
  ('demo-observability', 'https://charts.example/observability', '演示可观测组件仓库', 'none', 'active', NOW() - INTERVAL 25 MINUTE);

INSERT INTO xnet_aiops_k8s_helm_release
  (cluster_id, namespace, release_name, chart_name, chart_version, app_version,
   values_override, status, revision, notes, created_at)
VALUES
  (@k8s_prod, 'data-platform', 'redis-ha', 'redis', '20.6.2', '7.4', '{"replicas":3}', 'deployed', 5, '缓存集群运行正常', NOW() - INTERVAL 100 DAY),
  (@k8s_prod, 'monitoring', 'kube-prometheus', 'kube-prometheus-stack', '66.3.1', '0.78', '{"retention":"30d"}', 'deployed', 12, '监控栈运行正常', NOW() - INTERVAL 140 DAY),
  (@k8s_edge, 'gateway', 'nginx-ingress', 'ingress-nginx', '4.11.3', '1.11', '{"replicas":2}', 'deployed', 3, '边缘入口网关', NOW() - INTERVAL 80 DAY);

INSERT INTO xnet_aiops_k8s_deploy_plan
  (uid, plan_name, k8s_version, deploy_type, network_plugin, container_runtime,
   pod_cidr, service_cidr, install_metrics_server, install_ingress_nginx,
   storage_plugin, registry_url, status, result_cluster_id, created_at)
VALUES
  ('demo-aiops-deploy-plan-1', '边缘集群扩容计划', 'v1.30.8', 'ha', 'calico', 'containerd',
   '10.244.0.0/16', '10.96.0.0/12', 1, 1, 'local-path', NULL, 'success', @k8s_edge, NOW() - INTERVAL 95 DAY),
  ('demo-aiops-deploy-plan-2', '研发集群升级演练', 'v1.31.4', 'single', 'cilium', 'containerd',
   '10.245.0.0/16', '10.97.0.0/12', 1, 1, 'longhorn', NULL, 'validating', NULL, NOW() - INTERVAL 3 DAY);

SET @deploy_plan_1 = (SELECT id FROM xnet_aiops_k8s_deploy_plan WHERE uid = 'demo-aiops-deploy-plan-1');
SET @deploy_plan_2 = (SELECT id FROM xnet_aiops_k8s_deploy_plan WHERE uid = 'demo-aiops-deploy-plan-2');
INSERT INTO xnet_aiops_k8s_deploy_node
  (plan_id, host, ssh_port, ssh_user, role, hostname, status, status_message, created_at)
VALUES
  (@deploy_plan_1, '192.168.100.61', 22, 'demo', 'master', 'edge-master-01', 'ready', '节点安装完成', NOW() - INTERVAL 95 DAY),
  (@deploy_plan_1, '192.168.100.62', 22, 'demo', 'worker', 'edge-worker-01', 'ready', '节点安装完成', NOW() - INTERVAL 95 DAY),
  (@deploy_plan_2, '192.168.100.71', 22, 'demo', 'master', 'dev-k8s-master-01', 'preparing', '正在执行环境检查', NOW() - INTERVAL 3 DAY);

INSERT INTO xnet_aiops_k8s_deploy_log
  (plan_id, node_host, step, log_level, message, created_at)
VALUES
  (@deploy_plan_1, '192.168.100.61', 'control-plane', 'INFO', '控制面初始化完成', NOW() - INTERVAL 95 DAY + INTERVAL 20 MINUTE),
  (@deploy_plan_1, '192.168.100.62', 'join-worker', 'INFO', '工作节点加入完成', NOW() - INTERVAL 95 DAY + INTERVAL 28 MINUTE),
  (@deploy_plan_2, '192.168.100.71', 'preflight', 'INFO', 'CPU、内存和端口检查通过', NOW() - INTERVAL 10 MINUTE);

INSERT INTO xnet_aiops_k8s_alert_rule
  (cluster_id, name, description, severity, resource_type, metric_name, `condition`,
   threshold, duration, enabled, notify_channels, last_triggered, created_at)
VALUES
  (@k8s_prod, '节点 CPU 使用率过高', '节点 CPU 持续高于 85%', 'warning', 'node', 'node_cpu_usage', '>', 85, '5m', 1, '["email","webhook"]', NOW() - INTERVAL 4 HOUR, NOW() - INTERVAL 80 DAY),
  (@k8s_prod, 'Pod 重启次数异常', '单 Pod 一小时重启超过 5 次', 'critical', 'pod', 'pod_restart_count', '>', 5, '10m', 1, '["webhook"]', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 75 DAY),
  (@k8s_edge, '节点离线', '边缘节点心跳中断', 'critical', 'node', 'node_ready', '==', 0, '2m', 1, '["email"]', NULL, NOW() - INTERVAL 60 DAY);

SET @k8s_rule_cpu = (SELECT id FROM xnet_aiops_k8s_alert_rule WHERE cluster_id = @k8s_prod AND name = '节点 CPU 使用率过高' LIMIT 1);
SET @k8s_rule_pod = (SELECT id FROM xnet_aiops_k8s_alert_rule WHERE cluster_id = @k8s_prod AND name = 'Pod 重启次数异常' LIMIT 1);
INSERT INTO xnet_aiops_k8s_alert_history
  (rule_id, cluster_id, rule_name, severity, resource_type, resource_name, message,
   status, current_value, threshold, fired_at, resolved_at, created_at)
VALUES
  (@k8s_rule_cpu, @k8s_prod, '节点 CPU 使用率过高', 'warning', 'node', 'worker-03', 'CPU 使用率达到 88.6%', 'firing', 88.6, 85, NOW() - INTERVAL 35 MINUTE, NULL, NOW() - INTERVAL 35 MINUTE),
  (@k8s_rule_pod, @k8s_prod, 'Pod 重启次数异常', 'critical', 'pod', 'data-api-7d8f9', '一小时内重启 7 次', 'resolved', 7, 5, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY + INTERVAL 22 MINUTE, NOW() - INTERVAL 2 DAY);

INSERT INTO xnet_aiops_k8s_app_template
  (name, display_name, description, category, icon, helm_repo_name, helm_repo_url,
   chart_name, chart_version, default_values, doc_url, is_featured, sort_order)
VALUES
  ('demo-mysql', 'MySQL', '关系型数据库演示模板', 'database', NULL, 'demo-bitnami', 'https://charts.example/bitnami', 'mysql', '12.1.1', 'architecture: standalone', 'https://dev.mysql.com/doc/', 1, 1),
  ('demo-redis', 'Redis', '高性能缓存演示模板', 'database', NULL, 'demo-bitnami', 'https://charts.example/bitnami', 'redis', '20.6.2', 'architecture: replication', 'https://redis.io/docs/', 1, 2),
  ('demo-kafka', 'Kafka', '事件流平台演示模板', 'messaging', NULL, 'demo-bitnami', 'https://charts.example/bitnami', 'kafka', '31.1.0', 'controller.replicaCount: 3', 'https://kafka.apache.org/documentation/', 1, 3),
  ('demo-prometheus', 'Prometheus', '指标监控演示模板', 'monitoring', NULL, 'demo-observability', 'https://charts.example/observability', 'kube-prometheus-stack', '66.3.1', 'prometheus.retention: 30d', 'https://prometheus.io/docs/', 1, 4),
  ('demo-grafana', 'Grafana', '可视化看板演示模板', 'monitoring', NULL, 'demo-observability', 'https://charts.example/observability', 'grafana', '8.8.2', 'persistence.enabled: true', 'https://grafana.com/docs/', 0, 5),
  ('demo-minio', 'MinIO', '对象存储演示模板', 'storage', NULL, 'demo-bitnami', 'https://charts.example/bitnami', 'minio', '14.10.3', 'mode: distributed', 'https://min.io/docs/', 0, 6);

INSERT INTO xnet_aiops_k8s_helm_config (config_key, config_value, description)
VALUES
  ('demoShowcase', 'enabled', '公开演示数据已启用'),
  ('defaultRepoName', 'demo-bitnami', '演示默认 Helm 仓库'),
  ('defaultRepoUrl', 'https://charts.example/bitnami', '演示仓库地址，不用于生产')
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  description = VALUES(description);

INSERT INTO xnet_aiops_k8s_devops_project
  (name, description, jenkins_url, jenkins_user, jenkins_token, cluster_id, status, created_at)
VALUES
  ('演示-数据平台持续交付', '展示从代码检查、镜像构建到 Kubernetes 发布的完整流水线', 'https://jenkins.demo.example', 'demo', 'demo-token-not-valid', @k8s_prod, 'active', NOW() - INTERVAL 90 DAY),
  ('演示-边缘组件发布', '边缘采集组件的多架构构建与灰度发布', 'https://jenkins-edge.demo.example', 'demo', 'demo-token-not-valid', @k8s_edge, 'active', NOW() - INTERVAL 60 DAY);

SET @devops_project_data = (SELECT id FROM xnet_aiops_k8s_devops_project WHERE name = '演示-数据平台持续交付' LIMIT 1);
SET @devops_project_edge = (SELECT id FROM xnet_aiops_k8s_devops_project WHERE name = '演示-边缘组件发布' LIMIT 1);
INSERT INTO xnet_aiops_k8s_credential
  (project_id, name, type, description, username, password, token, created_at)
VALUES
  (@devops_project_data, '演示镜像仓库凭证', 'username-password', '占位凭证，不包含有效密钥', 'demo', NULL, NULL, NOW() - INTERVAL 88 DAY),
  (@devops_project_edge, '演示代码平台令牌', 'access-token', '占位令牌，不可用于任何外部系统', NULL, NULL, 'demo-token-not-valid', NOW() - INTERVAL 58 DAY);

SET @cred_data = (SELECT id FROM xnet_aiops_k8s_credential WHERE project_id = @devops_project_data LIMIT 1);
SET @cred_edge = (SELECT id FROM xnet_aiops_k8s_credential WHERE project_id = @devops_project_edge LIMIT 1);
INSERT INTO xnet_aiops_k8s_pipeline
  (project_id, name, description, type, jenkinsfile, source_type, source_url,
   source_branch, credential_id, disable_concurrent, timer_trigger, jenkins_job_name,
   jenkins_job_path, status, last_run_status, last_run_time, created_at)
VALUES
  (@devops_project_data, 'data-api-release', '数据 API 服务构建与发布', 'pipeline', 'pipeline { stages { stage("Build") { steps { echo "demo" } } } }', 'github', 'https://github.com/synapxnet/XnetDataops', 'display', @cred_data, 1, 'H 2 * * 1-5', 'data-api-release', 'showcase/data-api-release', 'active', 'success', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 85 DAY),
  (@devops_project_edge, 'collector-multiarch', '采集器多架构镜像构建', 'pipeline', 'pipeline { stages { stage("Build") { steps { echo "demo" } } } }', 'github', 'https://github.com/synapxnet/XnetAIops', 'display', @cred_edge, 1, NULL, 'collector-multiarch', 'showcase/collector-multiarch', 'active', 'running', NOW() - INTERVAL 12 MINUTE, NOW() - INTERVAL 55 DAY);

SET @pipeline_data = (SELECT id FROM xnet_aiops_k8s_pipeline WHERE project_id = @devops_project_data AND name = 'data-api-release');
SET @pipeline_edge = (SELECT id FROM xnet_aiops_k8s_pipeline WHERE project_id = @devops_project_edge AND name = 'collector-multiarch');
INSERT INTO xnet_aiops_k8s_pipeline_run
  (pipeline_id, run_number, status, trigger_type, trigger_user, parameters, start_time,
   end_time, duration_ms, stages_status, log_text, jenkins_build_url, created_at)
VALUES
  (@pipeline_data, 128, 'success', 'webhook', 'demo_operator', '[{"name":"branch","value":"display"}]', NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 3 HOUR + INTERVAL 8 MINUTE, 486000, '[{"name":"Build","status":"SUCCESS"},{"name":"Scan","status":"SUCCESS"},{"name":"Deploy","status":"SUCCESS"}]', '演示流水线执行成功', 'https://jenkins.demo.example/job/128', NOW() - INTERVAL 3 HOUR),
  (@pipeline_data, 127, 'success', 'timer', 'scheduler', '[]', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY + INTERVAL 9 MINUTE, 542000, '[{"name":"Build","status":"SUCCESS"},{"name":"Deploy","status":"SUCCESS"}]', '演示流水线执行成功', 'https://jenkins.demo.example/job/127', NOW() - INTERVAL 1 DAY),
  (@pipeline_edge, 46, 'running', 'manual', 'demo_operator', '[{"name":"arch","value":"amd64,arm64"}]', NOW() - INTERVAL 12 MINUTE, NULL, NULL, '[{"name":"Build amd64","status":"SUCCESS"},{"name":"Build arm64","status":"IN_PROGRESS"}]', '正在构建 arm64 镜像', 'https://jenkins-edge.demo.example/job/46', NOW() - INTERVAL 12 MINUTE);

-- REG: registry instances, projects, repositories, tags and sync activity.
INSERT INTO xnet_aiops_reg_registry
  (uid, registry_name, registry_type, description, version, status, deploy_mode,
   host_id, host, ssh_port, ssh_user, install_path, service_port, cluster_id,
   namespace, release_name, endpoint, api_url, admin_user, use_ssl, created_by, created_at)
VALUES
  ('demo-aiops-reg-harbor', '生产 Harbor 镜像仓库', 'harbor', '承载平台服务与数据组件镜像', '2.12.2', 'running', 'k8s',
   NULL, NULL, 22, NULL, NULL, 443, @k8s_prod, 'registry', 'harbor', 'https://harbor.demo.example', 'https://harbor.demo.example/api/v2.0', 'demo_admin', 1, 'demo_admin', NOW() - INTERVAL 145 DAY),
  ('demo-aiops-reg-distribution', '边缘 Docker Registry', 'docker_distribution', '边缘节点轻量镜像分发', '2.8.3', 'running', 'ssh',
   @host_rt_1, '192.168.100.31', 22, 'demo', '/opt/registry', 5000, NULL, NULL, NULL, 'https://registry.demo.example', 'https://registry.demo.example/v2', 'demo_admin', 1, 'demo_operator', NOW() - INTERVAL 85 DAY);

SET @registry_harbor = (SELECT id FROM xnet_aiops_reg_registry WHERE uid = 'demo-aiops-reg-harbor');
SET @registry_edge = (SELECT id FROM xnet_aiops_reg_registry WHERE uid = 'demo-aiops-reg-distribution');
INSERT INTO xnet_aiops_reg_deploy_log
  (registry_id, action, status, log_text, started_at, finished_at)
VALUES
  (@registry_harbor, 'upgrade', 'success', 'Harbor 组件滚动升级完成', NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY + INTERVAL 18 MINUTE),
  (@registry_edge, 'restart', 'success', 'Registry 服务重启完成', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY + INTERVAL 2 MINUTE);

INSERT INTO xnet_aiops_reg_project (registry_id, project_name, visibility, repo_count, created_at)
VALUES
  (@registry_harbor, 'data-platform', 'private', 12, NOW() - INTERVAL 140 DAY),
  (@registry_harbor, 'observability', 'private', 8, NOW() - INTERVAL 130 DAY),
  (@registry_harbor, 'public-mirror', 'public', 16, NOW() - INTERVAL 120 DAY),
  (@registry_edge, 'edge-services', 'private', 6, NOW() - INTERVAL 80 DAY);

SET @project_data = (SELECT id FROM xnet_aiops_reg_project WHERE registry_id = @registry_harbor AND project_name = 'data-platform');
SET @project_obs = (SELECT id FROM xnet_aiops_reg_project WHERE registry_id = @registry_harbor AND project_name = 'observability');
SET @project_edge = (SELECT id FROM xnet_aiops_reg_project WHERE registry_id = @registry_edge AND project_name = 'edge-services');
INSERT INTO xnet_aiops_reg_repository
  (registry_id, project_id, repo_name, tags_count, pull_count, latest_tag, updated_at)
VALUES
  (@registry_harbor, @project_data, 'data-api', 18, 28640, '1.8.3', NOW() - INTERVAL 3 HOUR),
  (@registry_harbor, @project_data, 'data-worker', 24, 19120, '2.4.1', NOW() - INTERVAL 1 DAY),
  (@registry_harbor, @project_obs, 'metrics-agent', 9, 8630, '0.9.5', NOW() - INTERVAL 2 DAY),
  (@registry_edge, @project_edge, 'collector-agent', 12, 4820, '1.3.0', NOW() - INTERVAL 12 MINUTE);

SET @repo_api = (SELECT id FROM xnet_aiops_reg_repository WHERE registry_id = @registry_harbor AND repo_name = 'data-api');
SET @repo_worker = (SELECT id FROM xnet_aiops_reg_repository WHERE registry_id = @registry_harbor AND repo_name = 'data-worker');
SET @repo_collector = (SELECT id FROM xnet_aiops_reg_repository WHERE registry_id = @registry_edge AND repo_name = 'collector-agent');
INSERT INTO xnet_aiops_reg_tag
  (repository_id, tag_name, digest, size_bytes, architecture, os, pushed_at)
VALUES
  (@repo_api, '1.8.3', 'sha256:demo-data-api-183', 268435456, 'amd64', 'linux', NOW() - INTERVAL 3 HOUR),
  (@repo_api, '1.8.2', 'sha256:demo-data-api-182', 266338304, 'amd64', 'linux', NOW() - INTERVAL 3 DAY),
  (@repo_worker, '2.4.1', 'sha256:demo-data-worker-241', 482344960, 'amd64', 'linux', NOW() - INTERVAL 1 DAY),
  (@repo_collector, '1.3.0-amd64', 'sha256:demo-collector-amd64', 125829120, 'amd64', 'linux', NOW() - INTERVAL 14 MINUTE),
  (@repo_collector, '1.3.0-arm64', 'sha256:demo-collector-arm64', 119537664, 'arm64', 'linux', NOW() - INTERVAL 12 MINUTE);

INSERT INTO xnet_aiops_reg_sync_task
  (registry_id, source_image, target_project, sync_method, harbor_policy_id,
   harbor_execution_id, status, status_detail, created_by, created_at)
VALUES
  (@registry_harbor, 'docker.io/library/nginx:1.27-alpine', 'public-mirror', 'harbor_replication', 1001, 5028, 'success', '镜像同步完成', 'demo_operator', NOW() - INTERVAL 4 HOUR),
  (@registry_harbor, 'quay.io/prometheus/prometheus:v2.53.0', 'observability', 'harbor_replication', 1002, 5029, 'success', '镜像同步完成', 'demo_operator', NOW() - INTERVAL 2 HOUR),
  (@registry_edge, 'harbor.demo.example/data-platform/collector-agent:1.3.0', 'edge-services', 'skopeo', NULL, NULL, 'running', '正在同步 arm64 清单', 'demo_operator', NOW() - INTERVAL 6 MINUTE);

-- Complete tenant mappings after clusters are available.
INSERT INTO xnet_aiops_usr_user_role_cluster (user_id, role_id, cluster_id)
VALUES
  (@aiops_admin, @aiops_role_admin, @cluster_prod),
  (@aiops_admin, @aiops_role_admin, @cluster_realtime),
  (@aiops_operator, @aiops_role_operator, @cluster_prod),
  (@aiops_operator, @aiops_role_operator, @cluster_realtime),
  (@aiops_viewer, @aiops_role_viewer, @cluster_prod);

COMMIT;
