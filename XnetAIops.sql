-- ============================================================
-- XnetAIops 智能运维平台 - 数据库建表脚本
-- Database: XnetAIops
-- ============================================================

CREATE DATABASE IF NOT EXISTS XnetAIops DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE XnetAIops;

-- ============================================================
-- CLM - 集群管理模块
-- ============================================================

CREATE TABLE xnet_aiops_clm_cluster (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  cluster_name VARCHAR(100) NOT NULL,
  cluster_code VARCHAR(50) NOT NULL UNIQUE,
  description TEXT,
  cluster_type VARCHAR(50) DEFAULT 'hadoop' COMMENT '集群类型: hadoop/k8s/custom',
  status VARCHAR(20) DEFAULT 'inactive' COMMENT 'inactive/configuring/running/error/stopped',
  total_hosts INT DEFAULT 0,
  running_services INT DEFAULT 0,
  created_by VARCHAR(50),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集群信息表';

CREATE TABLE xnet_aiops_clm_variable (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  variable_name VARCHAR(100) NOT NULL,
  variable_value TEXT,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_clm_cluster(id) ON DELETE CASCADE,
  UNIQUE KEY uk_cluster_var (cluster_id, variable_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集群变量表';

CREATE TABLE xnet_aiops_clm_mysql_instance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  instance_name VARCHAR(100) NOT NULL,
  cluster_id BIGINT,
  host VARCHAR(200) NOT NULL,
  ssh_port INT DEFAULT 22,
  ssh_user VARCHAR(50) DEFAULT 'root',
  auth_type VARCHAR(20) DEFAULT 'password',
  encrypted_password VARCHAR(500),
  encrypted_private_key TEXT,
  mysql_port INT DEFAULT 3306,
  mysql_version VARCHAR(20) DEFAULT '8.0',
  data_dir VARCHAR(200) DEFAULT '/var/lib/mysql',
  charset VARCHAR(20) DEFAULT 'utf8mb4',
  innodb_buffer_pool_size INT DEFAULT 1024 COMMENT 'MB',
  max_connections INT DEFAULT 500,
  encrypted_root_password VARCHAR(500),
  role VARCHAR(20) DEFAULT 'standalone' COMMENT 'standalone/master/slave',
  master_instance_id BIGINT,
  server_id INT DEFAULT 1,
  status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/deploying/deployed/running/stopped/failed',
  deploy_log LONGTEXT,
  last_heartbeat DATETIME,
  description TEXT,
  created_by VARCHAR(50),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MySQL实例表';

CREATE TABLE xnet_aiops_clm_redis_instance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  instance_name VARCHAR(100) NOT NULL,
  cluster_id BIGINT,
  host VARCHAR(200) NOT NULL,
  ssh_port INT DEFAULT 22,
  ssh_user VARCHAR(50) DEFAULT 'root',
  auth_type VARCHAR(20) DEFAULT 'password',
  encrypted_password VARCHAR(500),
  encrypted_private_key TEXT,
  redis_port INT DEFAULT 6379,
  redis_version VARCHAR(20) DEFAULT '7.2',
  encrypted_redis_password VARCHAR(500),
  max_memory INT DEFAULT 1024 COMMENT 'MB',
  max_memory_policy VARCHAR(30) DEFAULT 'noeviction',
  persistence_mode VARCHAR(10) DEFAULT 'rdb' COMMENT 'none/rdb/aof/both',
  data_dir VARCHAR(200) DEFAULT '/var/lib/redis',
  deploy_mode VARCHAR(20) DEFAULT 'standalone' COMMENT 'standalone/sentinel/cluster',
  role VARCHAR(20) DEFAULT 'master' COMMENT 'master/slave/sentinel',
  master_instance_id BIGINT,
  cluster_bus_port INT,
  status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/deploying/deployed/running/stopped/failed',
  deploy_log LONGTEXT,
  last_heartbeat DATETIME,
  description TEXT,
  created_by VARCHAR(50),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Redis实例表';

-- ============================================================
-- HOM - 主机管理模块
-- ============================================================

CREATE TABLE xnet_aiops_hom_host (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  cluster_id BIGINT NOT NULL,
  hostname VARCHAR(200) NOT NULL,
  ip_address VARCHAR(50) NOT NULL,
  ssh_port INT DEFAULT 22,
  ssh_user VARCHAR(50) DEFAULT 'root',
  auth_type VARCHAR(20) DEFAULT 'password' COMMENT 'password/privateKey',
  encrypted_password VARCHAR(500),
  private_key TEXT,
  os_type VARCHAR(50),
  os_version VARCHAR(100),
  cpu_arch VARCHAR(20) COMMENT 'x86_64/aarch64',
  cpu_cores INT,
  total_mem_gb DECIMAL(10,2),
  total_disk_gb DECIMAL(10,2),
  used_mem_gb DECIMAL(10,2),
  used_disk_gb DECIMAL(10,2),
  cpu_usage DECIMAL(5,2),
  rack VARCHAR(50),
  node_label VARCHAR(100),
  status VARCHAR(20) DEFAULT 'unknown' COMMENT 'online/offline/error/unknown',
  agent_status VARCHAR(20) DEFAULT 'not_installed' COMMENT 'not_installed/installing/running/stopped',
  last_heartbeat DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_clm_cluster(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主机信息表';

CREATE TABLE xnet_aiops_hom_rack (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  rack_name VARCHAR(50) NOT NULL,
  description VARCHAR(200),
  UNIQUE KEY uk_cluster_rack (cluster_id, rack_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机架信息表';

-- ============================================================
-- SVM - 服务管理模块
-- ============================================================

CREATE TABLE xnet_aiops_svm_framework (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  frame_name VARCHAR(100) NOT NULL,
  frame_code VARCHAR(50) NOT NULL UNIQUE,
  frame_version VARCHAR(50) NOT NULL,
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务框架表';

CREATE TABLE xnet_aiops_svm_service_def (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  framework_id BIGINT NOT NULL,
  service_name VARCHAR(100) NOT NULL,
  service_label VARCHAR(100),
  service_version VARCHAR(50),
  description TEXT,
  dependencies VARCHAR(500) COMMENT 'JSON数组: 依赖的其他服务名',
  package_name VARCHAR(200),
  config_json LONGTEXT COMMENT '服务配置模板JSON',
  sort_order INT DEFAULT 0,
  FOREIGN KEY (framework_id) REFERENCES xnet_aiops_svm_framework(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='框架服务定义表';

CREATE TABLE xnet_aiops_svm_role_def (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_def_id BIGINT NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  role_type VARCHAR(20) COMMENT 'master/worker/client',
  cardinality VARCHAR(20) COMMENT '1/1+/0+/ALL',
  jmx_port INT,
  log_file VARCHAR(500),
  FOREIGN KEY (service_def_id) REFERENCES xnet_aiops_svm_service_def(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务角色定义表';

CREATE TABLE xnet_aiops_svm_service_instance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  cluster_id BIGINT NOT NULL,
  service_def_id BIGINT NOT NULL,
  service_name VARCHAR(100) NOT NULL,
  status VARCHAR(20) DEFAULT 'not_installed' COMMENT 'not_installed/installing/running/stopped/error',
  config_json LONGTEXT,
  config_version INT DEFAULT 1,
  need_restart BOOLEAN DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集群服务实例表';

CREATE TABLE xnet_aiops_svm_role_instance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  service_instance_id BIGINT NOT NULL,
  role_def_id BIGINT NOT NULL,
  role_name VARCHAR(100) NOT NULL,
  role_type VARCHAR(20),
  host_id BIGINT NOT NULL,
  hostname VARCHAR(200),
  status VARCHAR(20) DEFAULT 'stopped' COMMENT 'running/stopped/error/installing',
  need_restart BOOLEAN DEFAULT FALSE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (service_instance_id) REFERENCES xnet_aiops_svm_service_instance(id) ON DELETE CASCADE,
  FOREIGN KEY (host_id) REFERENCES xnet_aiops_hom_host(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务角色实例表';

CREATE TABLE xnet_aiops_svm_command (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  cluster_id BIGINT NOT NULL,
  command_name VARCHAR(200) NOT NULL,
  command_type VARCHAR(30) COMMENT 'install/start/stop/restart/config_update',
  status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/running/success/failed/cancelled',
  progress INT DEFAULT 0 COMMENT '进度百分比 0-100',
  service_instance_id BIGINT,
  created_by VARCHAR(50),
  started_at DATETIME,
  finished_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作指令表';

CREATE TABLE xnet_aiops_svm_command_host (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  command_id BIGINT NOT NULL,
  host_id BIGINT NOT NULL,
  hostname VARCHAR(200),
  status VARCHAR(20) DEFAULT 'pending',
  progress INT DEFAULT 0,
  result_msg TEXT,
  FOREIGN KEY (command_id) REFERENCES xnet_aiops_svm_command(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指令-主机表';

CREATE TABLE xnet_aiops_svm_command_host_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  command_host_id BIGINT NOT NULL,
  role_name VARCHAR(100),
  role_type VARCHAR(20),
  status VARCHAR(20) DEFAULT 'pending',
  result_msg TEXT,
  started_at DATETIME,
  finished_at DATETIME,
  FOREIGN KEY (command_host_id) REFERENCES xnet_aiops_svm_command_host(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='指令-主机-角色表';

-- ============================================================
-- MON - 监控告警模块
-- ============================================================

CREATE TABLE xnet_aiops_mon_alert_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  cluster_id BIGINT NOT NULL,
  rule_name VARCHAR(200) NOT NULL,
  service_name VARCHAR(100),
  expression TEXT NOT NULL COMMENT 'PromQL 表达式',
  compare_method VARCHAR(10) COMMENT '>/>=/</<=/==/!=',
  threshold_value DECIMAL(20,4),
  alert_level VARCHAR(20) DEFAULT 'warning' COMMENT 'info/warning/critical',
  duration_seconds INT DEFAULT 60 COMMENT '持续时间触发',
  enabled BOOLEAN DEFAULT TRUE,
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则表';

CREATE TABLE xnet_aiops_mon_alert_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  cluster_id BIGINT NOT NULL,
  alert_rule_id BIGINT,
  alert_name VARCHAR(200),
  hostname VARCHAR(200),
  alert_level VARCHAR(20),
  alert_info TEXT,
  alert_advice TEXT,
  status VARCHAR(20) DEFAULT 'open' COMMENT 'open/acknowledged/resolved',
  triggered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  resolved_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警历史表';

CREATE TABLE xnet_aiops_mon_notify_group (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  group_name VARCHAR(100) NOT NULL,
  notify_type VARCHAR(20) DEFAULT 'email' COMMENT 'email/webhook/sms',
  webhook_url VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警通知组表';

-- ============================================================
-- USR - 用户权限模块
-- ============================================================

CREATE TABLE xnet_aiops_usr_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(200) NOT NULL COMMENT 'BCrypt加密',
  email VARCHAR(100),
  phone VARCHAR(20),
  user_type VARCHAR(20) DEFAULT 'user' COMMENT 'admin/user',
  status VARCHAR(20) DEFAULT 'active' COMMENT 'active/disabled',
  last_login_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE xnet_aiops_usr_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(50) NOT NULL UNIQUE,
  role_code VARCHAR(50) NOT NULL UNIQUE,
  description VARCHAR(200),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE xnet_aiops_usr_user_role_cluster (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  cluster_id BIGINT,
  UNIQUE KEY uk_user_role_cluster (user_id, role_id, cluster_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色-集群映射表';

CREATE TABLE xnet_aiops_usr_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token VARCHAR(500) NOT NULL,
  ip VARCHAR(50),
  expire_at DATETIME NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- ============================================================
-- CLM - Hadoop 集群节点表
-- ============================================================
CREATE TABLE IF NOT EXISTS xnet_aiops_clm_hadoop_cluster (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  description TEXT,
  host VARCHAR(256) NOT NULL,
  port INT DEFAULT 22,
  ssh_user VARCHAR(64) NOT NULL,
  ssh_password TEXT,
  ssh_private_key TEXT,
  hadoop_version VARCHAR(32),
  os_type VARCHAR(32) DEFAULT 'centos7',
  node_type VARCHAR(16) NOT NULL DEFAULT 'master' COMMENT 'master / node',
  deploy_mode VARCHAR(16) DEFAULT 'standard' COMMENT 'standard / ha',
  components TEXT COMMENT 'JSON array: ["hdfs","yarn","mapreduce"]',
  hdfs_data_dirs TEXT COMMENT 'JSON array: ["/data1","/data2"]',
  hdfs_replication INT DEFAULT 3,
  hdfs_block_size BIGINT DEFAULT 134217728,
  yarn_memory INT DEFAULT 8192 COMMENT 'MB',
  yarn_cpu INT DEFAULT 8,
  ha_master_host VARCHAR(256),
  zk_cluster VARCHAR(512),
  status VARCHAR(32) DEFAULT 'created',
  deploy_log LONGTEXT,
  master_id BIGINT COMMENT '关联的 Master 节点 ID (仅 node 类型)',
  created_by VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_node_type (node_type),
  INDEX idx_status (status),
  INDEX idx_master_id (master_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Hadoop 集群节点表';

-- ============================================================
-- CLM - Hadoop 版本表
-- ============================================================
CREATE TABLE IF NOT EXISTS xnet_aiops_clm_hadoop_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version VARCHAR(32) NOT NULL UNIQUE,
  version_type VARCHAR(16) DEFAULT 'stable' COMMENT 'stable / alpha / beta',
  release_date DATE,
  download_url TEXT,
  is_latest TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_version_type (version_type),
  INDEX idx_is_latest (is_latest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Hadoop 版本表';

-- ============================================================
-- CLM - Jenkins Master 表
-- ============================================================
CREATE TABLE IF NOT EXISTS xnet_aiops_clm_jenkins_master (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  host VARCHAR(256) NOT NULL,
  port INT DEFAULT 22,
  username VARCHAR(64) NOT NULL,
  encrypted_password TEXT COMMENT 'AES加密的SSH密码',
  os_type VARCHAR(16) DEFAULT 'linux',
  jenkins_port INT DEFAULT 8080,
  jenkins_home VARCHAR(256) DEFAULT '/var/jenkins_home',
  jenkins_version VARCHAR(32),
  java_version VARCHAR(8),
  java_opts VARCHAR(512),
  admin_username VARCHAR(64),
  encrypted_admin_password TEXT COMMENT 'AES加密的管理员密码',
  credentials_config TEXT COMMENT 'JSON格式凭证配置',
  status VARCHAR(32) DEFAULT 'pending',
  initial_password VARCHAR(256),
  deploy_log LONGTEXT,
  last_heartbeat DATETIME,
  region VARCHAR(32),
  cpu_cores INT,
  ram_gb INT,
  disk_gb INT,
  tenant_uid VARCHAR(64),
  description TEXT,
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_tenant (tenant_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Jenkins Master 表';

-- ============================================================
-- CLM - Jenkins Node (Agent) 表
-- ============================================================
CREATE TABLE IF NOT EXISTS xnet_aiops_clm_jenkins_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  host VARCHAR(256) NOT NULL,
  port INT DEFAULT 22,
  username VARCHAR(64) NOT NULL,
  encrypted_password TEXT COMMENT 'AES加密的SSH密码',
  os_type VARCHAR(16) DEFAULT 'linux',
  region VARCHAR(32),
  container_type VARCHAR(16) COMMENT 'cce / docker',
  resource_type VARCHAR(32) COMMENT 'cpu / single_gpu / multi_gpu',
  resource_spec VARCHAR(64),
  cpu_cores INT,
  ram_gb INT,
  gpu_memory INT,
  gpu_model VARCHAR(64),
  gpu_count INT,
  status VARCHAR(32) DEFAULT 'pending',
  jenkins_url VARCHAR(512),
  agent_name VARCHAR(128),
  work_dir VARCHAR(256),
  java_version VARCHAR(8),
  python_version VARCHAR(8),
  agent_version VARCHAR(32),
  labels VARCHAR(512),
  description TEXT,
  deploy_log LONGTEXT,
  last_heartbeat DATETIME,
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status),
  INDEX idx_resource_type (resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Jenkins Node (Agent) 表';

-- ============================================================
-- CLM - Jenkins 版本表
-- ============================================================
CREATE TABLE IF NOT EXISTS xnet_aiops_clm_jenkins_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version VARCHAR(32) NOT NULL UNIQUE,
  version_type VARCHAR(16) DEFAULT 'stable' COMMENT 'stable / weekly',
  release_date DATE,
  download_url TEXT,
  sha256 VARCHAR(128),
  is_lts TINYINT(1) DEFAULT 0,
  is_latest TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_version_type (version_type),
  INDEX idx_is_lts (is_lts),
  INDEX idx_is_latest (is_latest)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Jenkins 版本表';

-- ============================================================
-- 初始数据
-- ============================================================

INSERT INTO xnet_aiops_usr_user (uid, username, password, user_type, status)
VALUES (UUID(), 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6CQARaY1.k0YGKISbVFnTUjXS', 'admin', 'active');
-- 默认密码: password

INSERT INTO xnet_aiops_usr_role (role_name, role_code, description) VALUES
('管理员', 'ADMIN', '系统管理员，拥有所有权限'),
('运维人员', 'OPERATOR', '运维操作人员，可管理集群和服务'),
('观察者', 'VIEWER', '只读权限，查看集群状态');

-- ============================================================
-- K8S - Kubernetes管理模块
-- ============================================================

CREATE TABLE xnet_aiops_k8s_cluster (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  description TEXT,
  api_server_url VARCHAR(500),
  kubeconfig_content TEXT COMMENT 'AES加密的kubeconfig内容',
  version VARCHAR(50) COMMENT 'Kubernetes版本',
  node_count INT DEFAULT 0,
  namespace_count INT DEFAULT 0,
  status VARCHAR(20) DEFAULT 'inactive' COMMENT 'active/inactive/error/connecting',
  provider VARCHAR(50) COMMENT '集群提供商: self-managed/aliyun/aws/gcp',
  network_plugin VARCHAR(50) COMMENT '网络插件: calico/flannel/cilium',
  container_runtime VARCHAR(50) COMMENT '容器运行时: containerd/cri-o',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s集群信息表';

CREATE TABLE xnet_aiops_k8s_cluster_component (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  component_name VARCHAR(100) NOT NULL COMMENT '组件名称: kube-apiserver/etcd/kube-scheduler等',
  component_type VARCHAR(50) NOT NULL COMMENT '组件类型: kubernetes/kubesphere/monitoring/logging',
  status VARCHAR(20) DEFAULT 'unknown' COMMENT 'healthy/unhealthy/unknown',
  message TEXT,
  checked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s集群组件健康状态表';

CREATE TABLE xnet_aiops_k8s_cluster_metrics_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  cpu_capacity DOUBLE DEFAULT 0 COMMENT 'CPU总核数',
  cpu_used DOUBLE DEFAULT 0 COMMENT 'CPU已使用核数',
  memory_capacity BIGINT DEFAULT 0 COMMENT '内存总量(bytes)',
  memory_used BIGINT DEFAULT 0 COMMENT '内存已使用量(bytes)',
  pod_capacity INT DEFAULT 0 COMMENT 'Pod总容量',
  pod_used INT DEFAULT 0 COMMENT 'Pod已使用数',
  storage_capacity BIGINT DEFAULT 0 COMMENT '存储总量(bytes)',
  storage_used BIGINT DEFAULT 0 COMMENT '存储已使用量(bytes)',
  snapshot_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s集群资源指标快照表';

CREATE TABLE xnet_aiops_k8s_event_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  namespace VARCHAR(255),
  kind VARCHAR(100) COMMENT '资源类型: Pod/Node/Deployment等',
  name VARCHAR(255) COMMENT '资源名称',
  event_type VARCHAR(20) COMMENT 'Normal/Warning',
  reason VARCHAR(255),
  message TEXT,
  source_component VARCHAR(255),
  first_timestamp DATETIME,
  last_timestamp DATETIME,
  count INT DEFAULT 1,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE,
  INDEX idx_cluster_ns (cluster_id, namespace),
  INDEX idx_cluster_kind (cluster_id, kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s事件日志表';

CREATE TABLE xnet_aiops_k8s_prometheus_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL UNIQUE,
  prometheus_url VARCHAR(500) NOT NULL COMMENT 'Prometheus API地址',
  auth_type VARCHAR(20) DEFAULT 'none' COMMENT 'none/basic/bearer',
  auth_token TEXT COMMENT '认证Token',
  username VARCHAR(100),
  password VARCHAR(255),
  status VARCHAR(20) DEFAULT 'inactive' COMMENT 'active/inactive/error',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s Prometheus配置表';

CREATE TABLE xnet_aiops_k8s_helm_repo (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE,
  url VARCHAR(500) NOT NULL,
  description TEXT,
  auth_type VARCHAR(20) DEFAULT 'none' COMMENT 'none/basic',
  username VARCHAR(100),
  password VARCHAR(255),
  status VARCHAR(20) DEFAULT 'active' COMMENT 'active/syncing/error',
  last_synced_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Helm仓库表';

CREATE TABLE xnet_aiops_k8s_helm_release (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  namespace VARCHAR(255) NOT NULL,
  release_name VARCHAR(255) NOT NULL,
  chart_name VARCHAR(255) NOT NULL,
  chart_version VARCHAR(100),
  app_version VARCHAR(100),
  values_override TEXT COMMENT '用户自定义values(JSON)',
  status VARCHAR(50) COMMENT 'deployed/failed/pending-install/uninstalling',
  revision INT DEFAULT 1,
  notes TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE,
  UNIQUE KEY uk_cluster_ns_release (cluster_id, namespace, release_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Helm Release记录表';

CREATE TABLE xnet_aiops_k8s_deploy_plan (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  plan_name VARCHAR(100) NOT NULL,
  k8s_version VARCHAR(50) NOT NULL COMMENT 'Kubernetes版本',
  deploy_type VARCHAR(20) DEFAULT 'single' COMMENT 'single/ha - 单Master/高可用',
  network_plugin VARCHAR(50) DEFAULT 'calico' COMMENT '网络插件: calico/flannel/cilium',
  container_runtime VARCHAR(50) DEFAULT 'containerd' COMMENT '容器运行时: containerd/cri-o',
  pod_cidr VARCHAR(50) DEFAULT '10.244.0.0/16',
  service_cidr VARCHAR(50) DEFAULT '10.96.0.0/12',
  install_metrics_server BOOLEAN DEFAULT TRUE,
  install_ingress_nginx BOOLEAN DEFAULT TRUE,
  storage_plugin VARCHAR(50) DEFAULT 'local-path' COMMENT '存储插件: none/local-path/longhorn/nfs',
  registry_url VARCHAR(255) DEFAULT NULL COMMENT '私有镜像仓库地址(如 10.0.0.1:80)，为空则使用公共镜像源',
  status VARCHAR(20) DEFAULT 'draft' COMMENT 'draft/validating/deploying/success/failed',
  result_cluster_id BIGINT COMMENT '部署成功后关联的K8s集群ID',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (result_cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s集群部署计划表';

CREATE TABLE xnet_aiops_k8s_deploy_node (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  host VARCHAR(255) NOT NULL COMMENT '节点IP或主机名',
  ssh_port INT DEFAULT 22,
  ssh_user VARCHAR(100) DEFAULT 'root',
  ssh_password VARCHAR(500) COMMENT 'AES加密',
  ssh_key TEXT COMMENT '私钥内容(AES加密)',
  role VARCHAR(20) NOT NULL COMMENT 'master/worker',
  hostname VARCHAR(100) COMMENT '节点主机名(可选)',
  status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/preparing/installing/ready/failed',
  status_message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (plan_id) REFERENCES xnet_aiops_k8s_deploy_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s部署节点表';

CREATE TABLE xnet_aiops_k8s_deploy_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  node_host VARCHAR(255),
  step VARCHAR(100) COMMENT '部署步骤名称',
  log_level VARCHAR(20) DEFAULT 'INFO' COMMENT 'INFO/WARN/ERROR',
  message TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (plan_id) REFERENCES xnet_aiops_k8s_deploy_plan(id) ON DELETE CASCADE,
  INDEX idx_plan_id (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s部署日志表';

-- ==================== Phase 5: 监控告警 ====================

CREATE TABLE xnet_aiops_k8s_alert_rule (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  name VARCHAR(200) NOT NULL COMMENT '规则名称',
  description VARCHAR(500) COMMENT '规则描述',
  severity VARCHAR(20) NOT NULL DEFAULT 'warning' COMMENT 'critical/warning/info',
  resource_type VARCHAR(50) NOT NULL COMMENT 'node/pod/deployment/cluster',
  metric_name VARCHAR(200) NOT NULL COMMENT '指标名称',
  `condition` VARCHAR(10) NOT NULL DEFAULT '>' COMMENT '> < >= <= ==',
  threshold DOUBLE NOT NULL DEFAULT 80 COMMENT '阈值',
  duration VARCHAR(20) DEFAULT '5m' COMMENT '持续时间',
  enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  notify_channels VARCHAR(500) DEFAULT '[]' COMMENT 'JSON数组: ["email","webhook"]',
  last_triggered DATETIME COMMENT '最后触发时间',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE,
  INDEX idx_cluster_id (cluster_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s告警规则表';

CREATE TABLE xnet_aiops_k8s_alert_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_id BIGINT COMMENT '关联规则ID',
  cluster_id BIGINT NOT NULL,
  rule_name VARCHAR(200) COMMENT '规则名称(冗余)',
  severity VARCHAR(20) COMMENT 'critical/warning/info',
  resource_type VARCHAR(50) COMMENT '资源类型',
  resource_name VARCHAR(200) COMMENT '资源名称',
  message TEXT COMMENT '告警消息',
  status VARCHAR(20) DEFAULT 'firing' COMMENT 'firing/resolved',
  current_value DOUBLE COMMENT '当前值',
  threshold DOUBLE COMMENT '阈值',
  fired_at DATETIME COMMENT '触发时间',
  resolved_at DATETIME COMMENT '恢复时间',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE,
  INDEX idx_cluster_id (cluster_id),
  INDEX idx_rule_id (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s告警历史表';

-- ============================================================
-- REG - 仓库管理模块
-- ============================================================

-- 仓库实例表
CREATE TABLE xnet_aiops_reg_registry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  uid VARCHAR(36) NOT NULL UNIQUE,
  registry_name VARCHAR(100) NOT NULL,
  registry_type ENUM('harbor','gitlab','docker_distribution') NOT NULL COMMENT '仓库类型',
  description TEXT,
  version VARCHAR(50),
  status VARCHAR(20) DEFAULT 'not_deployed' COMMENT 'not_deployed/deploying/running/stopped/failed/uninstalling',
  deploy_mode ENUM('ssh','k8s') NOT NULL COMMENT '部署方式',
  -- SSH 部署字段
  host_id BIGINT COMMENT 'HOM主机ID',
  host VARCHAR(200) COMMENT '主机地址',
  ssh_port INT DEFAULT 22,
  ssh_user VARCHAR(50),
  encrypted_password VARCHAR(500),
  encrypted_private_key TEXT,
  install_path VARCHAR(200),
  service_port INT COMMENT '仓库服务端口(Harbor:80, GitLab:80, Registry:5000)',
  -- K8s 部署字段
  cluster_id BIGINT COMMENT 'K8s集群ID',
  namespace VARCHAR(100),
  release_name VARCHAR(100),
  helm_values TEXT COMMENT 'Helm values YAML',
  -- 仓库访问信息
  endpoint VARCHAR(500) COMMENT '访问地址',
  api_url VARCHAR(500) COMMENT 'API地址',
  admin_user VARCHAR(100),
  encrypted_admin_password VARCHAR(500),
  use_ssl BOOLEAN DEFAULT FALSE,
  cert_pem TEXT,
  created_by VARCHAR(50),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_registry_type (registry_type),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库实例表';

-- 部署日志表
CREATE TABLE xnet_aiops_reg_deploy_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  registry_id BIGINT NOT NULL,
  action ENUM('install','upgrade','uninstall','start','stop','restart') NOT NULL COMMENT '操作类型',
  status VARCHAR(20) DEFAULT 'running' COMMENT 'running/success/failed',
  log_text LONGTEXT,
  started_at DATETIME,
  finished_at DATETIME,
  FOREIGN KEY (registry_id) REFERENCES xnet_aiops_reg_registry(id) ON DELETE CASCADE,
  INDEX idx_registry_id (registry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部署日志表';

-- 仓库项目表（Harbor projects / GitLab groups）
CREATE TABLE xnet_aiops_reg_project (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  registry_id BIGINT NOT NULL,
  project_name VARCHAR(200) NOT NULL,
  visibility VARCHAR(20) DEFAULT 'public' COMMENT 'public/private',
  repo_count INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (registry_id) REFERENCES xnet_aiops_reg_registry(id) ON DELETE CASCADE,
  INDEX idx_registry_id (registry_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库项目表';

-- 仓库镜像/仓库表
CREATE TABLE xnet_aiops_reg_repository (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  registry_id BIGINT NOT NULL,
  project_id BIGINT,
  repo_name VARCHAR(300) NOT NULL,
  tags_count INT DEFAULT 0,
  pull_count BIGINT DEFAULT 0,
  latest_tag VARCHAR(200),
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (registry_id) REFERENCES xnet_aiops_reg_registry(id) ON DELETE CASCADE,
  INDEX idx_registry_id (registry_id),
  INDEX idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镜像仓库表';

-- 镜像标签表
CREATE TABLE xnet_aiops_reg_tag (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  repository_id BIGINT NOT NULL,
  tag_name VARCHAR(200) NOT NULL,
  digest VARCHAR(200),
  size_bytes BIGINT DEFAULT 0,
  architecture VARCHAR(50),
  os VARCHAR(50),
  pushed_at DATETIME,
  FOREIGN KEY (repository_id) REFERENCES xnet_aiops_reg_repository(id) ON DELETE CASCADE,
  INDEX idx_repository_id (repository_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镜像标签表';
