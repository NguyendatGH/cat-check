# Setup VPS + CI/CD cho CatCheck

Hướng dẫn từng bước cho VPS **Ubuntu 24.04 LTS (x86_64)**: cài hạ tầng → đưa dự án vào `/opt/prod` →
bật GitHub Actions để tự test và tự deploy. Các bước chạy app lần đầu đã có trong
[DEPLOYMENT.md](DEPLOYMENT.md); file này chỉ dẫn tới đó khi cần.

Cấu trúc thư mục trên VPS sau khi xong:

```
/opt/prod/
├── catcheck/     # repo (git clone), chứa .env thật — KHÔNG commit
└── backups/      # bản dump DB tự động (cron + trước mỗi lần deploy)
```

Quy ước trong file: lệnh có `sudo` chạy bằng user `deploy`; `<DOMAIN>`, `<VPS_IP>` thay bằng giá trị thật.

---

## 0. Chuẩn bị

| Cần | Ghi chú |
|---|---|
| VPS Ubuntu 24.04 x86_64 | **Không dùng ARM**: image backend chỉ có OpenCV cho x86_64. |
| RAM | VPS tự build image (Maven + npm), nên nên có **4 GB**; 2 GB thì bắt buộc thêm swap (bước A7). Đây là ước lượng, chưa đo. |
| Ổ đĩa | **≥ 40 GB**. Image backend ~1,4 GB, cache build tăng dần theo mỗi lần deploy (máy dev từng lên 13 GB). |
| Domain | Bản ghi **A** (và AAAA nếu có IPv6) trỏ về `<VPS_IP>`. Cần trước bước B5. |
| Tài khoản | SMTP (gửi mail), Google OAuth client. Danh sách biến đủ ở `.env.prod.example`. |

Nếu nhà cung cấp VPS có **snapshot**, chụp một bản sau khi xong phần A.

---

## A. Hạ tầng (làm một lần)

### A1. Cập nhật hệ thống, đặt múi giờ

Đăng nhập bằng `root` lần đầu:

```bash
apt update && apt full-upgrade -y
timedatectl set-timezone Asia/Ho_Chi_Minh
reboot   # nếu bản cập nhật có kernel mới
```

### A2. Tạo user `deploy` (không chạy mọi thứ bằng root)

```bash
adduser deploy
usermod -aG sudo deploy
# chép SSH key của root (key bạn đang dùng để đăng nhập) sang user mới
rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy
```

Mở **cửa sổ terminal mới**, thử `ssh deploy@<VPS_IP>` thành công rồi mới làm tiếp.

### A3. Khoá SSH: chỉ cho đăng nhập bằng key, cấm root

```bash
sudo tee /etc/ssh/sshd_config.d/99-hardening.conf > /dev/null <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF
sudo sshd -t && sudo systemctl reload ssh
```

Giữ nguyên phiên SSH hiện tại, mở phiên mới để thử lại. Nếu bị khoá ngoài, vào bằng console web của nhà cung cấp
và xoá file `99-hardening.conf`.

### A4. Firewall (ufw)

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp     # HTTP/3
sudo ufw enable
sudo ufw status
```

> **Lưu ý Docker vượt ufw:** cổng mà Docker publish (`ports:` trong compose) được mở trực tiếp qua iptables, ufw
> **không chặn được**. `docker-compose.prod.yml` chỉ publish 80/443 (và adminer ở `127.0.0.1`), nên an toàn.
> **Đừng** thêm `ports:` cho `postgres` hay `backend`.

### A5. Chống dò mật khẩu SSH (fail2ban)

```bash
sudo apt install -y fail2ban
sudo systemctl enable --now fail2ban
sudo fail2ban-client status sshd
```

### A6. Tự cài bản vá bảo mật

```bash
sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades   # chọn Yes
```

### A7. Swap (bắt buộc nếu RAM ≤ 4 GB)

Build backend (Maven) + JVM + build frontend cùng lúc dễ hết RAM.

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf && sudo sysctl --system
free -h
```

### A8. Docker Engine + Compose (từ repo chính thức của Docker)

