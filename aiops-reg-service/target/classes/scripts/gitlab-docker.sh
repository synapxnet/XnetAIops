#!/bin/bash
set -e

INSTALL_PATH="${INSTALL_PATH}"
REGISTRY_HOST="${REGISTRY_HOST}"
ADMIN_PASSWORD="${ADMIN_PASSWORD}"
VERSION="${VERSION}"
USE_SSL="${USE_SSL}"
SERVICE_PORT="${SERVICE_PORT}"

echo "=== GitLab CE Installation ==="
echo "Install path: $INSTALL_PATH"
echo "Host: $REGISTRY_HOST"
echo "Version: $VERSION"

# Create directories
GITLAB_HOME="$INSTALL_PATH/gitlab"
mkdir -p "$GITLAB_HOME/config" "$GITLAB_HOME/logs" "$GITLAB_HOME/data"

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
    GITLAB_IMAGE="gitlab/gitlab-ce:latest"
else
    GITLAB_IMAGE="gitlab/gitlab-ce:${VERSION}-ce.0"
fi

# Stop existing container if running
docker stop gitlab 2>/dev/null || true
docker rm gitlab 2>/dev/null || true

echo "Pulling GitLab image: $GITLAB_IMAGE..."
docker pull "$GITLAB_IMAGE" 2>&1

# Determine ports
HTTP_PORT="${SERVICE_PORT:-80}"

# Determine external URL
if [ "$USE_SSL" = "true" ]; then
    EXTERNAL_URL="https://$REGISTRY_HOST"
else
    if [ "$HTTP_PORT" = "80" ]; then
        EXTERNAL_URL="http://$REGISTRY_HOST"
    else
        EXTERNAL_URL="http://$REGISTRY_HOST:$HTTP_PORT"
    fi
fi

echo "Starting GitLab container (port: $HTTP_PORT)..."
docker run -d \
    --name gitlab \
    --hostname "$REGISTRY_HOST" \
    --restart always \
    -p ${HTTP_PORT}:80 \
    -p 443:443 \
    -p 2222:22 \
    -v "$GITLAB_HOME/config:/etc/gitlab" \
    -v "$GITLAB_HOME/logs:/var/log/gitlab" \
    -v "$GITLAB_HOME/data:/var/opt/gitlab" \
    -e GITLAB_OMNIBUS_CONFIG="external_url '$EXTERNAL_URL'; gitlab_rails['initial_root_password']='$ADMIN_PASSWORD'; registry_external_url '$EXTERNAL_URL:5050'; gitlab_rails['registry_enabled']=true;" \
    "$GITLAB_IMAGE" 2>&1

echo "=== GitLab CE installation completed ==="
echo "Access GitLab at: $EXTERNAL_URL"
echo "Admin user: root"
echo "Note: GitLab may take 3-5 minutes to fully start up."

# Wait and check status
echo "Waiting for GitLab to start..."
sleep 30
docker ps --filter name=gitlab --format "table {{.Status}}"
