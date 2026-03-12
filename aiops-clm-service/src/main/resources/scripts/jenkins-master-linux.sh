#!/bin/bash
set -e

# ============================================================
# Jenkins Master 自动部署脚本 (Linux)
# 由 XnetAIops CLM 模块自动生成
# ============================================================

JENKINS_VERSION="${JENKINS_VERSION}"
JENKINS_PORT="${JENKINS_PORT}"
JENKINS_HOME="${JENKINS_HOME}"
JAVA_VERSION="${JAVA_VERSION}"
JAVA_OPTS="${JAVA_OPTS}"
ADMIN_USERNAME="${ADMIN_USERNAME}"
ADMIN_PASSWORD="${ADMIN_PASSWORD}"
ADMIN_EMAIL="${ADMIN_EMAIL}"
INSTALL_SUGGESTED="${INSTALL_SUGGESTED_PLUGINS}"
HOST_IP="${HOST_IP}"
TIMEZONE="${TIMEZONE}"

JENKINS_WAR_DIR="/opt/jenkins"
JENKINS_WAR="$JENKINS_WAR_DIR/jenkins.war"
JENKINS_LOG="/var/log/jenkins"
PLUGIN_MANAGER_JAR="$JENKINS_WAR_DIR/jenkins-plugin-manager.jar"
PLUGIN_MANAGER_VERSION="2.14.0"

echo "============================================"
echo "  Jenkins Master 部署开始"
echo "  版本: $JENKINS_VERSION"
echo "  端口: $JENKINS_PORT"
echo "  Home: $JENKINS_HOME"
echo "  Java: $JAVA_VERSION"
echo "============================================"

# ==================== 1. 系统检测 ====================
echo ""
echo "[1/9] 检测操作系统..."

if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_ID="$ID"
    OS_VERSION="$VERSION_ID"
    echo "  操作系统: $PRETTY_NAME"
else
    OS_ID="unknown"
    echo "  操作系统: 未知"
fi

# ==================== 2. 安装 Java ====================
echo ""
echo "[2/9] 检查/安装 Java $JAVA_VERSION..."

if command -v java &>/dev/null; then
    CURRENT_JAVA=$(java -version 2>&1 | head -1 | awk -F'"' '{print $2}' | cut -d'.' -f1)
    echo "  当前Java版本: $CURRENT_JAVA"
    if [ "$CURRENT_JAVA" -ge "$JAVA_VERSION" ] 2>/dev/null; then
        echo "  Java版本满足要求，跳过安装"
    else
        echo "  Java版本不满足，需要安装Java $JAVA_VERSION"
        NEED_JAVA=true
    fi
else
    echo "  未检测到Java，需要安装"
    NEED_JAVA=true
fi

if [ "$NEED_JAVA" = true ]; then
    case "$OS_ID" in
        ubuntu|debian)
            apt-get update -qq
            apt-get install -y -qq openjdk-${JAVA_VERSION}-jdk-headless 2>&1 | tail -3
            ;;
        centos|rhel|rocky|almalinux|fedora|opencloudos|tencentos)
            if command -v dnf &>/dev/null; then
                dnf install -y -q java-${JAVA_VERSION}-openjdk-headless 2>&1 | tail -3
            else
                yum install -y -q java-${JAVA_VERSION}-openjdk-headless 2>&1 | tail -3
            fi
            ;;
        *)
            echo "  警告: 不支持的操作系统($OS_ID)，尝试通用安装..."
            if command -v dnf &>/dev/null; then
                dnf install -y -q java-${JAVA_VERSION}-openjdk-headless 2>&1 || true
            elif command -v yum &>/dev/null; then
                yum install -y -q java-${JAVA_VERSION}-openjdk-headless 2>&1 || true
            elif command -v apt-get &>/dev/null; then
                apt-get update -qq && apt-get install -y -qq openjdk-${JAVA_VERSION}-jdk-headless 2>&1 || true
            else
                echo "  错误: 无法自动安装Java，请手动安装"
                exit 1
            fi
            ;;
    esac
    echo "  Java安装完成: $(java -version 2>&1 | head -1)"
