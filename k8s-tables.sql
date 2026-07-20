-- ============================================================
-- K8S - Kubernetes管理模块 (独立建表脚本)
-- 数据库: XnetAIops
-- ============================================================

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_cluster (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_cluster_component (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id BIGINT NOT NULL,
  component_name VARCHAR(100) NOT NULL COMMENT '组件名称: kube-apiserver/etcd/kube-scheduler等',
  component_type VARCHAR(50) NOT NULL COMMENT '组件类型: kubernetes/kubesphere/monitoring/logging',
  status VARCHAR(20) DEFAULT 'unknown' COMMENT 'healthy/unhealthy/unknown',
  message TEXT,
  checked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cluster_id) REFERENCES xnet_aiops_k8s_cluster(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s集群组件健康状态表';

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_cluster_metrics_snapshot (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_event_log (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_prometheus_config (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_helm_repo (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_helm_release (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_deploy_plan (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_deploy_node (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_deploy_log (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_alert_rule (
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

CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_alert_history (
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
-- K8sCluster 新增 SSH 字段 (Helm CLI 需要 SSH 到 master 节点)
-- ============================================================
ALTER TABLE xnet_aiops_k8s_cluster
  ADD COLUMN ssh_host VARCHAR(200) COMMENT 'SSH连接地址' AFTER container_runtime,
  ADD COLUMN ssh_port INT DEFAULT 22 COMMENT 'SSH端口' AFTER ssh_host,
  ADD COLUMN ssh_user VARCHAR(100) COMMENT 'SSH用户名' AFTER ssh_port,
  ADD COLUMN ssh_password VARCHAR(500) COMMENT 'SSH密码(加密存储)' AFTER ssh_user,
  ADD COLUMN ssh_key TEXT COMMENT 'SSH私钥' AFTER ssh_password;

-- ============================================================
-- 应用模板表 (App Store 预置应用)
-- ============================================================
CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_app_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  display_name VARCHAR(200) NOT NULL,
  description TEXT,
  category VARCHAR(50) NOT NULL COMMENT 'middleware/devops/monitoring/storage/database/messaging',
  icon VARCHAR(500),
  helm_repo_name VARCHAR(100) NOT NULL,
  helm_repo_url VARCHAR(500) NOT NULL,
  chart_name VARCHAR(100) NOT NULL,
  chart_version VARCHAR(50) COMMENT '推荐版本，null=latest',
  default_values TEXT COMMENT '默认 values.yaml',
  doc_url VARCHAR(500),
  is_featured TINYINT DEFAULT 0 COMMENT '推荐应用',
  sort_order INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='K8s应用模板表(App Store)';

-- ============================================================
-- 预置 13 个应用模板 (种子数据)
-- 使用国内可访问的 Helm 仓库:
--   stable = 阿里云镜像 (https://kubernetes.oss-cn-hangzhou.aliyuncs.com/charts)
--   其他仓库保持官方源（需集群节点可访问）
-- ============================================================
INSERT INTO xnet_aiops_k8s_app_template (name, display_name, description, category, icon, helm_repo_name, helm_repo_url, chart_name, chart_version, default_values, doc_url, is_featured, sort_order) VALUES
('mysql', 'MySQL', '开源关系型数据库，广泛用于Web应用和企业级系统', 'database', 'https://bitnami.com/assets/stacks/mysql/img/mysql-stack-220x234.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'mysql', NULL, 'mysqlRootPassword: "changeme"\nmysqlDatabase: mydb\nmysqlUser: admin\nmysqlPassword: "changeme"\npersistence:\n  enabled: true\n  size: 10Gi', 'https://github.com/helm/charts/tree/master/stable/mysql', 1, 1),

('redis', 'Redis', '高性能Key-Value内存数据库，支持持久化、集群和哨兵模式', 'database', 'https://bitnami.com/assets/stacks/redis/img/redis-stack-220x234.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'redis', NULL, 'password: "changeme"\nmaster:\n  persistence:\n    enabled: true\n    size: 5Gi\nslave:\n  replicas: 1', 'https://github.com/helm/charts/tree/master/stable/redis', 1, 2),

('elasticsearch', 'Elasticsearch', '分布式搜索和分析引擎，用于日志分析、全文检索和数据可视化', 'database', 'https://bitnami.com/assets/stacks/elasticsearch/img/elasticsearch-stack-220x234.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'elasticsearch', NULL, 'replicas: 1\nminimumMasterNodes: 1\nvolumeClaimTemplate:\n  resources:\n    requests:\n      storage: 10Gi', 'https://github.com/helm/charts/tree/master/stable/elasticsearch', 0, 3),

('minio', 'MinIO', '高性能对象存储服务，兼容Amazon S3 API', 'storage', 'https://min.io/resources/img/logo.svg', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'minio', NULL, 'accessKey: admin\nsecretKey: "changeme"\npersistence:\n  enabled: true\n  size: 20Gi\nmode: standalone', 'https://github.com/helm/charts/tree/master/stable/minio', 1, 4),

('nginx-ingress', 'Nginx Ingress', 'Kubernetes Ingress 控制器，基于 Nginx 的负载均衡和反向代理', 'middleware', 'https://bitnami.com/assets/stacks/nginx/img/nginx-stack-220x234.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'nginx-ingress', NULL, 'controller:\n  replicaCount: 1\n  service:\n    type: ClusterIP', 'https://github.com/helm/charts/tree/master/stable/nginx-ingress', 0, 5),

('rabbitmq', 'RabbitMQ', '开源消息代理中间件，支持AMQP、MQTT等多种协议', 'middleware', 'https://bitnami.com/assets/stacks/rabbitmq/img/rabbitmq-stack-220x234.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'rabbitmq', NULL, 'rabbitmq:\n  username: admin\n  password: "changeme"\npersistence:\n  enabled: true\n  size: 5Gi', 'https://github.com/helm/charts/tree/master/stable/rabbitmq', 0, 6),

('kafka', 'Kafka', '分布式流处理平台，高吞吐量的消息队列系统', 'messaging', 'https://bitnami.com/assets/stacks/kafka/img/kafka-stack-220x234.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'kafka-manager', NULL, 'zkHosts: "zookeeper:2181"', 'https://github.com/helm/charts/tree/master/stable/kafka-manager', 1, 7),

('harbor', 'Harbor', '企业级容器镜像仓库，支持镜像扫描、签名和复制', 'devops', 'https://goharbor.io/img/logos/harbor-icon-color.png', 'harbor', 'https://helm.goharbor.io', 'harbor', NULL, 'expose:\n  type: clusterIP\nexternalURL: https://harbor.example.com\npersistence:\n  persistentVolumeClaim:\n    registry:\n      size: 50Gi', 'https://goharbor.io/docs/', 1, 8),

('jenkins', 'Jenkins', '开源持续集成和持续交付（CI/CD）自动化服务器', 'devops', 'https://www.jenkins.io/images/logos/jenkins/jenkins.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'jenkins', NULL, 'master:\n  adminPassword: "changeme"\n  serviceType: ClusterIP\npersistence:\n  enabled: true\n  size: 10Gi', 'https://github.com/helm/charts/tree/master/stable/jenkins', 0, 9),

('gitlab', 'GitLab', '一站式DevOps平台，集成代码管理、CI/CD、项目管理', 'devops', 'https://about.gitlab.com/images/press/press-kit-icon.svg', 'gitlab', 'https://charts.gitlab.io', 'gitlab', NULL, 'global:\n  hosts:\n    domain: example.com\n  edition: ce\ncertmanager:\n  install: false\nnginx-ingress:\n  enabled: false', 'https://docs.gitlab.com/', 0, 10),

('sonarqube', 'SonarQube', '代码质量和安全分析平台，支持多种编程语言', 'devops', 'https://assets-eu-01.kc-usercontent.com/0e76e8c6-29e0-01b6-ca9b-fc4482f9858d/e3a60dc3-bd08-407b-86d5-9e0e4e20dfe2/sonarqube-logo.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'sonarqube', NULL, 'persistence:\n  enabled: true\n  size: 10Gi\npostgresql:\n  enabled: true', 'https://github.com/helm/charts/tree/master/stable/sonarqube', 0, 11),

('prometheus', 'Prometheus', '云原生监控和告警系统，广泛用于Kubernetes集群监控', 'monitoring', 'https://prometheus.io/assets/prometheus_logo_grey.svg', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'prometheus', NULL, 'server:\n  persistentVolume:\n    size: 20Gi\nalertmanager:\n  enabled: true', 'https://github.com/helm/charts/tree/master/stable/prometheus', 1, 12),

('grafana', 'Grafana', '开源数据可视化和监控平台，支持丰富的数据源和仪表板', 'monitoring', 'https://grafana.com/static/assets/img/fav32.png', 'bitnami', 'https://helm-charts.itboon.top/bitnami', 'grafana', NULL, 'adminUser: admin\nadminPassword: "changeme"\npersistence:\n  enabled: true\n  size: 5Gi', 'https://github.com/helm/charts/tree/master/stable/grafana', 1, 13);

-- ============================================================
-- 迁移脚本：将所有废弃仓库 URL 迁移到国内可用镜像
-- ============================================================
UPDATE xnet_aiops_k8s_app_template
SET helm_repo_name = 'bitnami',
    helm_repo_url = 'https://helm-charts.itboon.top/bitnami'
WHERE helm_repo_name IN ('stable', 'bitnami')
  AND (helm_repo_url LIKE '%kubernetes.oss-cn-hangzhou%' OR helm_repo_url LIKE '%charts.bitnami.com%'
       OR helm_repo_url LIKE '%registry-1.docker.io%');

UPDATE xnet_aiops_k8s_app_template
SET helm_repo_name = 'bitnami',
    helm_repo_url = 'https://helm-charts.itboon.top/bitnami'
WHERE helm_repo_name IN ('prometheus-community', 'sonarqube')
  AND name IN ('prometheus', 'sonarqube')
  AND helm_repo_url LIKE '%kubernetes.oss-cn-hangzhou%';

UPDATE xnet_aiops_k8s_app_template
SET helm_repo_name = 'bitnami',
    helm_repo_url = 'https://helm-charts.itboon.top/bitnami'
WHERE name = 'jenkins' AND helm_repo_url LIKE '%kubernetes.oss-cn-hangzhou%';

-- 同步更新 helm_repo 表
UPDATE xnet_aiops_k8s_helm_repo
SET url = 'https://helm-charts.itboon.top/bitnami'
WHERE url LIKE '%kubernetes.oss-cn-hangzhou%' OR url LIKE '%charts.bitnami.com%'
   OR url LIKE '%registry-1.docker.io%';

INSERT IGNORE INTO xnet_aiops_k8s_helm_repo (name, url, description, auth_type, status)
VALUES ('bitnami', 'https://helm-charts.itboon.top/bitnami', 'Bitnami Helm chart 国内镜像', 'none', 'active');

-- 更新 Helm 全局配置
UPDATE xnet_aiops_k8s_helm_config
SET config_value = 'https://helm-charts.itboon.top/bitnami',
    description = '默认 Helm chart 仓库地址（国内镜像）'
WHERE config_key = 'defaultRepoUrl';

INSERT IGNORE INTO xnet_aiops_k8s_helm_config (config_key, config_value, description) VALUES
('ociRegistry', '', 'OCI chart 仓库地址（海外集群可设 oci://registry-1.docker.io/bitnamicharts），为空则使用传统 HTTP 仓库');

-- ============================================================
-- Helm 全局配置表
-- ============================================================
CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_helm_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key VARCHAR(100) NOT NULL UNIQUE,
  config_value TEXT,
  description VARCHAR(500),
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Helm全局配置表';

INSERT IGNORE INTO xnet_aiops_k8s_helm_config (config_key, config_value, description) VALUES
('imageRegistry', '', '全局容器镜像仓库地址（如 docker.m.daocloud.io），为空则使用 chart 默认镜像源'),
('defaultRepoName', 'bitnami', '默认 Helm chart 仓库名称'),
('defaultRepoUrl', 'https://helm-charts.itboon.top/bitnami', '默认 Helm chart 仓库地址（国内镜像）'),
('ociRegistry', '', 'OCI chart 仓库镜像地址（国内可设 oci://docker.m.daocloud.io/bitnamicharts），为空则使用官方 oci://registry-1.docker.io/bitnamicharts');
