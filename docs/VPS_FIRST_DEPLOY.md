# Deploy CatCheck lần đầu lên VPS

Điểm xuất phát: VPS Ubuntu đã có **Docker**, **nginx** và **HTTPS cho `catcheck.click`**, code đã `git clone` về
`/opt/prod/catcheck`. Chưa có container, `.env` hay dữ liệu nào. Làm lần lượt từ bước 0 đến bước 9.

Sơ đồ khi chạy xong:

```
Trình duyệt ──HTTPS──▶ nginx (VPS, chứng chỉ của bạn)
                         │ http://127.0.0.1:8080
                         ▼
                      frontend (Caddy, container) ── /api, /oauth2, /login ──▶ backend (Spring, container)
                                                                                 │
                                                                              postgres (container)
```

Ký hiệu: **[local]** = chạy trên máy bạn (thư mục repo), **[VPS]** = chạy trên VPS.
Các lệnh `docker` giả định user của bạn thuộc nhóm `docker` (nếu không, thêm `sudo` phía trước).

---

## 0. Kiểm tra VPS trước khi làm

**[VPS]**

```bash
uname -m                      # phải là x86_64 (backend dùng OpenCV bản x86_64, VPS ARM không chạy được)
docker compose version        # Compose v2
free -h                       # RAM; ≤ 4 GB thì phải có swap (bên dưới)
df -h /                       # nên còn trống ≥ 15 GB (image + cache build + DB)
sudo ss -ltnp | grep ':8080 ' # phải KHÔNG ra gì: cổng 8080 dành cho Caddy
```

- **Cổng 8080 đã bị dùng:** chọn cổng khác, ví dụ 8090. Ở bước 3 thêm `HTTP_PORT=8090` vào `.env`, và ở bước 4
  đổi `proxy_pass` sang `http://127.0.0.1:8090`.
- **Chưa có swap mà RAM ≤ 4 GB:** build backend dễ bị kill (lỗi `exit code 137`). Tạo swap 4 GB:

  ```bash
  sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
  sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```

- **Giới hạn log Docker** (mặc định log container phình mãi đến đầy ổ). Kiểm tra `cat /etc/docker/daemon.json`,
  chưa có thì:

  ```bash
  sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
  { "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
  EOF
  sudo systemctl restart docker
  ```

---

## 1. Lấy code mới nhất

**[VPS]**

```bash
cd /opt/prod/catcheck
git checkout main && git pull
git log -1 --oneline
```

**Không sửa file nào được git theo dõi trên VPS.** Khi bật CI/CD, mỗi lần deploy sẽ `git reset --hard` và xoá
các chỉnh sửa đó. Ba file bí mật ở bước 2–3 đều bị git bỏ qua nên an toàn.

---

## 2. Chép 2 file từ máy bạn lên VPS

Hai file này nằm trong `.gitignore` nên `git pull` không mang lên VPS.

**[local]** (thay `<user>@<VPS_IP>`):

```bash
scp firebase-service-account.json  <user>@<VPS_IP>:/opt/prod/catcheck/
scp frontend/.env.production       <user>@<VPS_IP>:/opt/prod/catcheck/frontend/
```

**[VPS]**

```bash
cd /opt/prod/catcheck
ls -l firebase-service-account.json frontend/.env.production    # cả hai phải là FILE, không phải thư mục
sudo chown 1001:1001 firebase-service-account.json             # 1001 = user chạy backend trong container
sudo chmod 600 firebase-service-account.json
```

- **Phải chép trước lần `docker compose up` đầu tiên.** Nếu file chưa có, Docker tự tạo một *thư mục* tên
  `firebase-service-account.json`, và push bị tắt. Khi đó xem bảng sự cố ở cuối.
- `frontend/.env.production` chứa 5 biến `VITE_FIREBASE_*` cho push trên web. Vite build chúng vào JS lúc build
  image frontend, nên phải có file này **trước** bước build.

---

## 3. Tạo `.env`

**[VPS]** Tạo từ file mẫu và **sinh secret mới cho prod**. Không dùng lại key/pepper của máy local:

```bash
cd /opt/prod/catcheck
cp .env.prod.example .env && chmod 600 .env
sed -i \
  -e "s|^DOMAIN=.*|DOMAIN=catcheck.click|" \
  -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$(openssl rand -hex 32)|" \
  -e "s|^APP_PII_ENCRYPTION_KEYS=.*|APP_PII_ENCRYPTION_KEYS=v1:$(openssl rand -base64 32)|" \
  -e "s|^ACTIVATION_PEPPER=.*|ACTIVATION_PEPPER=$(openssl rand -base64 48)|" \
  -e "s|^RECOVERY_CODE_PEPPER=.*|RECOVERY_CODE_PEPPER=$(openssl rand -base64 48)|" \
  -e "s|^OTP_PEPPER=.*|OTP_PEPPER=$(openssl rand -base64 48)|" \
  -e "s|^CATCHECK_STORAGE_LOCAL_URLSIGNINGKEY=.*|CATCHECK_STORAGE_LOCAL_URLSIGNINGKEY=$(openssl rand -base64 48)|" \
  .env
```

