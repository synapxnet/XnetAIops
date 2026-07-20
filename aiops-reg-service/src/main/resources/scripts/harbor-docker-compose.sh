#!/bin/bash
set -e

INSTALL_PATH="${INSTALL_PATH}"
REGISTRY_HOST="${REGISTRY_HOST}"
ADMIN_PASSWORD="${ADMIN_PASSWORD}"
VERSION="${VERSION}"
USE_SSL="${USE_SSL}"
SERVICE_PORT="${SERVICE_PORT}"

echo "=== Harbor Installation ==="
echo "Install path: $INSTALL_PATH"
echo "Host: $REGISTRY_HOST"
echo "Version: $VERSION"

# Create install directory
mkdir -p "$INSTALL_PATH"
cd "$INSTALL_PATH"

# Install docker if not present
if ! command -v docker &> /dev/null; then
    echo "Installing Docker..."
    # Try Aliyun mirror first (China), fallback to official
    if curl -fsSL --connect-timeout 10 https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo -o /etc/yum.repos.d/docker-ce.repo 2>/dev/null; then
        echo "Using Aliyun mirror for Docker..."
        sed -i 's|download.docker.com|mirrors.aliyun.com/docker-ce|g' /etc/yum.repos.d/docker-ce.repo
        yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin 2>&1
    elif curl -fsSL --connect-timeout 10 https://get.docker.com -o /tmp/get-docker.sh 2>/dev/null; then
        echo "Using official Docker installer..."
        sh /tmp/get-docker.sh 2>&1
    else
        echo "ERROR: Cannot install Docker - no network access to Docker repositories"
        echo "Please install Docker manually before deploying Harbor."
        exit 1
    fi
    systemctl enable docker 2>&1 || true
    systemctl start docker 2>&1
    echo "Docker installed successfully"
fi

echo "Docker version: $(docker --version)"

# Install docker-compose if not present (check both standalone and plugin)
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo "Installing docker-compose..."
    COMPOSE_ARCH=$(uname -m)
    # Try Aliyun/DaoCloud mirror first
    curl -L --connect-timeout 15 "https://get.daocloud.io/docker/compose/releases/latest/download/docker-compose-$(uname -s)-${COMPOSE_ARCH}" -o /usr/local/bin/docker-compose 2>&1 || \
    curl -L --connect-timeout 15 "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-${COMPOSE_ARCH}" -o /usr/local/bin/docker-compose 2>&1
    chmod +x /usr/local/bin/docker-compose
    echo "docker-compose installed: $(docker-compose version)"
fi

# Determine Harbor version
if [ -z "$VERSION" ] || [ "$VERSION" = "latest" ]; then
    HARBOR_VERSION="v2.11.0"
else
    HARBOR_VERSION="v$VERSION"
fi

echo "Harbor version: $HARBOR_VERSION"

HARBOR_TARBALL="harbor-offline-installer-${HARBOR_VERSION}.tgz"

download_harbor() {
    local url="$1"
    echo "Trying: $url"
    curl -L --connect-timeout 30 --max-time 600 -C - -o "$HARBOR_TARBALL" "$url" 2>&1
    # Verify the downloaded file is a valid gzip
    if [ -s "$HARBOR_TARBALL" ] && gzip -t "$HARBOR_TARBALL" 2>/dev/null; then
        return 0
    fi
    echo "Download incomplete or corrupted, removing..."
    rm -f "$HARBOR_TARBALL"
    return 1
}

if [ -f "$HARBOR_TARBALL" ] && gzip -t "$HARBOR_TARBALL" 2>/dev/null; then
    echo "Harbor tarball already exists and is valid, skipping download."
else
    rm -f "$HARBOR_TARBALL"
    echo "Downloading Harbor $HARBOR_VERSION (offline installer ~700MB, please wait)..."
    # Try multiple mirrors with integrity check
    download_harbor "https://ghfast.top/https://github.com/goharbor/harbor/releases/download/${HARBOR_VERSION}/${HARBOR_TARBALL}" || \
    download_harbor "https://gh-proxy.com/https://github.com/goharbor/harbor/releases/download/${HARBOR_VERSION}/${HARBOR_TARBALL}" || \
    download_harbor "https://github.com/goharbor/harbor/releases/download/${HARBOR_VERSION}/${HARBOR_TARBALL}" || \
    { echo "ERROR: Failed to download Harbor installer after all attempts. The file is ~700MB, check network bandwidth."; exit 1; }
fi

echo "Extracting Harbor..."
tar xzf "$HARBOR_TARBALL" || { echo "ERROR: Tarball extraction failed. Deleting corrupted file, please retry."; rm -f "$HARBOR_TARBALL"; exit 1; }
cd harbor

# Generate harbor.yml from template
cp harbor.yml.tmpl harbor.yml

# Configure harbor.yml
sed -i "s|hostname: reg.mydomain.com|hostname: $REGISTRY_HOST|g" harbor.yml
sed -i "s|harbor_admin_password: Harbor12345|harbor_admin_password: $ADMIN_PASSWORD|g" harbor.yml

# Set custom HTTP port if not 80
if [ -n "$SERVICE_PORT" ] && [ "$SERVICE_PORT" != "80" ]; then
    echo "Configuring custom port: $SERVICE_PORT"
    sed -i "s|port: 80|port: $SERVICE_PORT|g" harbor.yml
fi

if [ "$USE_SSL" = "false" ]; then
    # Comment out the entire HTTPS block
    sed -i 's/^https:/#https:/' harbor.yml
    sed -i '/^#https:/,/^[a-z]/{s/^  /#  /}' harbor.yml
fi

echo "Running Harbor installer..."
./install.sh --with-trivy 2>&1

echo "=== Harbor installation completed ==="
echo "Access Harbor at: http://$REGISTRY_HOST"
echo "Admin user: admin"

# Verify Harbor is running
sleep 10
if command -v docker-compose &> /dev/null; then
    docker-compose ps
else
    docker compose ps
fi
