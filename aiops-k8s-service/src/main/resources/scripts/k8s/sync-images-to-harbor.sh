#!/bin/bash
#
# 镜像同步脚本 — 将 K8s 部署所需的全部镜像同步到私有 Harbor 仓库
#
# 使用方法:
#   1. 在可访问公网的服务器上运行（推荐海外服务器，拉取速度快）
#   2. 确保已安装 docker 并已登录 Harbor:
#      docker login <HARBOR_IP>:<PORT>
#   3. 运行:
#      bash sync-images-to-harbor.sh <HARBOR_ADDR>
#      例: bash sync-images-to-harbor.sh 10.0.0.1:80
#
# Harbor 项目需提前创建（可在 Harbor UI 中操作，设为 Public）:
#   - google_containers   (K8s 核心组件 + metrics-server)
#   - calico              (Calico CNI)
#   - flannel             (Flannel CNI)
#   - ingress-nginx       (Ingress Nginx)
#   - library             (docker.io 官方镜像)
#

set -e

HARBOR_ADDR="${1}"

if [ -z "$HARBOR_ADDR" ]; then
    echo "用法: $0 <HARBOR地址>"
    echo "例:   $0 10.0.0.1:80"
    exit 1
fi

echo "========================================"
echo "  镜像同步到 Harbor: ${HARBOR_ADDR}"
echo "========================================"

# 同步函数: 拉取 → 重新tag → 推送 → 清理
sync_image() {
    local src="$1"      # 源镜像 (如 registry.k8s.io/kube-apiserver:v1.31.0)
    local dst="$2"      # 目标镜像 (如 10.0.0.1:80/google_containers/kube-apiserver:v1.31.0)

    echo "---"
    echo "同步: ${src}"
    echo "  -> ${dst}"

    if docker pull "$src" 2>&1; then
        docker tag "$src" "$dst"
        if docker push "$dst" 2>&1; then
            echo "  OK"
        else
            echo "  WARN: push 失败"
            FAILED+=("$src -> $dst")
        fi
        docker rmi "$dst" 2>/dev/null || true
    else
        echo "  WARN: pull 失败"
        FAILED+=("$src")
    fi
}

FAILED=()

# ============================================================
# 1. K8s 核心组件 (registry.k8s.io → google_containers)
# ============================================================
echo ""
echo "===== [1/6] K8s 核心组件 ====="

K8S_VERSIONS=("1.31.0" "1.30.0" "1.30.6" "1.29.0" "1.29.10" "1.28.0" "1.28.15" "1.27.16")
K8S_COMPONENTS=("kube-apiserver" "kube-controller-manager" "kube-scheduler" "kube-proxy")

for ver in "${K8S_VERSIONS[@]}"; do
    for comp in "${K8S_COMPONENTS[@]}"; do
        sync_image "registry.k8s.io/${comp}:v${ver}" "${HARBOR_ADDR}/google_containers/${comp}:v${ver}"
    done
done

# pause 镜像 (sandbox image)
for pause_ver in "3.10" "3.9" "3.8"; do
    sync_image "registry.k8s.io/pause:${pause_ver}" "${HARBOR_ADDR}/google_containers/pause:${pause_ver}"
done

# CoreDNS
for dns_ver in "v1.11.3" "v1.11.1" "v1.10.1" "v1.9.3"; do
    sync_image "registry.k8s.io/coredns/coredns:${dns_ver}" "${HARBOR_ADDR}/google_containers/coredns:${dns_ver}"
done

# etcd
for etcd_ver in "3.5.15-0" "3.5.12-0" "3.5.10-0" "3.5.9-0"; do
    sync_image "registry.k8s.io/etcd:${etcd_ver}" "${HARBOR_ADDR}/google_containers/etcd:${etcd_ver}"
done

# ============================================================
# 2. metrics-server (registry.k8s.io → google_containers)
# ============================================================
echo ""
echo "===== [2/6] metrics-server ====="

for ms_ver in "v0.7.2" "v0.7.1" "v0.7.0" "v0.6.4"; do
    sync_image "registry.k8s.io/metrics-server/metrics-server:${ms_ver}" "${HARBOR_ADDR}/google_containers/metrics-server:${ms_ver}"
done

# ============================================================
# 3. Calico CNI (docker.io/calico → calico)
# ============================================================
echo ""
echo "===== [3/6] Calico CNI ====="

CALICO_VER="v3.27.0"
CALICO_COMPONENTS=("cni" "node" "kube-controllers")
for comp in "${CALICO_COMPONENTS[@]}"; do
    sync_image "docker.io/calico/${comp}:${CALICO_VER}" "${HARBOR_ADDR}/calico/${comp}:${CALICO_VER}"
done

# ============================================================
# 4. Flannel CNI (ghcr.io/flannel-io → flannel)
# ============================================================
echo ""
echo "===== [4/6] Flannel CNI ====="

FLANNEL_VERS=("v0.26.1" "v0.25.7" "v0.24.4")
for fver in "${FLANNEL_VERS[@]}"; do
    sync_image "ghcr.io/flannel-io/flannel:${fver}" "${HARBOR_ADDR}/flannel/flannel:${fver}"
    sync_image "ghcr.io/flannel-io/flannel-cni-plugin:v1.6.0-flannel1" "${HARBOR_ADDR}/flannel/flannel-cni-plugin:v1.6.0-flannel1"
done

# ============================================================
# 5. Ingress Nginx (registry.k8s.io → ingress-nginx)
# ============================================================
echo ""
echo "===== [5/6] Ingress Nginx ====="

INGRESS_IMAGES=(
    "registry.k8s.io/ingress-nginx/controller:v1.10.0"
    "registry.k8s.io/ingress-nginx/kube-webhook-certgen:v1.4.1"
)
for img in "${INGRESS_IMAGES[@]}"; do
    # 提取 image name:tag 部分
    name_tag="${img#registry.k8s.io/ingress-nginx/}"
    sync_image "$img" "${HARBOR_ADDR}/ingress-nginx/${name_tag}"
done

# ============================================================
# 6. 其他常用镜像 (docker.io/library → library)
# ============================================================
echo ""
echo "===== [6/6] 其他常用镜像 ====="

# local-path-provisioner
sync_image "docker.io/rancher/local-path-provisioner:v0.0.30" "${HARBOR_ADDR}/library/local-path-provisioner:v0.0.30"
# busybox (local-path-provisioner helper)
sync_image "docker.io/library/busybox:latest" "${HARBOR_ADDR}/library/busybox:latest"

# ============================================================
# 汇总
# ============================================================
echo ""
echo "========================================"
echo "  同步完成!"
echo "========================================"

if [ ${#FAILED[@]} -gt 0 ]; then
    echo ""
    echo "以下镜像同步失败 (${#FAILED[@]} 个):"
    for f in "${FAILED[@]}"; do
        echo "  - $f"
    done
    echo ""
    echo "请检查网络和 Harbor 项目配置后重新运行脚本"
    exit 1
else
    echo "全部镜像同步成功!"
    echo ""
    echo "下一步:"
    echo "  1. 在 XnetAIops 创建部署计划时，填入私有镜像仓库: ${HARBOR_ADDR}"
    echo "  2. 执行部署，所有镜像将自动从 Harbor 拉取"
fi
