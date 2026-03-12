#!/bin/bash
set -e

INSTALL_PATH="${INSTALL_PATH}"
REGISTRY_HOST="${REGISTRY_HOST}"
ADMIN_USER="${ADMIN_USER}"
ADMIN_PASSWORD="${ADMIN_PASSWORD}"
VERSION="${VERSION}"
USE_SSL="${USE_SSL}"
SERVICE_PORT="${SERVICE_PORT}"

echo "=== Docker Distribution (Registry) Installation ==="
echo "Install path: $INSTALL_PATH"
echo "Host: $REGISTRY_HOST"
echo "Version: $VERSION"

# Create directories
REGISTRY_HOME="$INSTALL_PATH/registry"
mkdir -p "$REGISTRY_HOME/data" "$REGISTRY_HOME/auth" "$REGISTRY_HOME/certs"

# Install docker if not present
if ! command -v docker &> /dev/null; then
    echo "Installing Docker..."
    if curl -fsSL --connect-timeout 10 https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo -o /etc/yum.repos.d/docker-ce.repo 2>/dev/null; then
        sed -i 's|download.docker.com|mirrors.aliyun.com/docker-ce|g' /etc/yum.repos.d/docker-ce.repo
        yum install -y docker-ce docker-ce-cli containerd.io 2>&1
    elif curl -fsSL --connect-timeout 10 https://get.docker.com -o /tmp/get-docker.sh 2>/dev/null; then
        sh /tmp/get-docker.sh 2>&1
    else
        echo "ERROR: Cannot install Docker. Please install manually."
        exit 1
    fi
    systemctl enable docker 2>&1 || true
    systemctl start docker 2>&1
fi

echo "Docker version: $(docker --version)"

# Determine image tag
if [ -z "$VERSION" ] || [ "$VERSION" = "latest" ]; then
    REGISTRY_IMAGE="registry:2"
else
    REGISTRY_IMAGE="registry:${VERSION}"
fi

# Setup basic auth if credentials provided
AUTH_ARGS=""
if [ -n "$ADMIN_USER" ] && [ -n "$ADMIN_PASSWORD" ]; then
    echo "Setting up basic authentication..."
    # Use docker to generate htpasswd (avoids needing apache2-utils installed)
    docker run --rm --entrypoint htpasswd registry:2 -Bbn "$ADMIN_USER" "$ADMIN_PASSWORD" > "$REGISTRY_HOME/auth/htpasswd" 2>&1
    AUTH_ARGS="-v $REGISTRY_HOME/auth:/auth -e REGISTRY_AUTH=htpasswd -e REGISTRY_AUTH_HTPASSWD_REALM=Registry -e REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd"
fi

# Stop existing container if running
docker stop registry 2>/dev/null || true
docker rm registry 2>/dev/null || true

echo "Pulling registry image: $REGISTRY_IMAGE..."
docker pull "$REGISTRY_IMAGE" 2>&1

# Setup SSL and port
REGISTRY_PORT="${SERVICE_PORT:-5000}"
SSL_ARGS=""
PORT_ARGS="-p ${REGISTRY_PORT}:5000"
if [ "$USE_SSL" = "true" ] && [ -f "$REGISTRY_HOME/certs/domain.crt" ]; then
    SSL_ARGS="-v $REGISTRY_HOME/certs:/certs -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/domain.crt -e REGISTRY_HTTP_TLS_KEY=/certs/domain.key"
    PORT_ARGS="-p 443:5000"
fi

echo "Starting Docker Registry container..."
eval docker run -d \
    --name registry \
    --restart always \
    $PORT_ARGS \
    $SSL_ARGS \
    -v "$REGISTRY_HOME/data:/var/lib/registry" \
    -e REGISTRY_STORAGE_DELETE_ENABLED=true \
    $AUTH_ARGS \
    "$REGISTRY_IMAGE" 2>&1

echo "=== Docker Registry installation completed ==="
echo "Access Registry at: http://$REGISTRY_HOST:$REGISTRY_PORT"

# Verify registry is running
sleep 5
docker ps --filter name=registry --format "table {{.Status}}"

# Test connectivity
echo "Testing registry connectivity..."
curl -sf "http://localhost:${REGISTRY_PORT}/v2/" && echo "Registry is responding!" || echo "Registry is starting up..."
