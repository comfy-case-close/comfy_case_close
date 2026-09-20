#!/usr/bin/env bash
# ============================================================================
# extract-service.sh <service> <dich>
#
# Tao mot ban sao cua MOT service voi pom.xml da doi sang dang "repo rieng":
#   - parent doi tu fnbx-parent  ->  spring-boot-starter-parent
#   - version thu vien doi tu ke thua  ->  ghim qua fnbx-bom
#
# Muc dich: chung minh service do KHONG phu thuoc ngam vao service khac.
# Neu build offline (-o) thanh cong thi ngay tach repo chi ton ~0,5 ngay.
# ADR-0003 muc 12.10 va 12.11.
#
#   ./tools/extract-service.sh cashclose /tmp/solo
#   cd /tmp/solo && mvn -o clean verify
# ============================================================================
set -euo pipefail

SVC="${1:?dung: extract-service.sh <service> <thu-muc-dich>}"
DEST="${2:?dung: extract-service.sh <service> <thu-muc-dich>}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SRC="$ROOT/services/${SVC}-service"
[[ "$SVC" == "gateway" ]] && SRC="$ROOT/services/api-gateway"
[[ -d "$SRC" ]] || { echo "khong tim thay $SRC" >&2; exit 1; }

# Doc version tu pom goc
REVISION="$(sed -n 's/.*<revision>\(.*\)<\/revision>.*/\1/p' "$ROOT/pom.xml" | head -1)"
BOOT_VERSION="$(sed -n '/<artifactId>spring-boot-starter-parent<\/artifactId>/,/<\/parent>/s/.*<version>\(.*\)<\/version>.*/\1/p' "$ROOT/pom.xml" | head -1)"

rm -rf "$DEST"; mkdir -p "$DEST"
cp -R "$SRC"/. "$DEST"/
rm -rf "$DEST/target"

ORIGINAL_POM="$DEST/pom.xml"
ARTIFACT="$(sed -n '0,/<artifactId>/s/.*<artifactId>\(.*\)<\/artifactId>.*/\1/p' "$ORIGINAL_POM" | sed -n 2p)"
[[ -n "${ARTIFACT:-}" ]] || ARTIFACT="${SVC}-service"

# Lay nguyen khoi <dependencies> cua service, bo qua phan <parent>
python3 - "$ORIGINAL_POM" "$DEST/.deps.xml" <<'PY'
import re, sys
src, out = sys.argv[1], sys.argv[2]
text = open(src, encoding='utf-8').read()
m = re.search(r'<dependencies>.*?</dependencies>', text, re.S)
open(out, 'w', encoding='utf-8').write(m.group(0) if m else '<dependencies/>')
PY
DEPS="$(cat "$DEST/.deps.xml")"; rm -f "$DEST/.deps.xml"

cat > "$ORIGINAL_POM" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<!-- SINH TU DONG boi tools/extract-service.sh - dung commit file nay.
     Day chinh xac la pom.xml ma service se co sau khi tach repo. -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>${BOOT_VERSION}</version>
    <relativePath/>
  </parent>

  <groupId>com.fnbx</groupId>
  <artifactId>${ARTIFACT}</artifactId>
  <version>${REVISION}</version>

  <properties>
    <java.version>21</java.version>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>com.fnbx</groupId>
        <artifactId>fnbx-bom</artifactId>
        <version>${REVISION}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

${DEPS}

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
POM

echo "OK  $SVC  ->  $DEST"
echo "    kiem tra:  cd $DEST && mvn -o clean verify"
