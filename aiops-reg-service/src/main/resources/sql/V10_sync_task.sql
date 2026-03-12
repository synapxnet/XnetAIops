-- Image sync task tracking table
CREATE TABLE IF NOT EXISTS xnet_aiops_reg_sync_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  registry_id BIGINT NOT NULL COMMENT '目标 Harbor 仓库ID',
  source_image VARCHAR(500) NOT NULL COMMENT '源镜像地址, e.g. docker.io/nginx:1.25',
  target_project VARCHAR(200) COMMENT '目标 Harbor 项目名',
  sync_method VARCHAR(20) NOT NULL DEFAULT 'harbor_replication' COMMENT 'harbor_replication / skopeo',
  harbor_policy_id BIGINT COMMENT 'Harbor replication policy ID',
  harbor_execution_id BIGINT COMMENT 'Harbor replication execution ID',
  status VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed/cancelled',
  status_detail TEXT COMMENT '状态详情/错误信息',
  created_by VARCHAR(100),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_registry_id (registry_id),
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='镜像同步任务表';
