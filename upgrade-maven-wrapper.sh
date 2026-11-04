#!/usr/bin/env bash

set -euo pipefail

WRAPPER_VERSION="3.3.4"
MAVEN_VERSION="3.9.16"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WRAPPER_DIR="${PROJECT_DIR}/.mvn/wrapper"
PROPERTIES_FILE="${WRAPPER_DIR}/maven-wrapper.properties"

echo "Project directory: ${PROJECT_DIR}"
echo "Maven Wrapper version: ${WRAPPER_VERSION}"
echo "Maven version: ${MAVEN_VERSION}"

cd "${PROJECT_DIR}"

mkdir -p "${WRAPPER_DIR}"

# 先补全旧 Maven Wrapper 的配置，使当前旧版 mvnw 可以正常启动。
cat > "${PROPERTIES_FILE}" <<EOF
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.1.0/maven-wrapper-3.1.0.jar
EOF

chmod +x mvnw

echo "Generating Maven Wrapper ${WRAPPER_VERSION}..."

./mvnw \
  --batch-mode \
  --no-transfer-progress \
  "org.apache.maven.plugins:maven-wrapper-plugin:${WRAPPER_VERSION}:wrapper" \
  "-Dmaven=${MAVEN_VERSION}" \
  "-Dtype=only-script"

# only-script 模式不需要 Wrapper JAR 和 Downloader 源文件。
rm -f "${WRAPPER_DIR}/maven-wrapper.jar"
rm -f "${WRAPPER_DIR}/MavenWrapperDownloader.java"
rm -f "${WRAPPER_DIR}/MavenWrapperDownloader.class"

# 明确写入最终配置，避免旧配置残留。
cat > "${PROPERTIES_FILE}" <<EOF
wrapperVersion=${WRAPPER_VERSION}
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip
EOF

chmod +x mvnw

# 保证 Git 仓库记录 Linux 执行权限。
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git update-index --chmod=+x mvnw || true
fi

echo
echo "Maven Wrapper generated successfully."
echo

./mvnw --version

echo
echo "Generated files:"
echo "  mvnw"
echo "  mvnw.cmd"
echo "  .mvn/wrapper/maven-wrapper.properties"