Không dùng bản `snap` hay `docker.io` của Ubuntu (thường cũ).

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker deploy   # đăng xuất rồi đăng nhập lại để có hiệu lực
```

Kiểm tra (sau khi đăng nhập lại):

```bash
docker run --rm hello-world
docker compose version
```

> User trong nhóm `docker` có quyền tương đương root. Chỉ cho `deploy` vào nhóm này.

### A9. Giới hạn log của Docker

Mặc định Docker **không xoay vòng log** — log container sẽ lấp đầy ổ đĩa theo thời gian.

```bash
sudo tee /etc/docker/daemon.json > /dev/null <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
sudo systemctl restart docker
```

### A10. Thư mục dự án

```bash
sudo mkdir -p /opt/prod/backups
sudo chown -R deploy:deploy /opt/prod
```

---

## B. Đưa dự án vào `/opt/prod`

### B0. Trước tiên: khôi phục GitHub Actions trên `main`

Code deploy đã nằm trên `main` (PR #30 và #31). Nhưng commit `1e760a9` đã chuyển `.github/` sang `archived/`, mà
`archived/` lại bị `.gitignore` bỏ qua, nên trên GitHub hiện **không còn workflow nào** (và không còn
`dependabot.yml`). Lấy lại các workflow từ `main` cũ (`7afd044`) trên một nhánh mới:

```bash
git switch -c chore/restore-ci main
git checkout 7afd044 -- .github/workflows .github/scripts
git commit -m "chore(ci): restore GitHub Actions workflows"
git push -u origin chore/restore-ci        # rồi mở PR vào main
```

Lệnh trên **cố ý không** lấy lại `.github/dependabot.yml`, để bot không mở PR nâng version hằng tuần nữa. Muốn
bật lại thì thêm `.github/dependabot.yml` vào lệnh `git checkout`. Sửa các workflow theo phần C trong cùng nhánh
này trước khi merge.

### B1. Deploy key để VPS đọc repo

Trên VPS (user `deploy`):

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github_deploy -N "" -C "catcheck-vps"
cat ~/.ssh/github_deploy.pub
cat >> ~/.ssh/config <<'EOF'
Host github.com
  IdentityFile ~/.ssh/github_deploy
  IdentitiesOnly yes
EOF
chmod 600 ~/.ssh/config
```

Trên GitHub: repo `NguyendatGH/cat-check` → **Settings → Deploy keys → Add deploy key** → dán public key,
**không** tick "Allow write access" (chỉ đọc).

### B2. Clone

```bash
ssh -T git@github.com            # gõ "yes" lần đầu; thấy "successfully authenticated" là được
git clone git@github.com:NguyendatGH/cat-check.git /opt/prod/catcheck
cd /opt/prod/catcheck
git checkout main
```

### B3. File `.env`

```bash
cd /opt/prod/catcheck
cp .env.prod.example .env
chmod 600 .env
nano .env
```

Điền mọi giá trị trống. Lệnh sinh từng secret có ngay trong file (`openssl rand ...`). Hai việc quan trọng:

- **Sao lưu `.env` ra ngoài VPS** (trình quản lý mật khẩu). Mất `APP_PII_ENCRYPTION_KEYS` hoặc các pepper thì
  không đọc lại được dữ liệu đã mã hoá.
- **Không sửa file nào khác trong repo trên VPS.** Lần deploy tự động sẽ `git reset --hard` về `main`;
  `.env` an toàn vì nó bị git bỏ qua.

Muốn bán hàng có thanh toán online: bỏ comment và điền `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`
(lấy ở my.payos.vn). Không điền thì shop chỉ có COD. Cách đặt webhook và thử đơn: xem `docs/DEPLOYMENT.md`, mục "PayOS".

### B4. Google OAuth

Google Cloud Console → Credentials → OAuth client → **Authorized redirect URIs** thêm:
`https://<DOMAIN>/login/oauth2/code/google`

### B5. Kiểm tra DNS trước khi chạy

```bash
dig +short <DOMAIN>     # phải ra đúng <VPS_IP>
```

Caddy chỉ lấy được chứng chỉ HTTPS khi domain đã trỏ đúng và cổng 80/443 mở.

### B6. Chạy lần đầu

Làm theo [DEPLOYMENT.md mục 3](DEPLOYMENT.md#3-first-start) (4 lệnh: postgres → backend `staging` một lần →
script bảng màu → `docker compose up -d --wait`). Build lần đầu mất vài phút đến hơn 10 phút tuỳ VPS.

Kiểm tra:

```bash
docker compose ps                                                  # 3 dịch vụ "healthy"
curl -fsS https://<DOMAIN>/api/v1/reference/ph-bands | head -c 120; echo
docker compose logs frontend | grep -i certificate                 # Caddy đã lấy chứng chỉ
```

Rồi tạo admin đầu tiên theo DEPLOYMENT.md, mục "Create the first admin".

### B7. Backup DB tự động mỗi đêm

`crontab -e` (user `deploy`), thêm 2 dòng (dấu `%` phải viết là `\%` trong crontab):

