#!/bin/bash
set -e

REGISTRY_URL="{{REGISTRY_URL}}"

echo "===== 安装containerd开始 ====="
if [ -n "$REGISTRY_URL" ]; then
    echo "使用私有镜像仓库: ${REGISTRY_URL}"
fi

# Detect OS
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_ID=$ID
else
    echo "ERROR: 无法检测操作系统"
    exit 1
fi

echo "检测到操作系统: $OS_ID"

install_containerd_centos() {
    echo "安装依赖..."
    yum install -y yum-utils device-mapper-persistent-data lvm2 2>/dev/null \
        || dnf install -y dnf-utils device-mapper-persistent-data lvm2

    echo "添加Docker仓库(阿里云镜像)..."
    yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo 2>/dev/null \
        || dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
    # Replace download.docker.com with Aliyun mirror in repo file
    sed -i 's+download.docker.com+mirrors.aliyun.com/docker-ce+' /etc/yum.repos.d/docker-ce.repo 2>/dev/null || true

    # containerd v2.x has incompatible config format (sandbox_image breaks kubeadm)
    # Pin to v1.7.x which is stable and fully compatible with K8s 1.28-1.31
    INSTALLED_VER=$(rpm -q containerd.io --queryformat '%{VERSION}' 2>/dev/null || echo "none")
    if [[ "$INSTALLED_VER" == 2.* ]]; then
        echo "检测到containerd v${INSTALLED_VER}(不兼容K8s)，移除..."
        systemctl stop containerd 2>/dev/null || true
        dnf remove -y containerd.io 2>/dev/null || yum remove -y containerd.io 2>/dev/null || true
    fi

    echo "安装containerd 1.7.x (K8s兼容版)..."
    if command -v dnf &>/dev/null; then
        dnf install -y 'containerd.io-1.7*' --allowerasing 2>/dev/null \
            || dnf install -y 'containerd.io-1.6*' --allowerasing 2>/dev/null \
            || { echo "WARNING: 1.7.x不可用，安装最新版"; dnf install -y containerd.io --allowerasing; }
    else
        yum install -y 'containerd.io-1.7*' 2>/dev/null \
            || yum install -y 'containerd.io-1.6*' 2>/dev/null \
            || { echo "WARNING: 1.7.x不可用，安装最新版"; yum install -y containerd.io; }
    fi

    configure_containerd
}

install_containerd_ubuntu() {
    echo "安装依赖..."
    apt-get update
    apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release

    echo "添加Docker GPG密钥和仓库(阿里云镜像)..."
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" > /etc/apt/sources.list.d/docker.list

    echo "安装containerd 1.7.x..."
    apt-get update
    # Pin to 1.7.x for K8s compatibility
    WANTED=$(apt-cache madison containerd.io 2>/dev/null | awk '/1\.7/{print $3}' | head -1)
    if [ -n "$WANTED" ]; then
        apt-get install -y "containerd.io=$WANTED"
    else
        apt-get install -y containerd.io
    fi

    configure_containerd
}

