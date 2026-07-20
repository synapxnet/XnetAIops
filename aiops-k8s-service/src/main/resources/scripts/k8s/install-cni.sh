#!/bin/bash
set -e

CNI_PLUGIN="{{CNI_PLUGIN}}"
POD_CIDR="{{POD_CIDR}}"
REGISTRY_URL="{{REGISTRY_URL}}"

echo "===== 安装网络插件 (${CNI_PLUGIN}) 开始 ====="
if [ -n "$REGISTRY_URL" ]; then
    echo "使用私有镜像仓库: ${REGISTRY_URL}"
fi

# 预拉取镜像函数：确保所有镜像就绪后再 apply，避免 Pod 长时间 Pending
pre_pull_images() {
    local yaml_file="$1"
    local images
    images=$(grep -oP 'image:\s*\K\S+' "$yaml_file" | sort -u)
    if [ -z "$images" ]; then
        echo "未从 manifest 中解析到镜像，跳过预拉取"
        return 0
    fi

    echo "需要预拉取以下镜像:"
    echo "$images"
    local failed=0
    for img in $images; do
        echo "拉取镜像: $img ..."
        if crictl pull "$img" 2>&1; then
            echo "  成功: $img"
        else
            echo "  WARN: $img 拉取失败，尝试继续..."
            failed=$((failed + 1))
        fi
    done
    if [ $failed -gt 0 ]; then
        echo "WARNING: ${failed} 个镜像拉取失败，apply 后 Pod 可能需要等待"
    else
        echo "所有镜像预拉取完成"
    fi
}

case "$CNI_PLUGIN" in
    calico)
        echo "安装Calico..."
        # Download calico manifest（优先使用国内镜像源，GitHub 在国内经常超时）
        CALICO_VER="v3.27.0"
        CALICO_YAML="/tmp/calico.yaml"

        echo "下载Calico manifest..."
        curl -fsSL --connect-timeout 10 "https://mirror.ghproxy.com/https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VER}/manifests/calico.yaml" -o "$CALICO_YAML" 2>/dev/null \
            || curl -fsSL --connect-timeout 10 "https://ghfast.top/https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VER}/manifests/calico.yaml" -o "$CALICO_YAML" 2>/dev/null \
            || curl -fsSL --connect-timeout 30 "https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VER}/manifests/calico.yaml" -o "$CALICO_YAML"

        # Update CIDR if needed
        if [ -n "$POD_CIDR" ] && [ "$POD_CIDR" != "127.0.0.1/16" ]; then
            echo "配置Pod CIDR: ${POD_CIDR}"
            sed -i "s|# - name: CALICO_IPV4POOL_CIDR|- name: CALICO_IPV4POOL_CIDR|" "$CALICO_YAML"
            sed -i "s|#   value: \"127.0.0.1/16\"|  value: \"${POD_CIDR}\"|" "$CALICO_YAML"
        fi

        # 当使用私有仓库时，替换 calico 镜像地址
        if [ -n "$REGISTRY_URL" ]; then
            echo "替换Calico镜像地址为私有仓库..."
            sed -i "s|docker.io/calico/|${REGISTRY_URL}/calico/|g" "$CALICO_YAML"
            sed -i "s|calico/cni:|${REGISTRY_URL}/calico/cni:|g" "$CALICO_YAML"
            sed -i "s|calico/node:|${REGISTRY_URL}/calico/node:|g" "$CALICO_YAML"
            sed -i "s|calico/kube-controllers:|${REGISTRY_URL}/calico/kube-controllers:|g" "$CALICO_YAML"
        fi

        # 预拉取所有 calico 镜像，确保就绪后再 apply
        pre_pull_images "$CALICO_YAML"

        kubectl apply -f "$CALICO_YAML"
        echo "Calico 安装完成"
        ;;

    flannel)
        echo "安装Flannel..."
        FLANNEL_YAML="/tmp/kube-flannel.yml"
        curl -fsSL --connect-timeout 10 "https://mirror.ghproxy.com/https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml" -o "$FLANNEL_YAML" 2>/dev/null \
            || curl -fsSL --connect-timeout 30 "https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml" -o "$FLANNEL_YAML"

        # 当使用私有仓库时，替换 flannel 镜像地址（flannel 镜像来自 ghcr.io 和 docker.io）
        if [ -n "$REGISTRY_URL" ]; then
            echo "替换Flannel镜像地址为私有仓库..."
            sed -i "s|ghcr.io/flannel-io/|${REGISTRY_URL}/flannel/|g" "$FLANNEL_YAML"
            sed -i "s|docker.io/flannel/|${REGISTRY_URL}/flannel/|g" "$FLANNEL_YAML"
            sed -i "s|docker.io/rancher/|${REGISTRY_URL}/library/|g" "$FLANNEL_YAML"
        fi

        pre_pull_images "$FLANNEL_YAML"

        kubectl apply -f "$FLANNEL_YAML"
        echo "Flannel 安装完成"
        ;;

    cilium)
        echo "安装Cilium..."
        # Install Cilium CLI if not present
        if ! command -v cilium &>/dev/null; then
            CILIUM_CLI_VERSION=$(curl -s https://raw.githubusercontent.com/cilium/cilium-cli/main/stable.txt)
            CLI_ARCH=amd64
            if [ "$(uname -m)" = "aarch64" ]; then CLI_ARCH=arm64; fi
            curl -L --fail --remote-name-all https://github.com/cilium/cilium-cli/releases/download/${CILIUM_CLI_VERSION}/cilium-linux-${CLI_ARCH}.tar.gz
            tar xzvf cilium-linux-${CLI_ARCH}.tar.gz -C /usr/local/bin
            rm -f cilium-linux-${CLI_ARCH}.tar.gz
        fi
        cilium install
        echo "Cilium 安装完成"
        ;;

    *)
        echo "ERROR: 不支持的CNI插件: $CNI_PLUGIN"
        exit 1
        ;;
esac

# Wait for CNI pods to be ready（延长超时到5分钟）
echo "等待网络插件Pod就绪..."
kubectl wait --for=condition=Ready pods --all -n kube-system --timeout=300s 2>/dev/null || true

echo "===== 网络插件安装完成 ====="
