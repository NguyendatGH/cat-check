#!/usr/bin/env bash
# Chạy backend ở profile `local`.
#
# Backend hiện build bằng Java 21. Script này đảm bảo tiến trình Maven và tiến trình
# Spring Boot dùng cùng một JDK, tránh lỗi UnsupportedClassVersionError khi fork app.
#
# Script cũng nạp .env ở gốc repo — 4 secret bắt buộc (ACTIVATION_PEPPER,
# RECOVERY_CODE_PEPPER, OTP_PEPPER, APP_PII_ENCRYPTION_KEYS) nằm ở đó và KHÔNG được
# Spring tự đọc khi chạy native bằng mvnw.
set -euo pipefail

JDK_HOME="${CATCHECK_JDK_HOME:-${JAVA_HOME:-}}"
if [[ -z "$JDK_HOME" ]]; then
  JAVA_BIN="$(command -v java || true)"
  if [[ -n "$JAVA_BIN" ]]; then
    JDK_HOME="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN")")")"
  fi
fi
if [[ ! -x "$JDK_HOME/bin/java" ]]; then
  echo "Khong tim thay JDK 21. Dat CATCHECK_JDK_HOME hoac JAVA_HOME tro toi JDK 21." >&2
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
# Clean target trước khi chạy để loại class artifact được build bằng JDK cũ (đặc biệt
# sau lần chuyển Java 25 -> Java 21); chỉ xóa build output, không đụng source/database.
exec "$SCRIPT_DIR/mvnw" -q clean spring-boot:run \
  "-Dspring-boot.run.profiles=$SPRING_PROFILES_ACTIVE" "$@"