fi

# ==================== 3. 创建目录 ====================
echo ""
echo "[3/9] 创建目录结构..."

mkdir -p "$JENKINS_WAR_DIR"
mkdir -p "$JENKINS_HOME"
mkdir -p "$JENKINS_HOME/plugins"
mkdir -p "$JENKINS_LOG"
mkdir -p "$JENKINS_HOME/init.groovy.d"

echo "  Jenkins WAR: $JENKINS_WAR_DIR"
echo "  Jenkins Home: $JENKINS_HOME"
echo "  Jenkins Log: $JENKINS_LOG"

# ==================== 4. 下载 Jenkins WAR ====================
echo ""
echo "[4/9] 下载 Jenkins WAR ($JENKINS_VERSION)..."

if [ -f "$JENKINS_WAR" ]; then
    EXISTING_VER=$(java -jar "$JENKINS_WAR" --version 2>/dev/null || echo "unknown")
    if [ "$EXISTING_VER" = "$JENKINS_VERSION" ]; then
        echo "  Jenkins $JENKINS_VERSION 已存在，跳过下载"
    else
        echo "  已存在版本 $EXISTING_VER，重新下载..."
        rm -f "$JENKINS_WAR"
    fi
fi

if [ ! -f "$JENKINS_WAR" ]; then
    MIRRORS=(
        "https://mirrors.tuna.tsinghua.edu.cn/jenkins/war-stable/${JENKINS_VERSION}/jenkins.war"
        "https://mirrors.huaweicloud.com/jenkins/war-stable/${JENKINS_VERSION}/jenkins.war"
        "https://get.jenkins.io/war-stable/${JENKINS_VERSION}/jenkins.war"
    )

    DOWNLOADED=false
    for MIRROR in "${MIRRORS[@]}"; do
        echo "  尝试下载: $MIRROR"
        if curl -fSL --connect-timeout 30 --max-time 600 -o "$JENKINS_WAR" "$MIRROR" 2>&1; then
            DOWNLOADED=true
            echo "  下载成功"
            break
        else
            echo "  下载失败，尝试下一个镜像..."
            rm -f "$JENKINS_WAR"
        fi
    done

    if [ "$DOWNLOADED" != true ]; then
        echo "  错误: 所有镜像均无法下载 Jenkins WAR"
        exit 1
    fi
fi

echo "  WAR文件大小: $(du -h "$JENKINS_WAR" | cut -f1)"

# ==================== 5. 安装插件 ====================
echo ""
echo "[5/9] 安装Jenkins插件..."

# 必需插件列表（Pipeline + 凭证 + Git + Blue Ocean）
REQUIRED_PLUGINS=(
    "workflow-aggregator"
    "pipeline-stage-view"
    "blueocean"
    "credentials-binding"
    "git"
    "github"
    "gitlab-plugin"
    "ssh-credentials"
    "antisamy-markup-formatter"
    "locale"
)

# 下载 Jenkins Plugin Installation Manager Tool
if [ ! -f "$PLUGIN_MANAGER_JAR" ]; then
    echo "  下载插件管理工具..."
    PM_MIRRORS=(
        "https://github.com/jenkinsci/plugin-installation-manager-tool/releases/download/${PLUGIN_MANAGER_VERSION}/jenkins-plugin-manager-${PLUGIN_MANAGER_VERSION}.jar"
        "https://mirror.ghproxy.com/https://github.com/jenkinsci/plugin-installation-manager-tool/releases/download/${PLUGIN_MANAGER_VERSION}/jenkins-plugin-manager-${PLUGIN_MANAGER_VERSION}.jar"
    )
    PM_DOWNLOADED=false
    for PM_MIRROR in "${PM_MIRRORS[@]}"; do
        echo "  尝试: $PM_MIRROR"
        if curl -fSL --connect-timeout 30 --max-time 120 -o "$PLUGIN_MANAGER_JAR" "$PM_MIRROR" 2>&1; then
            PM_DOWNLOADED=true
            echo "  插件管理工具下载成功"
            break
        else
            rm -f "$PLUGIN_MANAGER_JAR"
        fi
    done
    if [ "$PM_DOWNLOADED" != true ]; then
        echo "  警告: 无法下载插件管理工具，将在Jenkins启动后通过Groovy脚本安装插件"
    fi
