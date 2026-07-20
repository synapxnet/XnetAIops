#!/bin/bash
set -e

K8S_VERSION="{{K8S_VERSION}}"
# Extract major.minor for repo
K8S_MINOR=$(echo "$K8S_VERSION" | grep -oP '^\d+\.\d+')

echo "===== 安装kubeadm/kubelet/kubectl (v${K8S_VERSION}) 开始 ====="

# Detect OS
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_ID=$ID
else
    echo "ERROR: 无法检测操作系统"
    exit 1
fi

echo "检测到操作系统: $OS_ID"

install_kubeadm_centos() {
    echo "添加Kubernetes仓库(阿里云镜像)..."
    cat > /etc/yum.repos.d/kubernetes.repo <<EOF
[kubernetes]
name=Kubernetes
baseurl=https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_MINOR}/rpm/
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_MINOR}/rpm/repodata/repomd.xml.key
EOF

    echo "安装kubeadm kubelet kubectl..."
    if command -v dnf &>/dev/null; then
        dnf install -y kubelet-${K8S_VERSION} kubeadm-${K8S_VERSION} kubectl-${K8S_VERSION} --disableexcludes=kubernetes
    else
        yum install -y kubelet-${K8S_VERSION} kubeadm-${K8S_VERSION} kubectl-${K8S_VERSION} --disableexcludes=kubernetes
    fi

    echo "启用kubelet..."
    systemctl enable kubelet
}

install_kubeadm_ubuntu() {
    echo "添加Kubernetes GPG密钥和仓库(阿里云镜像)..."
    mkdir -p /etc/apt/keyrings
    curl -fsSL https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_MINOR}/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

    echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://mirrors.aliyun.com/kubernetes-new/core/stable/v${K8S_MINOR}/deb/ /" > /etc/apt/sources.list.d/kubernetes.list

    echo "安装kubeadm kubelet kubectl..."
    apt-get update
    apt-get install -y kubelet=${K8S_VERSION}-* kubeadm=${K8S_VERSION}-* kubectl=${K8S_VERSION}-*
    apt-mark hold kubelet kubeadm kubectl

    echo "启用kubelet..."
    systemctl enable kubelet
}

case "$OS_ID" in
    centos|rhel|rocky|almalinux|fedora|opencloudos|tencentos|anolis|openeuler|kylin)
        install_kubeadm_centos
        ;;
    ubuntu|debian)
        install_kubeadm_ubuntu
        ;;
    *)
        echo "ERROR: 不支持的操作系统: $OS_ID"
        exit 1
        ;;
esac

echo "验证安装..."
kubeadm version
kubelet --version
kubectl version --client

echo "===== kubeadm/kubelet/kubectl 安装完成 ====="
