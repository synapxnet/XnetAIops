#!/bin/bash
set -e

K8S_VERSION="{{K8S_VERSION}}"
POD_CIDR="{{POD_CIDR}}"
SERVICE_CIDR="{{SERVICE_CIDR}}"
MASTER_IP="{{MASTER_IP}}"
REGISTRY_URL="{{REGISTRY_URL}}"

echo "===== 初始化Master节点开始 ====="
echo "K8s版本: v${K8S_VERSION}"
echo "Pod CIDR: ${POD_CIDR}"
echo "Service CIDR: ${SERVICE_CIDR}"
echo "配置的Master IP: ${MASTER_IP}"

# Detect if MASTER_IP is a NAT public IP (common on cloud VMs like Tencent/Aliyun)
# If it's not bound to any local interface, use the actual local IP for kubeadm init
ORIGINAL_MASTER_IP="${MASTER_IP}"
if ! ip addr show 2>/dev/null | grep -q "inet ${MASTER_IP}/"; then
    echo "WARNING: ${MASTER_IP} 不是本机网卡地址（云服务器NAT公网IP）"
    LOCAL_IP=$(ip route get 1 2>/dev/null | awk '{for(i=1;i<=NF;i++)if($i=="src")print $(i+1)}' | head -1)
    if [ -n "$LOCAL_IP" ]; then
        echo "自动检测到内网IP: $LOCAL_IP"
        echo "kubeadm将使用内网IP初始化，kubeconfig将替换回公网IP"
        MASTER_IP="$LOCAL_IP"
    else
        echo "WARNING: 无法检测内网IP，继续使用 ${MASTER_IP}"
    fi
fi
echo "实际使用的API Server地址: ${MASTER_IP}"

# Clean up any previous failed kubeadm init
echo "清理之前的kubeadm状态..."
kubeadm reset -f 2>/dev/null || true
rm -rf /etc/cni/net.d 2>/dev/null || true
rm -rf /root/.kube 2>/dev/null || true

# Verify containerd sandbox_image is correctly configured
if [ -n "$REGISTRY_URL" ]; then
    PAUSE_IMAGE="${REGISTRY_URL}/google_containers/pause:3.10"
else
    PAUSE_IMAGE="registry.aliyuncs.com/google_containers/pause:3.10"
fi
echo "检查containerd sandbox_image配置..."
if grep -q 'sandbox_image' /etc/containerd/config.toml; then
    grep sandbox_image /etc/containerd/config.toml
    # Fix if wrong value
    if ! grep -q "sandbox_image = \"${PAUSE_IMAGE}\"" /etc/containerd/config.toml; then
        echo "修复sandbox_image..."
        sed -i "s|sandbox_image = .*|sandbox_image = \"${PAUSE_IMAGE}\"|" /etc/containerd/config.toml
    fi
else
    echo "WARNING: sandbox_image未找到"
fi

# Ensure containerd is running with correct config
echo "重启containerd..."
systemctl restart containerd
sleep 3
systemctl is-active containerd

# Determine image repository
if [ -n "$REGISTRY_URL" ]; then
    IMAGE_REPO="${REGISTRY_URL}/google_containers"
    echo "使用私有镜像仓库: ${IMAGE_REPO}"
else
    IMAGE_REPO="registry.aliyuncs.com/google_containers"
    echo "使用阿里云镜像源: ${IMAGE_REPO}"
fi

# Pre-pull images
echo "预拉取K8s镜像..."
kubeadm config images pull \
    --kubernetes-version="v${K8S_VERSION}" \
    --image-repository="${IMAGE_REPO}" 2>&1 || {
    echo "WARNING: 镜像预拉取失败，kubeadm init将自行拉取"
}

echo "镜像准备完成"

# Run kubeadm init
echo "执行 kubeadm init..."
EXTRA_SANS_FLAG=""
if [ "$ORIGINAL_MASTER_IP" != "$MASTER_IP" ]; then
    EXTRA_SANS_FLAG="--apiserver-cert-extra-sans=${ORIGINAL_MASTER_IP}"
    echo "添加公网IP到证书SAN: ${ORIGINAL_MASTER_IP}"
fi

kubeadm init \
    --kubernetes-version="v${K8S_VERSION}" \
    --pod-network-cidr="${POD_CIDR}" \
    --service-cidr="${SERVICE_CIDR}" \
    --apiserver-advertise-address="${MASTER_IP}" \
    --image-repository="${IMAGE_REPO}" \
    --upload-certs \
    ${EXTRA_SANS_FLAG} 2>&1

# Setup kubectl for root user (using internal IP — needed for local commands below)
echo "配置kubectl..."
mkdir -p /root/.kube
cp -f /etc/kubernetes/admin.conf /root/.kube/config
chown root:root /root/.kube/config

# Generate join command BEFORE rewriting kubeconfig (needs internal IP to connect)
echo "生成join命令..."
JOIN_CMD=$(kubeadm token create --print-join-command 2>/dev/null)

# Patch kubeadm-config ConfigMap for NAT scenarios
# After TLS bootstrap, kubeadm join reads this ConfigMap for the API server address
# Without this fix, worker nodes from external networks cannot complete join
if [ "$ORIGINAL_MASTER_IP" != "$MASTER_IP" ]; then
    echo "修复kubeadm-config ConfigMap中的API Server地址 (${MASTER_IP} -> ${ORIGINAL_MASTER_IP})..."
    kubectl -n kube-system get cm kubeadm-config -o yaml | \
        sed "s|${MASTER_IP}|${ORIGINAL_MASTER_IP}|g" | \
        kubectl apply -f - 2>&1 || echo "WARNING: kubeadm-config修复失败"

    echo "修复cluster-info ConfigMap中的API Server地址..."
    kubectl -n kube-public get cm cluster-info -o yaml | \
        sed "s|${MASTER_IP}|${ORIGINAL_MASTER_IP}|g" | \
        kubectl apply -f - 2>&1 || echo "WARNING: cluster-info修复失败"
fi

# Now rewrite kubeconfig to use the public IP for external platform access
if [ "$ORIGINAL_MASTER_IP" != "$MASTER_IP" ]; then
    echo "替换kubeconfig中的内网IP为公网IP (${MASTER_IP} -> ${ORIGINAL_MASTER_IP})..."
    sed -i "s|https://${MASTER_IP}:|https://${ORIGINAL_MASTER_IP}:|g" /etc/kubernetes/admin.conf
    # Also rewrite the join command IP for worker nodes connecting from outside
    JOIN_CMD=$(echo "$JOIN_CMD" | sed "s|${MASTER_IP}:|${ORIGINAL_MASTER_IP}:|g")
fi

echo "===JOIN_COMMAND_START==="
echo "$JOIN_CMD"
echo "===JOIN_COMMAND_END==="

# Output kubeconfig for platform auto-registration (now with public IP)
echo "输出kubeconfig..."
echo "===KUBECONFIG_START==="
cat /etc/kubernetes/admin.conf
echo "===KUBECONFIG_END==="

echo "===== Master节点初始化完成 ====="
