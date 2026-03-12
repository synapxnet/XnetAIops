-- ============================================================
-- DevOps Pipeline Tables (K8S Module Extension)
-- Database: XnetAIops
-- ============================================================

-- 1. DevOps工程
CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_devops_project (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description TEXT,
  jenkins_url VARCHAR(500) NOT NULL COMMENT 'Jenkins地址, e.g. http://192.168.1.10:8080',
  jenkins_user VARCHAR(100) NOT NULL,
  jenkins_token VARCHAR(500) NOT NULL COMMENT 'Jenkins API Token',
  cluster_id BIGINT COMMENT '关联K8s集群ID(可选)',
  status VARCHAR(20) DEFAULT 'active' COMMENT 'active/error',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DevOps工程';

-- 2. 凭证
CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_credential (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  type VARCHAR(30) NOT NULL COMMENT 'username-password/ssh-key/access-token/kubeconfig',
  description TEXT,
  username VARCHAR(200),
  password VARCHAR(500) COMMENT '加密存储',
  private_key TEXT,
  passphrase VARCHAR(200),
  token VARCHAR(500),
  kubeconfig TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES xnet_aiops_k8s_devops_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='凭证管理';

-- 3. 流水线
CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_pipeline (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  description TEXT,
  type VARCHAR(20) DEFAULT 'pipeline' COMMENT 'pipeline/multi-branch',
  jenkinsfile TEXT COMMENT 'Jenkinsfile内容',
  source_type VARCHAR(20) COMMENT 'git/github/gitlab/svn/none',
  source_url VARCHAR(500) COMMENT '代码仓库地址',
  source_branch VARCHAR(100) DEFAULT 'main',
  credential_id BIGINT COMMENT '凭证ID',
  disable_concurrent BOOLEAN DEFAULT FALSE,
  timer_trigger VARCHAR(100) COMMENT 'Cron表达式',
  jenkins_job_name VARCHAR(200) COMMENT 'Jenkins中的Job名',
  jenkins_job_path VARCHAR(500) COMMENT 'Jenkins Job完整路径',
  status VARCHAR(20) DEFAULT 'active' COMMENT 'active/disabled/error',
  last_run_status VARCHAR(20) COMMENT 'success/failed/running/aborted',
  last_run_time DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (project_id) REFERENCES xnet_aiops_k8s_devops_project(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线';

-- 4. 流水线运行记录
CREATE TABLE IF NOT EXISTS xnet_aiops_k8s_pipeline_run (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pipeline_id BIGINT NOT NULL,
  run_number INT NOT NULL COMMENT 'Jenkins build number',
  status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/queued/running/success/failed/aborted',
  trigger_type VARCHAR(20) COMMENT 'manual/timer/webhook/scm',
  trigger_user VARCHAR(100),
  parameters TEXT COMMENT 'JSON: [{name,value}]',
  start_time DATETIME,
  end_time DATETIME,
  duration_ms BIGINT,
  stages_status TEXT COMMENT 'JSON: [{id,name,status,durationMillis}]',
  log_text LONGTEXT COMMENT '构建日志(缓存)',
  jenkins_build_url VARCHAR(500),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (pipeline_id) REFERENCES xnet_aiops_k8s_pipeline(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线运行记录';

-- 5. 给Jenkins Master表添加host_id字段(关联HOM主机管理)
ALTER TABLE xnet_aiops_clm_jenkins_master
  ADD COLUMN IF NOT EXISTS host_id BIGINT NULL COMMENT '关联HOM主机ID(可选)' AFTER disk_gb;