```cron
# 03:00 mỗi ngày: dump DB, giữ 14 ngày
0 3 * * * cd /opt/prod/catcheck && docker compose exec -T postgres pg_dump -U catcheck -Fc catcheck > /opt/prod/backups/db-$(date +\%F).dump && find /opt/prod/backups -name '*.dump' -mtime +14 -delete
# 04:00 Chủ nhật: dọn cache build cũ hơn 7 ngày (VPS tự build nên cache tăng mỗi lần deploy)
0 4 * * 0 docker builder prune -f --filter until=168h && docker image prune -f
```

Đổi `-U catcheck` / `catcheck` nếu bạn đặt `DB_USER` / `DB_NAME` khác. Backup nằm trên cùng VPS chưa phải backup
thật: nên định kỳ chép `/opt/prod/backups` (và volume ảnh nếu không dùng Cloudinary, xem DEPLOYMENT.md mục 6)
sang nơi khác, ví dụ bằng `rclone` lên Google Drive/S3. Dữ liệu có thông tin cá nhân — mã hoá khi lưu ngoài.

---

## C. GitHub Actions: CI/CD

### C0. Hiện trạng các workflow (bản lấy lại ở bước B0)

| File | Trạng thái | Cần làm |
|---|---|---|
| `backend-ci.yml` | Chỉ chạy tay (`workflow_dispatch`) | Thêm trigger PR + push (C3) |
| `frontend-ci.yml` | Chỉ chạy tay; bước `prettier --check` **đang fail** (186 file chưa format) | Thêm trigger; hoặc chạy `npx prettier --write .` một lần rồi commit, hoặc bỏ bước prettier |
| `deploy.yml` | **Đang tắt** (`if: false`), và lệch setup mới: `docker compose pull` (cần registry), thư mục `/opt/catcheck-<env>`, health check `localhost:8080` (prod không mở cổng này) | Thay nội dung (C4) |
| `build-image.yml` | Chỉ chạy tay; build + đẩy image lên GHCR | Chưa cần — dành cho bước nâng cấp (C7) |
| `e2e.yml`, `dependency-scan.yml`, `secret-scan.yml` | Chỉ chạy tay | Chạy thử bằng tay trước, chỉ bật tự động khi đã xanh |

### C1. Mô hình đề xuất

```
PR vào main ──► Backend CI + Frontend CI (bắt buộc xanh mới merge được)
merge vào main ──► Deploy: SSH vào VPS → git pull → backup DB → docker compose up -d --build --wait
                          → kiểm tra https://<DOMAIN> từ GitHub
```

Build image **ngay trên VPS**, không cần registry, không phải sửa `docker-compose.prod.yml`. Đổi lại VPS cần RAM
+ swap (A7) và dọn cache build (B7).

### C2. Key SSH riêng cho GitHub Actions

Trên **máy của bạn** (không phải VPS):

```bash
ssh-keygen -t ed25519 -f ./gha_catcheck -N "" -C "github-actions-catcheck"
```

- Nội dung `gha_catcheck.pub` → thêm 1 dòng vào `/home/deploy/.ssh/authorized_keys` trên VPS.
- Nội dung `gha_catcheck` (private key) → GitHub secret `SSH_KEY` (bước dưới). Sau đó **xoá** 2 file này khỏi máy.

Lấy fingerprint của VPS (để Actions chắc chắn đang kết nối đúng máy, chống giả mạo):

```bash
# chạy trên VPS
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub | awk '{print $2}'   # dạng SHA256:xxxx
```

Trên GitHub: **Settings → Environments → New environment** tên `production`:

| Loại | Tên | Giá trị |
|---|---|---|
| Secret | `SSH_HOST` | `<VPS_IP>` |
| Secret | `SSH_USER` | `deploy` |
| Secret | `SSH_KEY` | private key `gha_catcheck` (cả dòng BEGIN/END) |
| Secret | `SSH_FINGERPRINT` | `SHA256:...` vừa lấy |
| Variable | `DOMAIN` | `<DOMAIN>` |

Tuỳ chọn: trong environment `production` bật **Required reviewers** để mỗi lần deploy cần bạn bấm duyệt.

### C3. Cho CI tự chạy

Trong `backend-ci.yml` và `frontend-ci.yml`, thay khối `on:` bằng:

```yaml
on:
  pull_request:
    branches: [main]
  push:
    branches: [main]
  workflow_dispatch: {}
```

Rồi **Settings → Rules → Rulesets → New branch ruleset** cho `main`: bắt buộc qua PR và bắt buộc các check
(tên job, không phải tên workflow) xanh:

- `Compile / test / JaCoCo` và `Architecture check (ArchUnit / Modulith verify)` (Backend CI)
- `Lint / typecheck / test / build` (Frontend CI)