configure_containerd() {
    echo "配置containerd..."
    containerd --version
    mkdir -p /etc/containerd
    containerd config default > /etc/containerd/config.toml

    # Enable SystemdCgroup
    sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

    if [ -n "$REGISTRY_URL" ]; then
        # ===== 私有仓库模式：使用 Harbor 私有仓库 =====
        echo "配置私有镜像仓库模式: ${REGISTRY_URL}"

        # sandbox_image 从私有仓库拉取
        PAUSE_IMAGE="${REGISTRY_URL}/google_containers/pause:3.10"
        if grep -q 'sandbox_image' /etc/containerd/config.toml; then
            sed -i "s|sandbox_image = .*|sandbox_image = \"${PAUSE_IMAGE}\"|" /etc/containerd/config.toml
        fi

        # 启用 certs.d 目录
        if ! grep -q 'config_path' /etc/containerd/config.toml; then
            sed -i '/\[plugins\."io\.containerd\.grpc\.v1\.cri"\.registry\]/a\      config_path = "/etc/containerd/certs.d"' /etc/containerd/config.toml 2>/dev/null || true
        else
            sed -i 's|config_path = .*|config_path = "/etc/containerd/certs.d"|' /etc/containerd/config.toml
        fi

        # 配置私有仓库为 insecure registry（HTTP 访问）
        echo "配置 ${REGISTRY_URL} 为 insecure registry..."
        mkdir -p "/etc/containerd/certs.d/${REGISTRY_URL}"
        cat > "/etc/containerd/certs.d/${REGISTRY_URL}/hosts.toml" <<EOHOST
server = "http://${REGISTRY_URL}"

[host."http://${REGISTRY_URL}"]
  capabilities = ["pull", "resolve", "push"]
  skip_verify = true
EOHOST

        # 将 docker.io / registry.k8s.io / ghcr.io / quay.io 全部指向私有仓库
        for registry in docker.io registry.k8s.io ghcr.io quay.io; do
            echo "配置 ${registry} -> ${REGISTRY_URL}..."
            mkdir -p "/etc/containerd/certs.d/${registry}"
            cat > "/etc/containerd/certs.d/${registry}/hosts.toml" <<EOHOST
server = "https://${registry}"

[host."http://${REGISTRY_URL}"]
  capabilities = ["pull", "resolve"]
  skip_verify = true
EOHOST
        done

        echo "私有仓库镜像配置完成"
    else
        # ===== 公共源模式：自动探测国内镜像加速 =====
        # Update sandbox image to use Aliyun mirror (avoids registry.k8s.io timeout in China)
        PAUSE_IMAGE="registry.aliyuncs.com/google_containers/pause:3.10"
        if grep -q 'sandbox_image' /etc/containerd/config.toml; then
            sed -i "s|sandbox_image = .*|sandbox_image = \"${PAUSE_IMAGE}\"|" /etc/containerd/config.toml
        else
            echo "WARNING: sandbox_image不在默认配置中"
        fi

        # 配置 containerd 镜像加速（国内无法直接访问 docker.io / registry.k8s.io / ghcr.io）
        # 使用多个备选源，containerd 会按顺序尝试，第一个失败自动 fallback 到下一个
        echo "配置containerd镜像加速..."

        # 启用 certs.d 目录作为 registry 配置路径
        if ! grep -q 'config_path' /etc/containerd/config.toml; then
            sed -i '/\[plugins\."io\.containerd\.grpc\.v1\.cri"\.registry\]/a\      config_path = "/etc/containerd/certs.d"' /etc/containerd/config.toml 2>/dev/null || true
        else
            sed -i 's|config_path = .*|config_path = "/etc/containerd/certs.d"|' /etc/containerd/config.toml
        fi

        # 自动探测可用的 docker.io 镜像源
        DOCKER_MIRRORS=("https://dockerpull.org" "https://docker.1panel.live" "https://docker.m.daocloud.io" "https://dockerproxy.cn")
        echo "探测可用的docker.io镜像源..."
        mkdir -p /etc/containerd/certs.d/docker.io
        {
            echo 'server = "https://docker.io"'
            for mirror in "${DOCKER_MIRRORS[@]}"; do
                if curl -s --connect-timeout 3 --max-time 5 "${mirror}/v2/" >/dev/null 2>&1 || \
                   curl -sI --connect-timeout 3 --max-time 5 "${mirror}/v2/" 2>/dev/null | grep -qE "200|401"; then
                    echo "  可用: $mirror"  >&2
                    echo "[host.\"${mirror}\"]"
                    echo '  capabilities = ["pull", "resolve"]'
                else
                    echo "  不可用: $mirror" >&2
                fi
            done
        } > /etc/containerd/certs.d/docker.io/hosts.toml

        # 自动探测可用的 registry.k8s.io 镜像源
        K8S_MIRRORS=("https://k8s.m.daocloud.io" "https://registry.k8s.io")
        echo "探测可用的registry.k8s.io镜像源..."
        mkdir -p /etc/containerd/certs.d/registry.k8s.io
        {
            echo 'server = "https://registry.k8s.io"'
            for mirror in "${K8S_MIRRORS[@]}"; do
                if curl -s --connect-timeout 3 --max-time 5 "${mirror}/v2/" >/dev/null 2>&1 || \
                   curl -sI --connect-timeout 3 --max-time 5 "${mirror}/v2/" 2>/dev/null | grep -qE "200|401"; then
                    echo "  可用: $mirror" >&2
                    echo "[host.\"${mirror}\"]"
                    echo '  capabilities = ["pull", "resolve"]'
                else
                    echo "  不可用: $mirror" >&2
                fi
            done
        } > /etc/containerd/certs.d/registry.k8s.io/hosts.toml

        # 自动探测可用的 ghcr.io 镜像源（Flannel、Cilium 等）
        GHCR_MIRRORS=("https://ghcr.m.daocloud.io" "https://ghcr.io")
        echo "探测可用的ghcr.io镜像源..."
        mkdir -p /etc/containerd/certs.d/ghcr.io
        {
            echo 'server = "https://ghcr.io"'
            for mirror in "${GHCR_MIRRORS[@]}"; do
                if curl -s --connect-timeout 3 --max-time 5 "${mirror}/v2/" >/dev/null 2>&1 || \
                   curl -sI --connect-timeout 3 --max-time 5 "${mirror}/v2/" 2>/dev/null | grep -qE "200|401"; then
                    echo "  可用: $mirror" >&2
                    echo "[host.\"${mirror}\"]"
                    echo '  capabilities = ["pull", "resolve"]'
                else
                    echo "  不可用: $mirror" >&2
                fi
            done
        } > /etc/containerd/certs.d/ghcr.io/hosts.toml

        # 自动探测可用的 quay.io 镜像源
        QUAY_MIRRORS=("https://quay.m.daocloud.io" "https://quay.io")
        echo "探测可用的quay.io镜像源..."
        mkdir -p /etc/containerd/certs.d/quay.io
        {
            echo 'server = "https://quay.io"'
            for mirror in "${QUAY_MIRRORS[@]}"; do
                if curl -s --connect-timeout 3 --max-time 5 "${mirror}/v2/" >/dev/null 2>&1 || \
                   curl -sI --connect-timeout 3 --max-time 5 "${mirror}/v2/" 2>/dev/null | grep -qE "200|401"; then
                    echo "  可用: $mirror" >&2
                    echo "[host.\"${mirror}\"]"
                    echo '  capabilities = ["pull", "resolve"]'
                else
                    echo "  不可用: $mirror" >&2
                fi
            done
        } > /etc/containerd/certs.d/quay.io/hosts.toml

        echo "镜像加速配置完成"
    fi

    echo "最终配置:"
    for reg in docker.io registry.k8s.io ghcr.io quay.io; do
        echo "--- $reg ---"
        cat /etc/containerd/certs.d/$reg/hosts.toml 2>/dev/null || echo "  未配置"
    done

    # Verify
    echo "sandbox_image配置:"
    grep sandbox_image /etc/containerd/config.toml || echo "  未找到"
    echo "SystemdCgroup配置:"
    grep SystemdCgroup /etc/containerd/config.toml || echo "  未找到"
    echo "registry config_path:"
    grep config_path /etc/containerd/config.toml || echo "  未找到"

    echo "启动containerd..."
    systemctl daemon-reload
    systemctl enable containerd
    systemctl restart containerd
    sleep 2

    echo "验证containerd状态..."
    systemctl is-active containerd
}

case "$OS_ID" in
    centos|rhel|rocky|almalinux|fedora|opencloudos|tencentos|anolis|openeuler|kylin)
        install_containerd_centos
        ;;
    ubuntu|debian)
        install_containerd_ubuntu
        ;;
    *)
        echo "ERROR: 不支持的操作系统: $OS_ID"
        exit 1
        ;;
esac

echo "===== containerd安装完成 ====="
