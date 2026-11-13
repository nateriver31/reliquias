#!/usr/bin/env bash
set -euo pipefail
tools=/workspace/.tools
mkdir -p "$tools/downloads"
cd "$tools/downloads"
if [[ ! -x "$tools/jdk-25.0.5+8/bin/javac" ]]; then
  curl -fsSL --retry 2 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz' -o temurin-25.0.5+8.tar.gz
  curl -fsSL --retry 2 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_linux_hotspot_25.0.5_8.tar.gz.sha256.txt' -o temurin-25.0.5+8.sha256
  python3 - <<'PY'
import hashlib
from pathlib import Path
p = Path('temurin-25.0.5+8.tar.gz')
if hashlib.file_digest(p.open('rb'), 'sha256').hexdigest() != Path('temurin-25.0.5+8.sha256').read_text().split()[0]:
    raise SystemExit('JDK checksum mismatch')
PY
  tar -xzf temurin-25.0.5+8.tar.gz -C "$tools"
fi
if [[ ! -x "$tools/apache-maven-3.9.9/bin/mvn" ]]; then
  curl -fsSL --retry 2 https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz -o apache-maven-3.9.9-bin.tar.gz
  curl -fsSL --retry 2 https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.tar.gz.sha512 -o apache-maven-3.9.9-bin.tar.gz.sha512
  python3 - <<'PY'
import hashlib
from pathlib import Path
p = Path('apache-maven-3.9.9-bin.tar.gz')
if hashlib.file_digest(p.open('rb'), 'sha512').hexdigest() != Path('apache-maven-3.9.9-bin.tar.gz.sha512').read_text().split()[0]:
    raise SystemExit('Maven checksum mismatch')
PY
  tar -xzf apache-maven-3.9.9-bin.tar.gz -C "$tools"
fi
# Public trust anchors only. Preserve TLS validation when using the managed HTTPS proxy.
cp "$tools/jdk-25.0.5+8/lib/security/cacerts" "$tools/java-cacerts"
if [[ -n "${CODEX_PROXY_CERT:-}" && -f "$CODEX_PROXY_CERT" ]]; then
  "$tools/jdk-25.0.5+8/bin/keytool" -importcert -noprompt -alias codex-cloud-proxy \
    -file "$CODEX_PROXY_CERT" -keystore "$tools/java-cacerts" -storepass changeit
fi
"$tools/jdk-25.0.5+8/bin/javac" -version
JAVA_HOME="$tools/jdk-25.0.5+8" "$tools/apache-maven-3.9.9/bin/mvn" -version