`COMPOSE_FILE=docker-compose.prod.yml`, `DB_NAME` và `DB_USER` đã có sẵn trong file mẫu.

**[local]** Xem các giá trị tài khoản bên ngoài cần chép sang. Lệnh này in secret ra màn hình của bạn, đừng
chạy khi đang chia sẻ màn hình:

```bash
grep -E '^(SMTP_EMAIL|SMTP_APP_PASSWORD|GOOGLE_OAUTH_|CLOUDINARY_|PAYOS_|CATCHECK_AI_(ENABLED|API_KEY|BASE_URL|CHAT_MODEL))' .env
```

**[VPS]** `nano .env` rồi điền:

| Trong `.env` VPS | Lấy từ |
|---|---|
| `MAIL_USERNAME`, `MAIL_PASSWORD` | `SMTP_EMAIL`, `SMTP_APP_PASSWORD` ở local (`MAIL_HOST`/`MAIL_PORT` đã là Gmail 587) |
| `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET` | giống local |
| `CLOUDINARY_*` (3 dòng, bỏ dấu `#`) | giống local |
| `CATCHECK_AI_ENABLED`, `CATCHECK_AI_API_KEY`, `CATCHECK_AI_CHAT_MODEL` (bỏ `#`) | giống local. Nếu local có `CATCHECK_AI_BASE_URL` thì thêm một dòng y hệt |
| `PAYOS_*` (3 dòng, bỏ `#`) | my.payos.vn, xem lưu ý dưới |

Không chép sang `CATCHECK_AI_EMBEDDING_MODEL_DIR`, `FIREBASE_SERVICE_ACCOUNT_PATH`, `FIGMA_TOKEN`, `FRONTEND_URL`,
`GOOGLE_REDIRECT_URI`. Compose đã tự đặt hai biến đầu, ba biến còn lại prod không dùng.

**PayOS:** mỗi database đánh mã đơn PayOS từ 100000. Nếu kênh PayOS này đã từng tạo link thanh toán khi bạn test
ở local, PayOS sẽ từ chối các mã trùng. Khi đó tạo **kênh riêng cho prod** trên my.payos.vn và dùng key của kênh đó.

Kiểm tra `.env`:

```bash
grep -nE '^[A-Z0-9_]+=$' .env        # phải KHÔNG ra dòng nào (biến để trống sẽ đè giá trị mặc định)
docker compose config -q && echo OK  # cú pháp + biến bắt buộc
```

**Sao lưu `.env` và `firebase-service-account.json` ra ngoài VPS**, ví dụ vào trình quản lý mật khẩu. Mất
`APP_PII_ENCRYPTION_KEYS` hoặc các pepper thì không đọc lại được dữ liệu đã mã hoá. Còn `DB_PASSWORD` thì
Postgres chỉ đọc một lần lúc tạo DB, nên **đừng đổi** sau lần chạy đầu.

---

## 4. Cấu hình nginx

nginx giữ HTTPS (chứng chỉ của bạn) và chuyển mọi request vào Caddy ở `127.0.0.1:8080`.

**[VPS]** Tìm file cấu hình site:

```bash
grep -rl catcheck.click /etc/nginx/sites-enabled/ /etc/nginx/conf.d/ 2>/dev/null
```

Sửa block `server { listen 443 ssl ... }` của `catcheck.click`. **Giữ nguyên** các dòng `listen 443 ssl`,
`ssl_certificate`, `ssl_certificate_key`, `include .../options-ssl-nginx.conf` (thường có chú thích
`# managed by Certbot`). Xoá `root`/`index`/`try_files` cũ nếu có, rồi đặt `client_max_body_size` và
`location /` như sau:

```nginx
server {
    listen 443 ssl http2;                         # giữ dòng listen hiện có
    server_name catcheck.click;

    ssl_certificate     /etc/letsencrypt/live/catcheck.click/fullchain.pem;   # giữ đường dẫn hiện có
    ssl_certificate_key /etc/letsencrypt/live/catcheck.click/privkey.pem;

    client_max_body_size 10m;                     # ảnh scan tới 9 MB; mặc định 1 MB sẽ lỗi 413

    location / {
        proxy_pass http://127.0.0.1:8080;         # đổi nếu bạn đặt HTTP_PORT khác
        proxy_set_header Host              $host;
        proxy_set_header X-Forwarded-Proto $scheme;   # BẮT BUỘC: thiếu thì đăng nhập hỏng
        proxy_set_header X-Forwarded-Host  $host;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    }
}
```