fi

# 使用插件管理工具安装插件
if [ -f "$PLUGIN_MANAGER_JAR" ]; then
    PLUGIN_LIST=$(IFS=, ; echo "${REQUIRED_PLUGINS[*]}")
    echo "  安装插件: $PLUGIN_LIST"
    echo "  (这可能需要几分钟...)"

    # 设置镜像源加速插件下载
    export JENKINS_UC="https://mirrors.tuna.tsinghua.edu.cn/jenkins/updates"
    export JENKINS_UC_DOWNLOAD="https://mirrors.tuna.tsinghua.edu.cn/jenkins"

    java -jar "$PLUGIN_MANAGER_JAR" \
        --war "$JENKINS_WAR" \
        --plugin-download-directory "$JENKINS_HOME/plugins" \
        --plugins ${REQUIRED_PLUGINS[@]} \
        --verbose 2>&1 | tail -20 || {
            echo "  警告: 部分插件安装可能失败，Jenkins启动后可手动安装"
        }

    PLUGIN_COUNT=$(ls "$JENKINS_HOME/plugins/"*.jpi 2>/dev/null | wc -l)
    echo "  已安装 $PLUGIN_COUNT 个插件文件"
else
    # 备选方案: 生成Groovy脚本在Jenkins启动后安装插件
    echo "  使用Groovy脚本方式安装插件..."
    cat > "$JENKINS_HOME/init.groovy.d/00-install-plugins.groovy" << 'PLUGIN_GROOVY_EOF'
import jenkins.model.*
import hudson.model.*

def plugins = [
    "workflow-aggregator",
    "pipeline-stage-view",
    "blueocean",
    "credentials-binding",
    "git",
    "github",
    "gitlab-plugin",
    "ssh-credentials",
    "antisamy-markup-formatter",
    "locale"
]

def instance = Jenkins.getInstance()
def pm = instance.getPluginManager()
def uc = instance.getUpdateCenter()

// 确保更新中心已加载
if (uc.getSites().isEmpty() || uc.getAvailables().isEmpty()) {
    uc.updateAllSites()
    Thread.sleep(5000)
}

def installed = false
plugins.each { pluginName ->
    if (!pm.getPlugin(pluginName)) {
        def plugin = uc.getPlugin(pluginName)
        if (plugin) {
            println "安装插件: ${pluginName}"
            plugin.deploy(true).get()
            installed = true
        } else {
            println "插件未找到: ${pluginName}"
        }
    } else {
        println "插件已存在: ${pluginName}"
    }
}

if (installed) {
    println "插件安装完成，需要重启Jenkins"
    instance.safeRestart()
}
PLUGIN_GROOVY_EOF
    echo "  Groovy插件安装脚本已配置"
fi

# ==================== 6. 配置初始化脚本 ====================
echo ""
echo "[6/9] 配置Jenkins初始化..."

# 跳过安装向导
cat > "$JENKINS_HOME/init.groovy.d/01-skip-wizard.groovy" << 'GROOVY_EOF'
import jenkins.model.*
import hudson.util.*
import jenkins.install.*

def instance = Jenkins.getInstance()
instance.setInstallState(InstallState.INITIAL_SETUP_COMPLETED)
instance.save()
println "安装向导已跳过"
GROOVY_EOF

# 创建管理员用户 + 生成API Token
cat > "$JENKINS_HOME/init.groovy.d/02-create-admin.groovy" << GROOVY_EOF
import jenkins.model.*
import hudson.security.*
import jenkins.security.*

def instance = Jenkins.getInstance()
def hudsonRealm = new HudsonPrivateSecurityRealm(false)

