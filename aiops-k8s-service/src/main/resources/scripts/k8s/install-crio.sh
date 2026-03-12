#!/bin/bash
set -e

K8S_VERSION="{{K8S_VERSION}}"
# Extract major.minor for CRI-O version
CRIO_VERSION=$(echo "$K8S_VERSION" | grep -oP '^\d+\.\d+')

echo "===== 安装CRI-O开始 (version: $CRIO_VERSION) ====="

# Detect OS
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_ID=$ID
else
    echo "ERROR: 无法检测操作系统"
    exit 1
fi

echo "检测到操作系统: $OS_ID"

install_crio_centos() {
    echo "添加CRI-O仓库..."
    cat > /etc/yum.repos.d/cri-o.repo <<EOF
[cri-o]
name=CRI-O
baseurl=https://pkgs.k8s.io/addons:/cri-o:/stable:/v${CRIO_VERSION}/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/addons:/cri-o:/stable:/v${CRIO_VERSION}/rpm/repodata/repomd.xml.key
EOF

    echo "安装CRI-O..."
    if command -v dnf &>/dev/null; then
        dnf install -y cri-o
    else
        yum install -y cri-o
    fi

    echo "启动CRI-O..."
    systemctl daemon-reload
    systemctl enable crio
    systemctl start crio
}

install_crio_ubuntu() {
    echo "添加CRI-O仓库..."
    curl -fsSL https://pkgs.k8s.io/addons:/cri-o:/stable:/v${CRIO_VERSION}/deb/Release.key | gpg --dearmor -o /etc/apt/keyrings/cri-o-apt-keyring.gpg
    echo "deb [signed-by=/etc/apt/keyrings/cri-o-apt-keyring.gpg] https://pkgs.k8s.io/addons:/cri-o:/stable:/v${CRIO_VERSION}/deb/ /" > /etc/apt/sources.list.d/cri-o.list

    echo "安装CRI-O..."
    apt-get update
    apt-get install -y cri-o

    echo "启动CRI-O..."
    systemctl daemon-reload
    systemctl enable crio
    systemctl start crio
}

case "$OS_ID" in
    centos|rhel|rocky|almalinux|fedora|opencloudos|tencentos|anolis|openeuler|kylin)
        install_crio_centos
        ;;
    ubuntu|debian)
        install_crio_ubuntu
        ;;
    *)
        echo "ERROR: 不支持的操作系统: $OS_ID"
        exit 1
        ;;
esac

echo "验证CRI-O状态..."
systemctl is-active crio

echo "===== CRI-O安装完成 ====="
