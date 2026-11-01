#!/bin/bash
# Maven wrapper for Git Bash (MinGW) —— 绕过官方 mvn 脚本在 MinGW 下不转 Windows 路径的问题
# 用法：./mvnw.sh -version  /  ./mvnw.sh compile  /  ./mvnw.sh test
set -e

# 修复 PATH（PortableGit 的 coreutils 目录，防止 grep/sed/dirname 找不到）
export PATH="/c/Users/西西/.workbuddy/binaries/PortableGit/versions/1.2.0/usr/bin:/c/Users/西西/.workbuddy/binaries/PortableGit/versions/1.2.0/bin:$PATH"

JDK="/d/work/jdk-21.0.12.1+1"
MVN_HOME="D:/work/Maven/apache-maven-3.9.9"
CLASSWORLDS="$MVN_HOME/boot/plexus-classworlds-2.8.0.jar"

# 当前目录转 Windows 混合路径 D:/xxx/yyy（Java 需要的格式）
if command -v cygpath >/dev/null 2>&1; then
  BASEDIR="$(cygpath -m "$PWD")"
else
  BASEDIR="$(echo "$PWD" | sed 's#^/\([a-zA-Z]\)/#\1:/#')"
fi

exec "$JDK/bin/java.exe" \
  -classpath "$CLASSWORLDS" \
  "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" \
  "-Dmaven.home=$MVN_HOME" \
  "-Dmaven.multiModuleProjectDirectory=$BASEDIR" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
