# CatCheck deployment guide

CatCheck runs as one Docker container per part, defined in `docker-compose.prod.yml`:

```
 Internet ──80/443──▶ frontend (Caddy)        serves the built React app, gets the HTTPS certificate,
                         │                     proxies /api, /oauth2, /login to the backend
                         ▼
                      backend (Spring Boot)   :8080, internal only; runs Flyway migrations on start
                         │
                         ▼
                      postgres (PG 18 + pgvector)   :5432, internal only
```

| Service    | Image / build                         | Published ports       | Data volume                                  |
|------------|---------------------------------------|-----------------------|----------------------------------------------|
| `postgres` | `pgvector/pgvector:0.8.7-pg18-trixie` | none                  | `pg_data` (database)                         |
| `backend`  | built from `backend/Dockerfile`       | none                  | `media_storage` (images, PDF reports, DSAR exports) |
| `frontend` | built from `frontend/Dockerfile`      | 80, 443 (tcp + udp)   | `caddy_data` (TLS certificates), `caddy_config` |
| `adminer`  | `adminer` (profile `tools`, optional) | 127.0.0.1:8081 only   | none                                         |

Only the frontend is reachable from outside. The backend and database are on the private Compose network.

Setting up a fresh Ubuntu VPS (Docker, firewall, `/opt/prod`) and GitHub Actions CI/CD: see [VPS_SETUP.md](VPS_SETUP.md).

## 1. Server requirements

- A Linux **x86_64** server. The backend image bundles OpenCV for `linux-x86_64` only, so ARM servers do not work.
- Docker Engine with the Compose v2 plugin (`docker compose version`).
- A domain whose DNS A/AAAA record points at the server, and ports 80 and 443 open in the firewall.
  Caddy needs both to get the certificate.
- An SMTP account for outgoing mail (OTP codes, reminders, DSAR links).
- A Google OAuth client with the authorized redirect URI `https://<DOMAIN>/login/oauth2/code/google`.

HTTPS is required, not optional. The session cookie is named `__Host-CATCHECK_SESSION`, and browsers only
accept that cookie over HTTPS, so login fails on plain HTTP or a bare IP address.

## 2. Configure

```bash
git clone <repo-url> catcheck && cd catcheck
cp .env.prod.example .env
chmod 600 .env
```

Fill in every empty value in `.env`. The file explains each one and shows how to generate the secrets:

| Variable | What it is |
|---|---|
| `COMPOSE_FILE` | Already set to `docker-compose.prod.yml`, so plain `docker compose …` uses the prod stack. |
| `DOMAIN` | Public host name, for example `catcheck.example.com`. |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Database credentials. The password is used when the volume is first created. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP with STARTTLS. Mail is sent from `MAIL_USERNAME`. |
| `GOOGLE_OAUTH_CLIENT_ID`, `GOOGLE_OAUTH_CLIENT_SECRET` | Google login. |
| `APP_PII_ENCRYPTION_KEYS` | Encryption key for personal data, format `v1:<base64 32 bytes>`. |
| `ACTIVATION_PEPPER`, `RECOVERY_CODE_PEPPER`, `OTP_PEPPER` | Three different secrets, 32 bytes or more each. |
| `CATCHECK_STORAGE_LOCAL_URLSIGNINGKEY` | Signs image URLs when images are stored on the server volume. |

Back up `.env` somewhere safe outside the server. If you lose `APP_PII_ENCRYPTION_KEYS` or the peppers,
you cannot read the encrypted data or verify existing codes again.

Optional integrations are commented out at the bottom of `.env`. Uncomment only the ones you use.
An empty value is not the same as an unset one.

- **Cloudinary:** set the three `CLOUDINARY_*` values to store images there instead of on the `media_storage` volume.
- **AI assistant LLM:** set `CATCHECK_AI_ENABLED=true` and `CATCHECK_AI_API_KEY`. When it is off, the assistant
  answers by quoting the care-tip articles.
- **Push notifications (backend):** put the service-account JSON on the server, set `FIREBASE_SERVICE_ACCOUNT_PATH`
  and `FIREBASE_PROJECT_ID`, and mount the file into the backend by adding this under `backend:` in
  `docker-compose.prod.yml`:
  ```yaml
      volumes:
        - media_storage:/var/lib/catcheck/storage
        - ./firebase-service-account.json:/run/secrets/firebase-service-account.json:ro
  ```