hudsonRealm.createAccount('${ADMIN_USERNAME}', '${ADMIN_PASSWORD}')
println "管理员用户已创建: ${ADMIN_USERNAME}"

instance.setSecurityRealm(hudsonRealm)

def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
strategy.setAllowAnonymousRead(false)
instance.setAuthorizationStrategy(strategy)

// 禁用CSRF的Crumb必须绑定Session（允许API Token直接调用）
def crumbIssuer = instance.getCrumbIssuer()
if (crumbIssuer instanceof hudson.security.csrf.DefaultCrumbIssuer) {
    crumbIssuer.setExcludeClientIPFromCrumb(true)
    println "已配置Crumb: excludeClientIPFromCrumb=true"
}

instance.save()
GROOVY_EOF

# 配置 URL 和时区
cat > "$JENKINS_HOME/init.groovy.d/03-configure-url.groovy" << GROOVY_EOF
import jenkins.model.*

def instance = Jenkins.getInstance()
def jenkinsUrl = "http://${HOST_IP:-\$(hostname -I | awk '{print \$1}')}:${JENKINS_PORT}/"
def location = JenkinsLocationConfiguration.get()
location.setUrl(jenkinsUrl)
location.setAdminAddress("${ADMIN_EMAIL}")
location.save()

System.setProperty('org.apache.commons.jelly.tags.fmt.timeZone', '${TIMEZONE}')
println "Jenkins URL: \${jenkinsUrl}"
println "时区: ${TIMEZONE}"
GROOVY_EOF

# 配置凭证（如果有）
cat > "$JENKINS_HOME/init.groovy.d/04-credentials.groovy" << 'GROOVY_EOF'
import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.impl.*
import com.cloudbees.plugins.credentials.domains.*

def createUsernamePasswordCredential(String id, String description, String username, String password) {
    def jenkins = Jenkins.instance
    def domain = Domain.global()
    def store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
    def credential = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id, description, username, password)
    store.addCredentials(domain, credential)
    println "凭证已创建: ${id}"
}

def createSshCredential(String id, String description, String username, String privateKeyBase64, String passphrase) {
    try {
        def privateKey = new String(privateKeyBase64.decodeBase64())
        def jenkins = Jenkins.instance
        def domain = Domain.global()
        def store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
        def privateKeySource = new com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey)
        def credential = new com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, id, username, privateKeySource, passphrase, description)
        store.addCredentials(domain, credential)
        println "SSH凭证已创建: ${id}"
    } catch (Exception e) {
        println "SSH凭证创建失败: ${id} - ${e.message}"
    }
}

// 自动生成的凭证配置
${CREDENTIALS_GROOVY_SCRIPT}
GROOVY_EOF

echo "  初始化脚本已配置"

# ==================== 7. 创建 systemd 服务 ====================
echo ""
echo "[7/9] 创建系统服务..."

JAVA_BIN=$(which java)

cat > /etc/systemd/system/jenkins.service << SERVICE_EOF
[Unit]
Description=Jenkins Master (AIops CLM Managed)
After=network.target

[Service]
Type=simple
User=root
Environment="JENKINS_HOME=${JENKINS_HOME}"
Environment="JAVA_OPTS=${JAVA_OPTS}"
ExecStart=${JAVA_BIN} ${JAVA_OPTS} -DJENKINS_HOME=${JENKINS_HOME} -Djenkins.install.runSetupWizard=false -jar ${JENKINS_WAR} --httpPort=${JENKINS_PORT}
ExecStop=/bin/kill -SIGTERM \$MAINPID
Restart=on-failure
RestartSec=10
LimitNOFILE=65536
TimeoutStartSec=300
TimeoutStopSec=60
StandardOutput=append:${JENKINS_LOG}/jenkins.log
StandardError=append:${JENKINS_LOG}/jenkins-error.log

[Install]
WantedBy=multi-user.target
SERVICE_EOF

systemctl daemon-reload
systemctl enable jenkins
echo "  服务文件已创建: /etc/systemd/system/jenkins.service"

# ==================== 8. 启动 Jenkins ====================
echo ""
echo "[8/9] 启动 Jenkins..."

