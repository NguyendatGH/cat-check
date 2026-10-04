#!/usr/bin/env bash
# scan-frontend-bundle.sh — quét bundle FE production đã build tìm secret lỡ
# lọt vào (dùng bởi .github/workflows/secret-scan.yml, step "quét bundle FE").
#
# Không có tool sẵn phù hợp cho việc quét cụ thể bundle JS đã build (gitleaks
# tối ưu cho quét diff/lịch sử git, không phải file build output) — script
# grep đơn giản theo đúng yêu cầu spec/parts/p18-devops.md:
#   - Khối PEM ("-----BEGIN ...")
#   - "private_key" (service-account JSON lỡ bị bundle vào)
#   - client_secret
#   - Chuỗi base64 dài (>=40 ký tự) đứng cạnh từ khoá secret|pepper|key
#
# Usage: scan-frontend-bundle.sh <thư mục dist>

set -euo pipefail

DIST_DIR="${1:?Cách dùng: scan-frontend-bundle.sh <thư mục dist>}"

if [ ! -d "$DIST_DIR" ]; then
  echo "::error::Không tìm thấy thư mục $DIST_DIR — chạy 'npm run build' trước khi quét."
  exit 1
fi

found=0

echo "== 1/4: khối PEM / private key =="
if grep -rlIE -- '-----BEGIN (RSA |EC |OPENSSH |)PRIVATE KEY-----' "$DIST_DIR"; then
  found=1
fi

echo "== 2/4: \"private_key\" (service account JSON) =="
if grep -rlIE '"private_key"[[:space:]]*:' "$DIST_DIR"; then
  found=1
fi

echo "== 3/4: client_secret =="
if grep -rlIiE 'client_secret' "$DIST_DIR"; then
  found=1
fi

echo "== 4/4: chuỗi base64 dài (>=40 ky tu) canh tu khoa secret|pepper|key =="
if grep -rlIiE '(secret|pepper|key)[^=:]{0,20}[:=][[:space:]]*["'"'"']?[A-Za-z0-9+/_-]{40,}={0,2}' "$DIST_DIR"; then
  found=1
fi

if [ "$found" -eq 1 ]; then
  echo "::error::Tìm thấy nội dung nghi là secret trong bundle FE ($DIST_DIR) — xem danh sách file ở trên."
  exit 1
fi

echo "OK — không tìm thấy secret nghi vấn trong $DIST_DIR"