- **Frontend build-time values** (`VITE_FIREBASE_*`, `VITE_MAP_TILES_URL`): Vite bakes these into the JS bundle at
  build time, so they do not belong in `.env`. Put them in `frontend/.env.production` (git-ignored) before you
  build the frontend image. Leave out any variable you do not use. An empty `VITE_MAP_TILES_URL` breaks the app
  on load.

### PayOS (online payment for shop orders)

Without PayOS, the shop only offers cash on delivery (COD). To turn on online payment:

1. Create a payment channel on [my.payos.vn](https://my.payos.vn) and link your bank account to it.
2. Copy the channel's **Client ID**, **API Key** and **Checksum Key** into `.env` as `PAYOS_CLIENT_ID`,
   `PAYOS_API_KEY` and `PAYOS_CHECKSUM_KEY` (uncomment them). Set all three: if any one is missing, PayOS stays off.
3. Restart the backend: `docker compose up -d backend`.
4. In the PayOS dashboard, set the channel's webhook URL to `https://<DOMAIN>/api/v1/payments/payos/webhook`.
   PayOS sends a test request when you save it, and the endpoint answers 200. The endpoint is public and Caddy
   already proxies `/api/*`, so nothing else needs opening. Requests with a bad signature get 400.
5. Place a real order for a small amount and check that it moves to `PAID` (My orders, or the admin order list).

Good to know:

- `CATCHECK_SHOP_WEBBASEURL` is already set to `https://${DOMAIN}` in `docker-compose.prod.yml`. PayOS sends the
  buyer back to `https://<DOMAIN>/orders/<id>`, so do not override it with another host.
- An online order that is still unpaid after 5 minutes is cancelled automatically. COD orders are never cancelled
  this way.
- If PayOS is down or the keys are not set, choosing online payment at checkout fails with 503
  `PAYMENT_UNAVAILABLE`. COD keeps working.

## 3. First start

On a new database, the `prod` profile refuses to start. It stops with
`bang mau dang ACTIVE la PLACEHOLDER` because the only color chart is the seeded placeholder.
`backend/tools/publish-standard-color-chart.sql` replaces it with the standard pH color scale.
That scale runs from yellow at pH 5.5, through green, to blue at pH 8.0, in 7 levels.
The script needs the tables to exist, so the first start runs the backend once with the `staging` profile
(same config, without that check) to create them.

Start the parts one at a time so you can see each one come up:

```bash
docker compose build                                    # build backend + frontend images (the first build takes several minutes)

docker compose up -d postgres                           # 1. database
BACKEND_PROFILE=staging docker compose up -d --wait backend   # 2. API, first start only: Flyway creates the tables

docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U catcheck -d catcheck \
  < backend/tools/publish-standard-color-chart.sql      # 3. publish the standard pH chart (safe to re-run)

docker compose up -d --wait                             # 4. backend restarts in prod, frontend (web + HTTPS) starts
docker compose exec backend printenv SPRING_PROFILES_ACTIVE   # prod
docker compose ps                                       # all three should be "running (healthy)"
```

From then on, a plain `docker compose up -d` (or `--build`) starts everything in the right order through the
`depends_on` health conditions. Leave `BACKEND_PROFILE` unset in `.env`.

**About the chart:** the standard scale is a generic pH indicator scale. It was not measured on CatCheck's
litter. When you have measured colors, create a new chart in `/admin/ph-color-chart` and publish it. That
archives the standard chart automatically. For each color, enter the pH value, the hex color, a Vietnamese
name and a ΔE tolerance (the standard chart uses `4`). The form fills in Lab from the hex. If you have Lab
values measured on the real litter, type them in instead; the form never overwrites typed Lab values.
Publishing requires:
- at least 4 colors, including pH **6.3** and **6.6** exactly
- pH values in increasing order
- neighboring colors that are clearly different (ΔE2000 ≥ 3)

Check that it works:

```bash
curl -fsS https://<DOMAIN>/api/v1/reference/ph-bands | head -c 200; echo   # API through Caddy
docker compose exec backend curl -fsS localhost:8080/actuator/health/readiness; echo
docker compose logs frontend | grep -i certificate                     # TLS certificate obtained
```

Then open `https://<DOMAIN>` in a browser.

### Create the first admin

The `prod` profile does not seed any accounts. Register a normal account in the web app, then grant it the
super-admin role:

```bash
docker compose exec postgres psql -U catcheck -d catcheck -c \
  "INSERT INTO user_role (user_id, role) SELECT id, 'ADMIN_SUPER' FROM app_user WHERE email = 'you@example.com';"
```

Sign out and sign in again so the session picks up the new role. Admin pages require TOTP two-factor
authentication, so the account must set up TOTP before the admin API will respond. Grant other staff roles
from the admin console after that.

## 4. Working with one part at a time

Each part is a separate container, so you can build, restart, inspect, or stop it on its own.
`docker compose up -d <svc>` also starts that service's dependencies if they are not running.

| Task | postgres | backend | frontend |
|---|---|---|---|
| Rebuild image | n/a (pulled) | `docker compose build backend` | `docker compose build frontend` |
| Deploy new version | n/a | `docker compose up -d backend` | `docker compose up -d frontend` |
| Restart | `docker compose restart postgres` | `docker compose restart backend` | `docker compose restart frontend` |
| Logs | `docker compose logs -f postgres` | `docker compose logs -f backend` | `docker compose logs -f frontend` |
| Shell | `docker compose exec postgres psql -U catcheck -d catcheck` | `docker compose exec backend bash` | `docker compose exec frontend sh` |
| Stop | `docker compose stop postgres` | `docker compose stop backend` | `docker compose stop frontend` |

Optional DB UI: `docker compose --profile tools up -d adminer`. It listens on the server's localhost only.
Reach it with `ssh -L 8081:localhost:8081 <server>`, then open `http://localhost:8081`
(server `postgres`, user/password from `.env`).

## 5. Updating

```bash
git pull
docker compose exec postgres pg_dump -U catcheck -Fc catcheck > backup-$(date +%F).dump   # before every backend update
docker compose build backend frontend
docker compose up -d backend      # Flyway applies new migrations on start
docker compose up -d frontend
docker image prune -f             # remove old image layers
```

If only frontend code changed, rebuild and restart only `frontend`, and the reverse for backend-only changes.
The site is unavailable while the backend container restarts. One backend instance is the supported setup.

## 6. Backup and restore

What to back up: the database, the `media_storage` volume (when not using Cloudinary), and `.env`.

```bash
# Database
docker compose exec postgres pg_dump -U catcheck -Fc catcheck > catcheck-$(date +%F).dump

# Media volume (Compose project name is catcheck-prod)
docker run --rm -v catcheck-prod_media_storage:/data -v "$PWD":/backup alpine \
  tar czf /backup/media-$(date +%F).tar.gz -C /data .
```

Restore the database into an empty stack:

```bash
docker compose up -d postgres
docker compose exec -T postgres pg_restore -U catcheck -d catcheck --clean --if-exists < catcheck-YYYY-MM-DD.dump
docker compose up -d
```

Backups contain personal data. Store them encrypted and with restricted access.

## 7. Running the full stack locally

`docker-compose.yml` is the local stack: postgres and mailpit by default, plus the backend and frontend
containers under the `full` profile. It uses the `local` profile and a local-only database password,
so do not use it on a server.

```bash
cp .env.example .env              # fill in the 4 required secrets and the Google OAuth values
docker compose --profile full up -d --build
# app: http://localhost:5173   mail: http://localhost:8025
```

For day-to-day development, run the backend and frontend natively. See `README.md`.

## 8. Troubleshooting

| Symptom | Likely cause |
|---|---|
| `docker compose up` fails with `set DB_PASSWORD in .env` (or `DOMAIN`) | The value is missing or empty in `.env`. |
| `frontend` logs show ACME/certificate errors | DNS does not point at the server yet, or port 80/443 is blocked. |
| Backend exits on start with `IllegalStateException` about a pepper or key | One of the four required secrets is missing or too short. |
| Backend exits on start with `bang mau dang ACTIVE la PLACEHOLDER` | The standard chart script has not run yet. See section 3. |
| Backend log says `Khong nap duoc OpenCV native` | The image was built from an old `backend/Dockerfile` without `libgtk2.0-0t64`. Rebuild it. Scans fail until then. |
| `dependency failed to start: container … backend is unhealthy` | Read `docker compose logs backend`. Usually a config value or the DB password is wrong. |
| DB auth fails after you changed `DB_PASSWORD` | Postgres only reads the password when the volume is first created. Change it with `ALTER ROLE` in psql. |
| Login works but you are signed out right away | The site is served over plain HTTP. See the HTTPS note in section 1. |
| Google login returns `redirect_uri_mismatch` | Add `https://<DOMAIN>/login/oauth2/code/google` in Google Cloud Console. |
| The server disk fills up | Container logs are not rotated by default. Set `log-opts` (`max-size`, `max-file`) in `/etc/docker/daemon.json`. |
