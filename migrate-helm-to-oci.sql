-- ============================================================
-- Helm 仓库迁移脚本
-- 将所有废弃的 Helm 仓库 URL 迁移到国内可用的镜像站
-- Bitnami 传统仓库(charts.bitnami.com)和阿里云镜像均已废弃
-- ============================================================

-- 1. 更新应用模板中的仓库 URL
UPDATE xnet_aiops_k8s_app_template
SET helm_repo_name = 'bitnami',
    helm_repo_url = 'https://helm-charts.itboon.top/bitnami'
WHERE helm_repo_url LIKE '%kubernetes.oss-cn-hangzhou%'
   OR helm_repo_url LIKE '%charts.bitnami.com%';

-- 2. 更新 Helm 仓库表中的 URL
UPDATE xnet_aiops_k8s_helm_repo
SET url = 'https://helm-charts.itboon.top/bitnami'
WHERE url LIKE '%kubernetes.oss-cn-hangzhou%'
   OR url LIKE '%charts.bitnami.com%'
   OR url LIKE '%registry-1.docker.io%';

-- 3. 确保有一个 bitnami 镜像仓库记录
INSERT IGNORE INTO xnet_aiops_k8s_helm_repo (name, url, description, auth_type, status)
VALUES ('bitnami', 'https://helm-charts.itboon.top/bitnami', 'Bitnami Helm chart 国内镜像', 'none', 'active');

-- 4. 更新全局配置
UPDATE xnet_aiops_k8s_helm_config
SET config_value = 'https://helm-charts.itboon.top/bitnami',
    description = '默认 Helm chart 仓库地址（国内镜像）'
WHERE config_key = 'defaultRepoUrl';

-- 5. 添加 OCI 仓库配置项（可选，海外集群可用）
INSERT IGNORE INTO xnet_aiops_k8s_helm_config (config_key, config_value, description) VALUES
('ociRegistry', '', 'OCI chart 仓库地址（海外集群可设 oci://registry-1.docker.io/bitnamicharts），为空则使用传统 HTTP 仓库');