systemctl start jenkins

# 等待Jenkins启动
echo "  等待Jenkins启动..."
MAX_WAIT=240
WAITED=0
while [ $WAITED -lt $MAX_WAIT ]; do
    HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${JENKINS_PORT}/login" 2>/dev/null || echo "000")
    if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "403" ]; then
        echo "  Jenkins已启动 (HTTP $HTTP_CODE)"
        break
    fi
    sleep 5
    WAITED=$((WAITED + 5))
    echo "  等待中... (${WAITED}s / ${MAX_WAIT}s)"
done

if [ $WAITED -ge $MAX_WAIT ]; then
    echo "  警告: Jenkins启动超时，请检查日志: $JENKINS_LOG/jenkins.log"
    echo "  最后10行日志:"
    tail -10 "$JENKINS_LOG/jenkins.log" 2>/dev/null || echo "  日志文件不存在"
fi

# 等待init.groovy.d脚本执行完成（等待管理员用户创建成功）
echo "  等待初始化脚本执行..."
INIT_WAITED=0
while [ $INIT_WAITED -lt 90 ]; do
    if grep -q "管理员用户已创建\|管理员用户已存在" "$JENKINS_LOG/jenkins.log" 2>/dev/null; then
        echo "  初始化脚本执行完成"
        break
    fi
    sleep 3
    INIT_WAITED=$((INIT_WAITED + 3))
done

# ==================== 9. 清理与验证 ====================
echo ""
echo "[9/9] 清理与验证..."

# 删除初始密码文件（已通过init.groovy.d创建管理员账号，不再需要）
INITIAL_PWD_FILE="$JENKINS_HOME/secrets/initialAdminPassword"
if [ -f "$INITIAL_PWD_FILE" ]; then
    rm -f "$INITIAL_PWD_FILE"
    echo "  已清理初始密码文件"
fi

# 删除init.groovy.d脚本（避免每次重启都重新执行）
rm -f "$JENKINS_HOME/init.groovy.d/01-skip-wizard.groovy"
rm -f "$JENKINS_HOME/init.groovy.d/02-create-admin.groovy"
rm -f "$JENKINS_HOME/init.groovy.d/03-configure-url.groovy"
rm -f "$JENKINS_HOME/init.groovy.d/04-credentials.groovy"
rm -f "$JENKINS_HOME/init.groovy.d/00-install-plugins.groovy"
echo "  已清理初始化脚本"

# 验证Jenkins是否正常响应
echo "  验证Jenkins服务..."
VERIFY_CODE=$(curl -s -o /dev/null -w '%{http_code}' -u "${ADMIN_USERNAME}:${ADMIN_PASSWORD}" "http://localhost:${JENKINS_PORT}/api/json" 2>/dev/null || echo "000")
if [ "$VERIFY_CODE" = "200" ]; then
    echo "  验证通过: API认证成功 (HTTP $VERIFY_CODE)"
else
    echo "  警告: API认证返回 HTTP $VERIFY_CODE (可能需要手动检查)"
fi

# 检查已安装的插件数量
INSTALLED_PLUGINS=$(curl -s -u "${ADMIN_USERNAME}:${ADMIN_PASSWORD}" "http://localhost:${JENKINS_PORT}/pluginManager/api/json?depth=1" 2>/dev/null | grep -o '"shortName"' | wc -l)
echo "  已安装插件数量: $INSTALLED_PLUGINS"

# 输出登录信息
echo ""
echo "============================================"
echo "  管理员用户: $ADMIN_USERNAME"
echo "  管理员密码: $ADMIN_PASSWORD"
echo "  Jenkins URL: http://${HOST_IP:-$(hostname -I | awk '{print $1}')}:${JENKINS_PORT}"
echo "  Jenkins Home: $JENKINS_HOME"
echo "  服务管理: systemctl {start|stop|restart|status} jenkins"
echo "  日志查看: tail -f $JENKINS_LOG/jenkins.log"
echo "============================================"
echo ""
echo "Jenkins Master 安装完成"
