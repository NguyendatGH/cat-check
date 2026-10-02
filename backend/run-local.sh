#!/usr/bin/env bash
# Chạy backend ở profile `local`.
#
# Vì sao cần script này: `java` trên PATH của máy dev là JDK 21, còn project build bằng
# JDK 25 (`pom.xml` <java.version>25</java.version>). Gọi thẳng `./mvnw spring-boot:run`
# sẽ biên dịch được (Maven dùng toolchain riêng) nhưng lúc FORK tiến trình app thì dùng
# `java` trên PATH ⇒ chết ngay với:
#   UnsupportedClassVersionError: ... class file version 69.0, this version of the Java
#   Runtime only recognizes class file versions up to 65.0
# (69.0 = JDK 25, 65.0 = JDK 21).
#
# Script cũng nạp .env ở gốc repo — 4 secret bắt buộc (ACTIVATION_PEPPER,
# RECOVERY_CODE_PEPPER, OTP_PEPPER, APP_PII_ENCRYPTION_KEYS) nằm ở đó và KHÔNG được
# Spring tự đọc khi chạy native bằng mvnw.
set -euo pipefail

JDK_HOME="${CATCHECK_JDK_HOME:-$HOME/.local/jdk/jdk-25.0.4.1+1}"
if [[ ! -x "$JDK_HOME/bin/java" ]]; then
  echo "Khong tim thay JDK 25 tai: $JDK_HOME" >&2
  echo "Dat bien CATCHECK_JDK_HOME tro toi thu muc JDK 25 roi chay lai." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
else
  echo "Canh bao: khong thay $ENV_FILE — thieu secret thi app se khong khoi dong duoc." >&2
fi

export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

echo "JDK : $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "Profile: $SPRING_PROFILES_ACTIVE"
exec "$SCRIPT_DIR/mvnw" -q spring-boot:run "$@"