Block `listen 80` chỉ cần `return 301 https://$host$request_uri;`, và Certbot thường đã tạo sẵn block này.

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Lúc này mở `https://catcheck.click` sẽ thấy **502 Bad Gateway**. Đó là bình thường vì container chưa chạy.

---

## 5. Build và chạy lần đầu

Build lần đầu mất khoảng 10–20 phút. Nên chạy trong `tmux` để mất SSH không làm đứt build
(`sudo apt install -y tmux`; vào lại phiên bằng `tmux attach -t deploy`).

**[VPS]**

```bash
tmux new -s deploy
cd /opt/prod/catcheck

docker compose build                                         # build backend + frontend

docker compose up -d postgres                                # 1. database
BACKEND_PROFILE=staging docker compose up -d --wait backend  # 2. chỉ lần đầu: Flyway tạo bảng

docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U catcheck -d catcheck \
  < backend/tools/publish-standard-color-chart.sql           # 3. đăng bảng màu pH chuẩn

docker compose up -d --wait                                  # 4. backend chạy lại ở prod + frontend
```

Vì sao có bước 2–3: profile `prod` từ chối khởi động khi bảng màu đang dùng vẫn là bản giữ chỗ (lỗi
`bang mau dang ACTIVE la PLACEHOLDER`). Script ở bước 3 thay nó bằng thang pH chuẩn, nhưng cần bảng DB đã tồn tại.
Vì vậy backend phải chạy một lần ở `staging` (cấu hình giống prod, chỉ bỏ qua kiểm tra này). Các lần sau chỉ cần
`docker compose up -d`. **Không** ghi `BACKEND_PROFILE` vào `.env`.

Thang pH chuẩn là thang chỉ thị chung, chưa đo trên cát CatCheck. Khi có màu đo thật, tạo bảng mới ở
`/admin/ph-color-chart` (DEPLOYMENT.md mục 3 có quy tắc).

---

## 6. Kiểm tra

**[VPS]**

```bash
docker compose ps                                            # postgres, backend, frontend: "running (healthy)"
docker compose exec backend printenv SPRING_PROFILES_ACTIVE  # prod
curl -fsSI http://127.0.0.1:8080/ | head -1                  # HTTP/1.1 200 OK (Caddy)
curl -fsS https://catcheck.click/api/v1/reference/ph-bands | head -c 200; echo   # JSON qua nginx → Caddy → backend
docker compose logs backend | grep -E 'Push FCM|Started |ERROR' | tail -n 20      # mong đợi "Push FCM: BẬT."
```

**Trình duyệt:** mở `https://catcheck.click`, đăng ký bằng email và nhận mã OTP. Nhận được mail nghĩa là SMTP
đã chạy. Đăng nhập xong, tải lại trang mà vẫn còn đăng nhập thì cookie HTTPS và header của nginx đều đúng.

---

## 7. Google, PayOS, push

**Google Cloud Console** → APIs & Services → Credentials → OAuth client → **Authorized redirect URIs**, thêm:

```
https://catcheck.click/login/oauth2/code/google
```

Nếu OAuth consent screen còn ở chế độ **Testing**, chỉ các "test users" đăng nhập Google được. Bấm **Publish app**
để mở cho mọi người.

**PayOS** (my.payos.vn → kênh thanh toán → Webhook): đặt URL

```
https://catcheck.click/api/v1/payments/payos/webhook
```

PayOS gửi request thử khi bạn lưu, và app phải đang chạy (bước 5) để trả 200. Sau đó thử thật:

1. Đặt một đơn nhỏ, chọn PayOS, trả tiền. Đơn phải chuyển sang `PAID`.
2. Đặt đơn thứ hai, bấm **Huỷ** trên trang PayOS. Đơn phải chuyển sang `CANCELLED`.

Đơn PayOS chưa trả sau 5 phút sẽ tự huỷ. Đơn COD không bị huỷ tự động.

**Push:** đăng nhập trên `https://catcheck.click` và bật thông báo khi trình duyệt hỏi.

---

## 8. Tạo admin đầu tiên

Profile `prod` không tạo sẵn tài khoản nào. Đăng ký một tài khoản thường trên web (bước 6), rồi **[VPS]**:

