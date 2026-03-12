#!/bin/bash
set -e

MASTER_IP="{{MASTER_IP}}"
TOKEN="{{TOKEN}}"
CA_HASH="{{CA_HASH}}"

echo "===== Worker节点加入集群开始 ====="
echo "Master地址: ${MASTER_IP}:6443"

# Use a JoinConfiguration file to ensure we connect to the correct API server address
# This avoids issues where kubeadm-config ConfigMap contains an internal IP
# that is unreachable from worker nodes on different networks (NAT/cloud scenarios)
cat > /tmp/kubeadm-join-config.yaml <<EOF
apiVersion: kubeadm.k8s.io/v1beta3
kind: JoinConfiguration
discovery:
  bootstrapToken:
    apiServerEndpoint: "${MASTER_IP}:6443"
    token: "${TOKEN}"
    caCertHashes:
    - "${CA_HASH}"
nodeRegistration:
  criSocket: unix:///var/run/containerd/containerd.sock
EOF

echo "执行 kubeadm join (使用配置文件)..."
kubeadm join --config /tmp/kubeadm-join-config.yaml 2>&1

# Cleanup
rm -f /tmp/kubeadm-join-config.yaml

echo "===== Worker节点已成功加入集群 ====="
