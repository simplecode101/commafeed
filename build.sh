#!/usr/bin/env bash
#
# 构建 CommaFeed 镜像并推送到 Harbor
#
# 用法:
#   ./build.sh                # maven 构建 + 打镜像 + 推送
#   ./build.sh --skip-build   # 复用已有 maven 产物，只打镜像 + 推送
#   ./build.sh --skip-push    # 只构建镜像，不推送
#
# 可通过环境变量覆盖:
#   REGISTRY=192.168.1.138       Harbor 地址
#   HARBOR_PROJECT=library       Harbor 项目名
#   IMAGE_NAME=commafeed         镜像名
#   DB_PROFILE=postgresql        数据库 profile (postgresql/mysql/mariadb/h2)
#   HTTP_PROXY_URL=http://192.168.1.86:7890   构建时使用的代理，留空表示不使用
#   JDK_HOME=/path/to/jdk21      构建用 JDK，留空则使用 JAVA_HOME / PATH 中的 java
#
# 注意:
#   - Maven 依赖仓库/代理由 ~/.m2/settings.xml 控制，本脚本不生成该文件
#   - maven 只产出 zip，脚本会自动解压出 Dockerfile 需要的目录
#
set -euo pipefail

REGISTRY="${REGISTRY:-192.168.1.138}"
HARBOR_PROJECT="${HARBOR_PROJECT:-library}"
IMAGE_NAME="${IMAGE_NAME:-commafeed}"
DB_PROFILE="${DB_PROFILE:-postgresql}"
HTTP_PROXY_URL="${HTTP_PROXY_URL:-http://192.168.1.86:7890}"

cd "$(dirname "$0")"

VERSION="$(sed -n 's/.*<version>\(.*\)<\/version>.*/\1/p' pom.xml | head -1)"
APP_DIR="commafeed-${VERSION}-${DB_PROFILE}"
IMAGE="${REGISTRY}/${HARBOR_PROJECT}/${IMAGE_NAME}:${VERSION}"
ZIP="commafeed-server/target/${APP_DIR}-jvm.zip"

SKIP_BUILD=0
SKIP_PUSH=0
for arg in "$@"; do
	case "$arg" in
		--skip-build) SKIP_BUILD=1 ;;
		--skip-push) SKIP_PUSH=1 ;;
		-h | --help)
			sed -n '2,20p' "$0"
			exit 0
			;;
		*)
			echo "未知参数: $arg" >&2
			exit 1
			;;
	esac
done

echo "== 版本: ${VERSION}  数据库: ${DB_PROFILE}  镜像: ${IMAGE}"

# ===== 环境准备 =====
# JDK 自动切换: 优先 JDK_HOME, 其次已有的 JAVA_HOME, 否则用 PATH 中的 java
if [ -n "${JDK_HOME:-}" ]; then
	export JAVA_HOME="${JDK_HOME}"
fi
if [ -n "${JAVA_HOME:-}" ]; then
	export PATH="${JAVA_HOME}/bin:${PATH}"
fi

REQUIRED_JAVA="$(sed -n 's/.*<maven.compiler.release>\(.*\)<\/maven.compiler.release>.*/\1/p' pom.xml | head -1)"
CURRENT_JAVA="$(java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
echo "==> JDK ${CURRENT_JAVA} (pom 要求 ${REQUIRED_JAVA:-未指定})  JAVA_HOME=${JAVA_HOME:-未设置}"
if [ -n "${REQUIRED_JAVA}" ] && [ -n "${CURRENT_JAVA}" ] && [ "${CURRENT_JAVA}" -lt "${REQUIRED_JAVA}" ]; then
	echo "错误: 需要 JDK ${REQUIRED_JAVA} 及以上, 当前为 ${CURRENT_JAVA}. 用 JDK_HOME 指定合适的 JDK" >&2
	exit 1
fi

# 代理: 影响 maven 插件与前端下载; Maven 依赖仓库另见 ~/.m2/settings.xml
if [ -n "${HTTP_PROXY_URL}" ]; then
	echo "==> 代理: ${HTTP_PROXY_URL}"
	export https_proxy="${HTTP_PROXY_URL}"
	export http_proxy="${HTTP_PROXY_URL}"
else
	echo "==> 不使用代理"
	unset https_proxy http_proxy 2>/dev/null || true
fi

if [ "${SKIP_BUILD}" -eq 0 ]; then
	echo "==> maven 构建 (profile: ${DB_PROFILE})"
	./mvnw clean package -DskipTests -P"${DB_PROFILE}"
fi

# maven 的 assembly 只产出 zip, 这里解压出 Dockerfile 需要的目录
if [ ! -d "commafeed-server/target/${APP_DIR}" ]; then
	if [ ! -f "${ZIP}" ]; then
		echo "构建产物不存在: ${ZIP}" >&2
		exit 1
	fi
	echo "==> 解压 ${ZIP}"
	if command -v unzip >/dev/null 2>&1; then
		unzip -q -o "${ZIP}" -d commafeed-server/target/
	else
		(cd commafeed-server/target && jar xf "$(basename "${ZIP}")")
	fi
fi

echo "==> 构建镜像"
docker build \
	-f docker/Dockerfile \
	--build-arg APP_DIR="${APP_DIR}" \
	-t "${IMAGE}" \
	commafeed-server/target/

if [ "${SKIP_PUSH}" -eq 0 ]; then
	echo "==> 推送到 ${REGISTRY}"
	if ! docker push "${IMAGE}"; then
		echo "推送失败, 请先执行: docker login ${REGISTRY}" >&2
		exit 1
	fi
	echo "== 完成: ${IMAGE}"
fi
