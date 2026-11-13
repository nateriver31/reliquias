#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME=/workspace/.tools/jdk-25.0.5+8
export PATH="$JAVA_HOME/bin:$PATH"
if [[ ! -x "$JAVA_HOME/bin/javac" || ! -f /workspace/.tools/java-cacerts ]]; then
  echo 'Execute bash scripts/bootstrap-cloud.sh primeiro.' >&2
  exit 1
fi
settings_dir=$(mktemp -d /tmp/reliquias-maven.XXXXXX)
trap 'rm -rf "$settings_dir"' EXIT
python3 - "$settings_dir/settings.xml" <<'PY'
import os, sys
from urllib.parse import urlsplit
from xml.etree.ElementTree import Element, SubElement, ElementTree
root = Element('settings', xmlns='http://maven.apache.org/SETTINGS/1.2.0')
SubElement(root, 'localRepository').text = '/workspace/.cache/maven'
proxies = SubElement(root, 'proxies')
for protocol in ('http', 'https'):
    value = os.environ.get(protocol.upper() + '_PROXY') or os.environ.get(protocol + '_proxy')
    if not value:
        continue
    url = urlsplit(value)
    proxy = SubElement(proxies, 'proxy')
    for name, text in [('id', protocol), ('active', 'true'), ('protocol', protocol), ('host', url.hostname), ('port', str(url.port or 80))]:
        SubElement(proxy, name).text = text
    SubElement(proxy, 'nonProxyHosts').text = 'localhost|127.*|[::1]'
    if url.username: SubElement(proxy, 'username').text = url.username
    if url.password: SubElement(proxy, 'password').text = url.password
ElementTree(root).write(sys.argv[1], encoding='utf-8', xml_declaration=True)
os.chmod(sys.argv[1], 0o600)
PY
export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=/workspace/.tools/java-cacerts -Djavax.net.ssl.trustStorePassword=changeit"
/workspace/.tools/apache-maven-3.9.9/bin/mvn --settings "$settings_dir/settings.xml" "$@"