```bash
docker compose exec postgres psql -U catcheck -d catcheck -c \
  "INSERT INTO user_role (user_id, role) SELECT id, 'ADMIN_SUPER' FROM app_user WHERE email = 'email-cua-ban@example.com';"
```

Phải ra `INSERT 0 1`. Đăng xuất rồi đăng nhập lại để nhận quyền. Trang admin bắt buộc bật **TOTP** (xác thực 2
lớp) trước khi dùng. Quyền cho nhân viên khác cấp trong trang admin.

---

## 9. Backup tự động và dọn dẹp

**[VPS]** `mkdir -p /opt/prod/backups`, rồi `crontab -e`, thêm (trong crontab, `%` phải viết `\%`):

```cron
# 03:00 mỗi ngày: dump DB, giữ 14 ngày
0 3 * * * cd /opt/prod/catcheck && docker compose exec -T postgres pg_dump -U catcheck -Fc catcheck > /opt/prod/backups/db-$(date +\%F).dump && find /opt/prod/backups -name '*.dump' -mtime +14 -delete
# 04:00 Chủ nhật: dọn cache build cũ (VPS tự build nên cache tăng sau mỗi lần deploy)
0 4 * * 0 docker builder prune -f --filter until=168h && docker image prune -f
```

Backup nằm trên chính VPS chưa phải backup thật. Định kỳ chép `/opt/prod/backups` ra nơi khác, ví dụ dùng
`rclone` lên Google Drive/S3, và mã hoá vì dữ liệu có thông tin cá nhân. Ảnh nằm trên Cloudinary nên không cần
backup volume ảnh.

---

## Cập nhật phiên bản mới (thủ công)

**[VPS]**

```bash
cd /opt/prod/catcheck && git pull
docker compose exec -T postgres pg_dump -U catcheck -Fc catcheck > /opt/prod/backups/pre-update-$(date +%F-%H%M).dump
docker compose up -d --build        # Flyway tự chạy migration mới khi backend khởi động
docker image prune -f
```

Web gián đoạn khoảng 1 phút trong lúc backend khởi động lại. Muốn deploy tự động khi push lên `main`, xem
[VPS_SETUP.md](VPS_SETUP.md) phần C (GitHub Actions).

---

## Sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách sửa |
|---|---|
| `docker compose` báo `set DOMAIN in .env` / `set DB_PASSWORD in .env` | Thiếu hoặc để trống biến đó trong `.env`. |
| Build bị kill, `exit code 137` | Hết RAM. Tạo swap (bước 0) rồi build lại. |
| nginx trả **502** | Container chưa chạy (`docker compose ps`), hoặc `proxy_pass` khác cổng `HTTP_PORT`. |
| Upload ảnh scan lỗi **413** | Thiếu `client_max_body_size 10m;` trong nginx. |
| Đăng nhập xong bị đăng xuất ngay, hoặc Google chuyển về `http://` | nginx thiếu `proxy_set_header X-Forwarded-Proto $scheme;`. |
| Google báo `redirect_uri_mismatch` | Chưa thêm redirect URI ở bước 7. |
| Backend dừng với `bang mau dang ACTIVE la PLACEHOLDER` | Chưa làm bước 2–3 của mục 5. |
| Backend dừng với `IllegalStateException` về pepper/key | Secret trống, quá ngắn hoặc sai định dạng. Chạy lại lệnh `sed` ở bước 3. **Chỉ** làm vậy khi DB chưa có dữ liệu thật. |
| Backend không kết nối được DB sau khi đổi `DB_PASSWORD` | Postgres chỉ đọc mật khẩu lúc tạo DB lần đầu. Trả lại mật khẩu cũ, hoặc đổi bằng `ALTER ROLE catcheck PASSWORD '...'` trong psql. |
| Không nhận được mail OTP | Sai `MAIL_USERNAME`/`MAIL_PASSWORD` (Gmail cần **App password**). Xem `docker compose logs backend \| grep -i mail`. |
| Log `Push FCM: TẮT` | `firebase-service-account.json` thiếu, sai quyền, hoặc là thư mục. Sửa: `docker compose stop backend`, `sudo rm -rf firebase-service-account.json`, chép lại (bước 2), `docker compose up -d backend`. |
| Shop không có lựa chọn PayOS | Thiếu một trong ba `PAYOS_*` (còn dấu `#` hoặc trống). Sửa xong chạy `docker compose up -d backend`. |
| Đặt đơn PayOS báo lỗi thanh toán | Kênh PayOS đã dùng mã đơn khi test ở local. Dùng kênh riêng cho prod (bước 3). |

Xem log: `docker compose logs -f backend` (hoặc `frontend`, `postgres`). Tài liệu đầy đủ:
[DEPLOYMENT.md](DEPLOYMENT.md).
