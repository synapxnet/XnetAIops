#!/bin/bash
set -e

echo "===== 节点环境准备开始 ====="

# Set hostname
HOSTNAME="{{HOSTNAME}}"
if [ -n "$HOSTNAME" ]; then
    echo "设置主机名: $HOSTNAME"
    hostnamectl set-hostname "$HOSTNAME"
fi

# Disable swap
echo "关闭swap..."
swapoff -a
sed -i '/ swap / s/^/#/' /etc/fstab

# Disable SELinux (if present)
if command -v setenforce &>/dev/null; then
    echo "关闭SELinux..."
    setenforce 0 || true
    sed -i 's/^SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config 2>/dev/null || true
    sed -i 's/^SELINUX=permissive/SELINUX=disabled/' /etc/selinux/config 2>/dev/null || true
fi

# Disable firewall
echo "关闭防火墙..."
if systemctl is-active --quiet firewalld 2>/dev/null; then
    systemctl stop firewalld
    systemctl disable firewalld
    echo "firewalld 已关闭"
fi
if systemctl is-active --quiet ufw 2>/dev/null; then
    systemctl stop ufw
    systemctl disable ufw
    echo "ufw 已关闭"
fi

# Load kernel modules
echo "加载内核模块..."
cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

# Set sysctl parameters
echo "配置网络参数..."
cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sysctl --system > /dev/null 2>&1

# Configure time sync
echo "配置时间同步..."
if command -v chronyc &>/dev/null; then
    systemctl enable chronyd 2>/dev/null || true
    systemctl start chronyd 2>/dev/null || true
    echo "chronyd 已启动"
elif command -v ntpd &>/dev/null; then
    systemctl enable ntpd 2>/dev/null || true
    systemctl start ntpd 2>/dev/null || true
    echo "ntpd 已启动"
else
    # Try to install chrony
    if command -v yum &>/dev/null; then
        yum install -y chrony > /dev/null 2>&1 || true
    elif command -v apt-get &>/dev/null; then
        apt-get install -y chrony > /dev/null 2>&1 || true
    fi
    systemctl enable chronyd 2>/dev/null || true
    systemctl start chronyd 2>/dev/null || true
fi

echo "===== 节点环境准备完成 ====="
