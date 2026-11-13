#!/usr/bin/env bash
# Useful independently of the Paper repository; does not replace the full Maven/MockBukkit suite.
set -euo pipefail
cd "$(dirname "$0")/.."
java_home=${JAVA_HOME:-/workspace/.tools/jdk-25.0.5+8}
cache=/workspace/.tools/downloads
jar=junit-platform-console-standalone-6.1.3.jar
mkdir -p "$cache"
rm -rf target/core-test-classes
mkdir -p target/core-test-classes
if [[ ! -f "$cache/$jar" || ! -f "$cache/$jar.sha256" ]]; then
  curl -fsSL --retry 2 "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/6.1.3/$jar" -o "$cache/$jar"
  curl -fsSL --retry 2 "https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/6.1.3/$jar.sha256" -o "$cache/$jar.sha256"
fi
python3 - "$cache/$jar" <<'PY'
import hashlib, sys
from pathlib import Path
p=Path(sys.argv[1])
if hashlib.file_digest(p.open('rb'), 'sha256').hexdigest()!=Path(str(p)+'.sha256').read_text().split()[0]:
    raise SystemExit('JUnit checksum mismatch')
PY
mapfile -t sources < <(rg --files src/main/java/io/github/nateriver31/reliquias/core src/test/java/io/github/nateriver31/reliquias/core -g '*.java')
"$java_home/bin/javac" --release 25 -encoding UTF-8 -cp "$cache/$jar" -d target/core-test-classes "${sources[@]}"
"$java_home/bin/java" -jar "$cache/$jar" execute --class-path target/core-test-classes \
  --scan-class-path --include-package io.github.nateriver31.reliquias.core --fail-if-no-tests --details=summary