Cần cho CI chạy ít nhất một lần thì tên check mới hiện ra để chọn.

### C4. Workflow deploy mới

Thay toàn bộ `.github/workflows/deploy.yml` bằng:

```yaml
name: Deploy (VPS)

on:
  push:
    branches: [main]
  workflow_dispatch: {}

concurrency:
  group: deploy-production
  cancel-in-progress: false   # không huỷ một lần deploy đang chạy dở

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Cập nhật code, backup DB, build và khởi động lại trên VPS
        uses: appleboy/ssh-action@v1.2.0
        with:
          host: ${{ secrets.SSH_HOST }}
          username: ${{ secrets.SSH_USER }}
          key: ${{ secrets.SSH_KEY }}
          fingerprint: ${{ secrets.SSH_FINGERPRINT }}
          command_timeout: 30m          # mặc định 10 phút, không đủ cho lần build đầu
          script: |
            set -euo pipefail
            cd /opt/prod/catcheck
            git fetch --prune origin main
            git reset --hard origin/main
            docker compose exec -T postgres pg_dump -U catcheck -Fc catcheck \
              > /opt/prod/backups/predeploy-$(date +%Y%m%d-%H%M%S).dump
            docker compose up -d --build --wait
            docker image prune -f

      - name: Kiểm tra site qua HTTPS
        run: |
          for i in $(seq 1 30); do
            if curl -fsS "https://${{ vars.DOMAIN }}/api/v1/reference/ph-bands" > /dev/null; then
              echo "Site OK"; exit 0
            fi
            sleep 5
          done
          echo "::error::https://${{ vars.DOMAIN }} không phản hồi sau 150 giây"
          exit 1
```

Giải thích nhanh:

- `git reset --hard origin/main`: VPS luôn đúng y code trên `main` (vì vậy không sửa file trong repo trên VPS).
- Backup **trước** khi chạy: Flyway tự chạy migration khi backend khởi động và không có rollback tự động.
- `--wait`: lệnh chỉ thành công khi cả 3 container `healthy`; không thì job đỏ và bạn được báo.
- `appleboy/ssh-action@v1.2.0` là bản `main` đang dùng; Dependabot đã đề xuất `1.2.5`. CLAUDE.md yêu cầu chốt
  version theo `research-integrations.md`, nên tự quyết bản nào.

### C5. Chạy thử toàn bộ quy trình

1. Tạo một PR nhỏ (ví dụ sửa 1 dòng README) → **Backend CI** và **Frontend CI** chạy và xanh.
2. Merge → tab **Actions** thấy **Deploy (VPS)** chạy (bấm duyệt nếu bật Required reviewers).
3. Mở `https://<DOMAIN>` kiểm tra; trên VPS `docker compose ps`.

### C6. Khi deploy hỏng: quay lại bản trước

```bash
cd /opt/prod/catcheck
git log --oneline -5                 # chọn commit tốt gần nhất
git reset --hard <commit_tốt>
docker compose up -d --build --wait
```

Nếu lỗi do migration làm hỏng dữ liệu, khôi phục bản `predeploy-*.dump` mới nhất theo DEPLOYMENT.md mục 6.
Sau đó sửa lỗi trên `main` (revert commit) để lần deploy tự động sau không đưa lỗi trở lại.

### C7. Nâng cấp sau (không bắt buộc)

Khi VPS yếu hoặc build lâu: build image trên GitHub (`build-image.yml` đã có sẵn, đẩy lên GHCR), VPS chỉ
`docker compose pull`. Việc này cần sửa `docker-compose.prod.yml` dùng `image: ghcr.io/...` thay cho `build:`
và đăng nhập GHCR trên VPS — làm sau khi quy trình trên đã chạy ổn.

---

## D. Vận hành hằng ngày

| Việc | Lệnh (trong `/opt/prod/catcheck`) |
|---|---|
| Trạng thái | `docker compose ps` |
| Xem log | `docker compose logs -f backend` (hoặc `frontend`, `postgres`) |
| Dung lượng | `df -h /` và `docker system df` |
| Lỗi CSP cần xem trước khi bật chặn | `docker compose logs backend \| grep csp_violation` |
| Cập nhật OS | tự động (A6); thỉnh thoảng `sudo apt full-upgrade` + `sudo reboot` |
| Chứng chỉ HTTPS | Caddy tự gia hạn; đừng xoá volume `caddy_data` |

Các lệnh theo từng phần (restart, shell, stop…) và backup/khôi phục chi tiết: xem [DEPLOYMENT.md](DEPLOYMENT.md).
