#!/usr/bin/env python3
"""Dung du lieu demo THAT cho CatCheck o may dev — mot lenh, chay lai duoc nhieu lan.

    export JAVA_HOME=$HOME/.local/jdk/jdk-25.0.4.1+1
    cd backend && CATCHECK_DEV_MANUAL_SCAN_ENABLED=true ./run-local.sh   # terminal 1
    backend/tools/seed-demo.py                                           # terminal 2

Script nay ton tai vi pipeline anh chua chay duoc tren du lieu that: bang mau pH da hieu
chuan va bo anh ground truth la du lieu owner CHUA cung cap (p20 X1/X2). Khong co scan thi
lich su, xu huong, export PDF, nhac nho, health flag va credit FEFO deu la man hinh rong.
Script dung du lieu qua DUONG API THAT (dang ky, kich hoat goi, tao meo, ghi scan bang
`POST /api/v1/dev/scans`) chu khong INSERT thang vao DB — nen moi bat bien nghiep vu (FEFO,
ledger, ScanSavedEvent, health flag, entitlement) deu duoc thuc thi that.

HAI NGOAI LE phai dung psql, va ly do:
  1. Doc `email_otp.code_hash` roi do nguoc ma OTP 6 chu so (10^6 HMAC-SHA256, ~2 giay).
     Khong co API nao tra ma OTP ra ngoai — dung thiet ke (p11 §11.2.1). Do nguoc bang
     OTP_PEPPER o .env la cach duy nhat lay ma ma khong phu thuoc email sink (Mailpit hay
     file deu duoc, khong can cai gi).
  2. INSERT `activation_code` (CHI bang ma, KHONG phai tai khoan). Khong co endpoint phat
     hanh ma — `ActivationCodeIssuanceService` chua co controller (UI admin thuoc M6). p18
     §"Seed data" liet ke dung "vai activation_code de test kich hoat" la noi dung seed hop
     le; tai khoan thi KHONG duoc seed bang SQL (p4 §4.9.2, p14 §14.5) nen tai khoan demo
     di qua `POST /auth/register` + OTP that.

AN TOAN: chi chay khi (a) API o localhost, (b) profile dang chay co `local` hoac `dev`, va
(c) endpoint dev-only `POST /api/v1/dev/scans` dang bat. Thieu mot trong ba thi script dung
lai va in cach khac phuc.
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import http.cookiejar
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]

DEMO_EMAIL = "demo@catcheck.vn"
# Trung dung hang so cua frontend/e2e/route-sweep.spec.ts — doi o mot noi thi hong o noi kia.
DEMO_PASSWORD = "DemoCat2025!"
DEMO_FULL_NAME = "Chu Nuoi Demo"

# Goi MULTI: 16 credit, khong gioi han so ho so meo, co trend + reminder + export
# (seed/R__seed_package_plan.sql). PLUS chi cho 1 ho so meo nen khong dung duoc o day.
DEMO_PACKAGE = "MULTI"
# Moi ma cho 16 credit. Pool 4 ma la du cho ke hoach 30 scan (27 lan tru credit, 3 lan dau
# dung luot trial) VA du cho mot lan chay lai sau khi da tieu het.
DEMO_CODE_POOL = 4
DEMO_PRODUCTION_BATCH = "DEMO-SEED-2026"

# Ten meo lay tu danh sach ten pho thong tu chon, khong dung thu vien sinh ten ngau nhien
# (p17 §17.8.3) — de khong vo tinh tao ra ten trung mot nguoi that.
CAT_A = {"name": "Mun", "sex": "MALE", "neutered": True, "weightKg": "4.20",
         "birthDate": "2022-05-10", "coatColor": "Den", "isPrimary": True,
         "notes": "Du lieu demo do backend/tools/seed-demo.py dung."}
CAT_B = {"name": "Bo", "sex": "FEMALE", "neutered": False, "weightKg": "3.10",
         "birthDate": "2023-08-01", "coatColor": "Vang kem", "isPrimary": False,
         "notes": "Du lieu demo do backend/tools/seed-demo.py dung."}

# ---------------------------------------------------------------------------
# Ke hoach scan. `h` = so gio TRUOC thoi diem chay; `cat` = "A" | "B" | None
# (None = SHARED_UNKNOWN). `ph=None` + co BLOCKING => INCONCLUSIVE.
#
# MOI con meo co MOT CAP scan confidence < 0.50 cach nhau > 6h, roi MOT scan nua sau do.
# Do la dieu kien cua rule R4 LOW_QUALITY_STREAK (streak=2, maxConfidence=0.50 — seed
# monitoring_rule), rule DUY NHAT con chay khi bang mau con placeholder; R1-R3 bi tat theo
# p6 §6.9.1 nguyen tac 5. Nho vay `GET /health-flags` co du lieu that chu khong rong.
#
# Vi sao can scan THU BA: RuleEvaluationService doc lich su bang JdbcTemplate
# (JdbcScanQueryRepository.findRecentForRules) trong CUNG transaction ma ScanPersistenceService
# ghi scan bang JPA. `scanRepository.save()` chua flush luc su kien phat ra, nen cau JOIN
# `sa.id = s.current_analysis_id` KHONG thay chinh scan vua tao — rule chi nhin thay cac scan
# DA COMMIT truoc do. Da xac nhan that: cap scan 0.45/0.45 khong sinh flag, them mot scan nua
# ngay sau thi flag xuat hien. Day la loi co san o ranh gioi scan<->insight (khong phai cua
# duong nhap tay); ke hoach nay chi khong phu thuoc vao viec no duoc sua hay chua — sua roi
# thi flag chi noi len som hon mot nhip, va dedupe_key (bucket 24h) van giu dung mot flag/meo.
#
# Khoang cach giua cac scan deu >= 6h de khong roi vao nhanh `minGapHours` cua R3 khi bang
# mau that duoc nap sau nay — luc do xu huong se tu kich hoat tren chinh du lieu nay.
# ---------------------------------------------------------------------------
SCAN_PLAN: list[dict] = [
    # --- Mun: 86 ngay lich su, di qua gan het cac dai phan loai ---
    {"id": "a01", "h": 2064, "cat": "A", "ph": "6.45", "conf": "0.82"},
    {"id": "a02", "h": 1920, "cat": "A", "ph": "6.50", "conf": "0.80"},
    {"id": "a03", "h": 1776, "cat": "A", "ph": "6.40", "conf": "0.78"},
    {"id": "a04", "h": 1632, "cat": "A", "ph": "6.55", "conf": "0.81"},
    {"id": "a05", "h": 1488, "cat": "A", "ph": "6.30", "conf": "0.76"},
    {"id": "a06", "h": 1344, "cat": "A", "ph": "6.70", "conf": "0.74"},
    {"id": "a07", "h": 1200, "cat": "A", "ph": "6.85", "conf": "0.72"},
    {"id": "a08", "h": 1056, "cat": "A", "ph": "6.60", "conf": "0.79"},
    {"id": "a09", "h": 912, "cat": "A", "ph": None, "flags": ["BLURRY"]},
    {"id": "a10", "h": 768, "cat": "A", "ph": "6.45", "conf": "0.83"},
    {"id": "a11", "h": 624, "cat": "A", "ph": "6.20", "conf": "0.70", "dispute": "Be moi doi thuc an."},
    {"id": "a12", "h": 480, "cat": "A", "ph": "5.95", "conf": "0.68"},
    {"id": "a13", "h": 336, "cat": "A", "ph": "6.35", "conf": "0.80"},
    {"id": "a14", "h": 192, "cat": "A", "ph": "6.50", "conf": "0.84"},
    # Cap confidence thap + mot scan sau do => R4 LOW_QUALITY_STREAK cho Mun.
    {"id": "a15", "h": 36, "cat": "A", "ph": "6.40", "conf": "0.45"},
    {"id": "a16", "h": 26, "cat": "A", "ph": "6.55", "conf": "0.45"},
    {"id": "a17", "h": 7, "cat": "A", "ph": "6.45", "conf": "0.78"},
    # --- Bo: lich su ngan hon, co mot lan HIGH ---
    {"id": "b01", "h": 1680, "cat": "B", "ph": "6.50", "conf": "0.79"},
    {"id": "b02", "h": 1344, "cat": "B", "ph": "6.60", "conf": "0.77"},
    {"id": "b03", "h": 1008, "cat": "B", "ph": "7.10", "conf": "0.73", "dispute": "Anh chup bi nguoc sang."},
    {"id": "b04", "h": 672, "cat": "B", "ph": None, "flags": ["NO_INDICATOR_GRAINS"]},
    {"id": "b05", "h": 504, "cat": "B", "ph": "6.45", "conf": "0.81"},
    {"id": "b06", "h": 240, "cat": "B", "ph": "6.35", "conf": "0.78"},
    # Cap confidence thap + mot scan sau do => R4 LOW_QUALITY_STREAK cho Bo.
    {"id": "b07", "h": 34, "cat": "B", "ph": "6.65", "conf": "0.48"},
    {"id": "b08", "h": 22, "cat": "B", "ph": "6.75", "conf": "0.46"},
    {"id": "b09", "h": 5, "cat": "B", "ph": "6.50", "conf": "0.80"},
    # --- Khay dung chung, chua biet cua be nao ---
    {"id": "s01", "h": 1200, "cat": None, "ph": "6.50", "conf": "0.70"},
    {"id": "s02", "h": 600, "cat": None, "ph": "6.40", "conf": "0.68"},
    {"id": "s03", "h": 300, "cat": None, "ph": None, "flags": ["TOO_DARK"]},
    # Con trong cua so doi meo 24h (ScanThresholds.REASSIGN_WINDOW_HOURS) — se doi sang Mun.
    {"id": "s04", "h": 16, "cat": None, "ph": "6.45", "conf": "0.72", "reassign_to": "A"},
]

CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"


# ===========================================================================
# Ha tang: psql, HTTP
# ===========================================================================

class Psql:
    """Chay SQL doc/ghi bang `psql`. Chi dung cho hai ngoai le ghi o docstring dau file."""

    def __init__(self, host: str, port: str, db: str, user: str, password: str):
        self.env = dict(os.environ, PGPASSWORD=password)
        self.base = ["psql", "-h", host, "-p", port, "-U", user, "-d", db,
                     "-v", "ON_ERROR_STOP=1", "--no-psqlrc"]

    def rows(self, sql: str) -> list[list[str]]:
        out = subprocess.run(self.base + ["-At", "-F", "\x1f", "-c", sql],
                             env=self.env, capture_output=True, text=True)
        if out.returncode != 0:
            raise RuntimeError(f"psql loi: {out.stderr.strip()}")
        return [line.split("\x1f") for line in out.stdout.splitlines() if line]

    def exec(self, sql: str) -> None:
        out = subprocess.run(self.base + ["-q"], input=sql,
                             env=self.env, capture_output=True, text=True)
        if out.returncode != 0:
            raise RuntimeError(f"psql loi: {out.stderr.strip()}")


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str, method: str, path: str):
        super().__init__(f"{method} {path} -> HTTP {status}: {body[:400]}")
        self.status = status
        self.body = body
        self.code = ""
        try:
            # ProblemDetail cua CatCheck: `errorCode` = ten hang ErrorCode (vd
            # ACTIVATION_CODE_ALREADY_USED), `title` lap lai no, `type` la ban slug.
            payload = json.loads(body) or {}
            self.code = payload.get("errorCode") or payload.get("code") or ""
        except (ValueError, AttributeError):
            pass


class Api:
    """Client HTTP giu cookie phien + echo cookie XSRF-TOKEN vao header X-XSRF-TOKEN.

    Day la dung pattern SPA ma IdentitySecurityConfig cau hinh (plain
    CsrfTokenRequestAttributeHandler + CookieCsrfTokenRepository.withHttpOnlyFalse).
    """

    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _csrf(self) -> str | None:
        for cookie in self.jar:
            if cookie.name == "XSRF-TOKEN":
                return cookie.value
        return None

    def call(self, method: str, path: str, body=None, headers: dict | None = None):
        url = self.base + path
        data = None
        hdrs = {"Accept": "application/json"}
        if body is not None:
            data = json.dumps(body).encode()
            hdrs["Content-Type"] = "application/json"
        if method not in ("GET", "HEAD"):
            token = self._csrf()
            if token:
                hdrs["X-XSRF-TOKEN"] = token
        hdrs.update(headers or {})
        req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
        try:
            with self.opener.open(req, timeout=60) as resp:
                raw = resp.read().decode("utf-8", "replace")
        except urllib.error.HTTPError as err:
            raise ApiError(err.code, err.read().decode("utf-8", "replace"), method, path) from None
        except urllib.error.URLError as err:
            raise RuntimeError(f"Khong ket noi duoc {url}: {err.reason}") from None
        if not raw.strip():
            return None
        try:
            return json.loads(raw)
        except ValueError:
            return raw

    def get(self, path):
        return self.call("GET", path)

    def post(self, path, body=None, headers=None):
        return self.call("POST", path, body, headers)

    def bootstrap_csrf(self) -> None:
        self.call("GET", "/api/v1/auth/csrf")


# ===========================================================================
# OTP: do nguoc ma 6 chu so tu code_hash
# ===========================================================================

def read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def recover_otp(db: Psql, pepper: str, email: str, purpose: str) -> str:
    """Do nguoc ma OTP dang con hieu luc.

    `email_otp.code_hash` = HMAC-SHA256(OTP_PEPPER, purpose || email || code), hex lowercase
    (HmacOtpCodeHasher). Khong gian ma la 10^6 nen do nguoc het ~2 giay — dung duoc o may dev
    va KHONG phai mot lo hong: ai co OTP_PEPPER + quyen doc DB thi da co toan quyen roi.
    """
    rows = db.rows(
        "SELECT code_hash FROM email_otp "
        f"WHERE email = '{email}' AND purpose = '{purpose}' "
        "AND consumed_at IS NULL AND expires_at > now() "
        "ORDER BY created_at DESC LIMIT 1")
    if not rows:
        raise RuntimeError(
            f"Khong thay challenge OTP nao dang hieu luc cho {purpose}. "
            "Backend co gui OTP khong? Kiem tra log cua run-local.sh.")
    target = rows[0][0].strip()
    key = pepper.encode()
    prefix = (purpose + email).encode()
    for number in range(1_000_000):
        code = b"%06d" % number
        if hmac.new(key, prefix + code, hashlib.sha256).hexdigest() == target:
            return code.decode()
    raise RuntimeError(
        "Do nguoc OTP that bai — OTP_PEPPER trong .env khong khop pepper ma backend dang chay.")


# ===========================================================================
# Ma kich hoat
# ===========================================================================

def crockford_checksum(body: str) -> str:
    """Ky tu checksum cuoi ma kich hoat — chep dung ActivationCodeFormat.checksumChar."""
    total = 0
    length = len(body)
    for index, char in enumerate(body):
        power = 1
        for _ in range(length - 1 - index):
            power = (power * 31) % 32
        total += CROCKFORD.index(char) * power
    return CROCKFORD[(32 - (total % 32)) % 32]


def demo_activation_code(package_code: str, serial: int) -> str:
    """Ma kich hoat tien doan duoc cho demo: `CC-<GOI>-DEM0SEED<n><checksum>`.

    Tien doan duoc la CHU DICH o day (chay lai script phai ra dung ma do, neu khong moi lan
    chay se sinh thanh mot lo credit moi). Ma that thi sinh bang SecureRandom trong
    ActivationCodeIssuanceService — duong do khong bi lop nay dung tới.
    """
    body = f"DEM0SEED{serial % 10}"
    assert len(body) == 9, body
    return f"CC-{package_code}-{body}{crockford_checksum(body)}"


def ensure_activation_codes(db: Psql, pepper: str, codes: list[str], package_code: str) -> None:
    """INSERT cac ma demo neu chua co. `ON CONFLICT DO NOTHING` => chay lai khong nhan doi."""
    values = []
    for code in codes:
        code_hash = hmac.new(pepper.encode(), code.encode(), hashlib.sha256).hexdigest()
        prefix = (package_code + "-")[:8]
        values.append(
            f"(gen_random_uuid(), '{code_hash}', 1, '{prefix}', '{package_code}', "
            f"'{DEMO_PRODUCTION_BATCH}', now(), now() + interval '365 days', 'ISSUED')")
    db.exec(
        "INSERT INTO activation_code (id, code_hash, pepper_version, code_prefix, package_code, "
        "production_batch, issued_at, valid_until, status) VALUES\n"
        + ",\n".join(values)
        + "\nON CONFLICT (code_hash) DO NOTHING;")


# ===========================================================================
# Cac buoc seed
# ===========================================================================

def ensure_account(api: Api, db: Psql, otp_pepper: str) -> dict:
    """Tai khoan demo o trang thai ACTIVE + dang nhap xong.

    Ba nhanh, theo dung thu tu re nhat truoc:
      1. Dang nhap duoc ngay          -> xong.
      2. Chua co tai khoan            -> register -> OTP -> register lai kem ticket.
      3. Co tai khoan nhung sai mat khau -> password-reset bang OTP roi dang nhap.
    """
    api.bootstrap_csrf()
    try:
        api.post("/api/v1/auth/login", {"email": DEMO_EMAIL, "password": DEMO_PASSWORD})
        print(f"  tai khoan {DEMO_EMAIL}: dang nhap duoc, giu nguyen")
        return api.get("/api/v1/auth/session")
    except ApiError as err:
        if err.status not in (401, 403, 404, 422):
            raise
        login_error = err

    exists = db.rows(f"SELECT 1 FROM app_user WHERE email = '{DEMO_EMAIL}'")
    if not exists:
        consents = [{"purposeCode": code, "granted": True} for code in
                    ("SERVICE_CORE", "SCAN_IMAGE_RETAIN", "HEALTH_REMINDER_PUSH",
                     "HEALTH_REMINDER_EMAIL")]
        api.post("/api/v1/auth/register", {
            "email": DEMO_EMAIL, "password": DEMO_PASSWORD, "fullName": DEMO_FULL_NAME,
            "locale": "vi", "consents": consents})
        code = recover_otp(db, otp_pepper, DEMO_EMAIL, "REGISTER_VERIFY")
        verified = api.post("/api/v1/auth/otp/verify", {
            "email": DEMO_EMAIL, "purpose": "REGISTER_VERIFY", "code": code})
        api.post("/api/v1/auth/register", {
            "email": DEMO_EMAIL, "password": DEMO_PASSWORD, "fullName": DEMO_FULL_NAME,
            "locale": "vi", "otpTicket": verified["otpTicket"]})
        print(f"  tai khoan {DEMO_EMAIL}: da tao moi + xac thuc OTP")
    else:
        print(f"  tai khoan {DEMO_EMAIL}: da ton tai nhung dang nhap that bai "
              f"({login_error.code or login_error.status}) — dat lai mat khau bang OTP")
        api.post("/api/v1/auth/password-reset/request", {"email": DEMO_EMAIL})
        code = recover_otp(db, otp_pepper, DEMO_EMAIL, "PASSWORD_RESET")
        verified = api.post("/api/v1/auth/otp/verify", {
            "email": DEMO_EMAIL, "purpose": "PASSWORD_RESET", "code": code})
        api.post("/api/v1/auth/password-reset/confirm", {
            "token": verified["otpTicket"], "newPassword": DEMO_PASSWORD})

    session = api.get("/api/v1/auth/session")
    if not session.get("authenticated"):
        api.post("/api/v1/auth/login", {"email": DEMO_EMAIL, "password": DEMO_PASSWORD})
        session = api.get("/api/v1/auth/session")
    if not session.get("authenticated"):
        raise RuntimeError("Dang nhap that bai sau khi tao/dat lai tai khoan demo.")
    return session


def ensure_cats(api: Api) -> dict[str, str]:
    """Hai ho so meo, khop theo TEN nen chay lai khong tao them."""
    existing = {item["name"]: item["id"] for item in api.get("/api/v1/cats")["items"]}
    ids = {}
    for slot, spec in (("A", CAT_A), ("B", CAT_B)):
        if spec["name"] in existing:
            ids[slot] = existing[spec["name"]]
            print(f"  meo {spec['name']}: da co")
        else:
            created = api.post("/api/v1/cats", spec)
            ids[slot] = created["id"]
            print(f"  meo {spec['name']}: da tao")
    return ids


def ensure_credits(api: Api, db: Psql, activation_pepper: str) -> None:
    """Dam bao tai khoan CO it nhat mot lo credit — man hinh so du phai co noi dung that.

    KHONG tinh truoc "can bao nhieu credit cho ca ke hoach": chay lai lan hai thi moi scan
    deu la replay nen khong ton credit nao, va doi them credit o day se lam script bao loi
    oan. Thieu toi dau thi `ensure_scans` nap them toi do (xem `top_up_credit`).
    """
    balance = api.get("/api/v1/credits/balance")
    if (balance.get("availableBalance") or 0) > 0 or balance.get("batches"):
        print(f"  credit: da co {balance.get('availableBalance')} "
              f"(+{balance.get('trialScansRemaining')} luot trial), "
              f"{len(balance.get('batches') or [])} lo")
        return
    top_up_credit(api, db, activation_pepper)


def top_up_credit(api: Api, db: Psql, activation_pepper: str) -> None:
    """Doi ma demo chua dung dau tien lay them mot lo credit.

    Ma demo tien doan duoc (`demo_activation_code`) nen chay lai khong sinh ma moi; ma da doi
    roi tra 409 ACTIVATION_CODE_ALREADY_USED va ta di tiep sang ma ke — dung mot lan moi ma,
    dung bat bien I24 (mot ma doi ra dung mot lo).
    """
    codes = [demo_activation_code(DEMO_PACKAGE, i) for i in range(1, DEMO_CODE_POOL + 1)]
    ensure_activation_codes(db, activation_pepper, codes, DEMO_PACKAGE)
    for code in codes:
        try:
            result = api.post("/api/v1/activations", {"code": code})
            print(f"  credit: kich hoat {DEMO_PACKAGE} +{result['creditsGranted']} "
                  f"(so du {result['balanceAfter']})")
            return
        except ApiError as err:
            if err.code == "ACTIVATION_CODE_ALREADY_USED":
                continue
            raise
    raise RuntimeError(
        f"Het ma demo chua dung ({DEMO_CODE_POOL} ma). Tang DEMO_CODE_POOL roi chay lai.")


def ensure_scans(api: Api, db: Psql, activation_pepper: str,
                 cat_ids: dict[str, str], now: datetime) -> dict[str, dict]:
    """Ghi het SCAN_PLAN qua `POST /api/v1/dev/scans`.

    Idempotency-Key tien doan duoc tu `id` cua tung dong ke hoach => chay lai la replay
    (ScanPersistenceService.replay), khong ghi them scan va KHONG tru credit lan hai (p5 R8).
    """
    # `ScanResultResponse` (hop dong chung voi POST /scans) khong co co "idempotentReplay",
    # nen dem ban ghi THAT truoc/sau thay vi tin vao response.
    before = scan_ids(api)
    results: dict[str, dict] = {}
    for entry in SCAN_PLAN:
        captured = now - timedelta(hours=entry["h"])
        body = {
            "capturedAt": captured.isoformat().replace("+00:00", "Z"),
            "phValue": entry.get("ph"),
            "confidence": entry.get("conf"),
            "qualityFlags": entry.get("flags"),
            "captureSource": "CAMERA",
            "deviceHint": "seed-demo",
        }
        slot = entry["cat"]
        if slot:
            body["catId"] = cat_ids[slot]
            body["assignment"] = "ASSIGNED"
        else:
            body["assignment"] = "SHARED_UNKNOWN"
        body = {k: v for k, v in body.items() if v is not None}
        key = f"seed-demo-{entry['id']}"
        try:
            result = api.post("/api/v1/dev/scans", body, {"Idempotency-Key": key})
        except ApiError as err:
            if err.code != "CREDIT_INSUFFICIENT":
                raise
            top_up_credit(api, db, activation_pepper)
            result = api.post("/api/v1/dev/scans", body, {"Idempotency-Key": key})
        # `scanId` la null voi ban INCONCLUSIVE — dung theo p8 §8.5.4, nen moi buoc sau
        # (dispute, doi meo) deu phai kiem truoc khi dung.
        results[entry["id"]] = result
    after = scan_ids(api)
    created = len(after - before)
    print(f"  scan: {len(SCAN_PLAN)} dong ke hoach — {created} ghi moi, "
          f"{len(SCAN_PLAN) - created} replay; tong lich su {len(after)}")
    return results


def scan_ids(api: Api) -> set[str]:
    """Tat ca scanId cua tai khoan demo, lat het trang bang con tro cua `GET /scans`."""
    ids: set[str] = set()
    cursor = None
    while True:
        path = "/api/v1/scans?limit=100" + (f"&cursor={cursor}" if cursor else "")
        page = api.get(path)
        ids.update(item["scanId"] for item in page["items"])
        if not page.get("hasMore"):
            return ids
        cursor = page["nextCursor"]


def ensure_disputes(api: Api, scans: dict[str, dict]) -> int:
    count = 0
    for entry in SCAN_PLAN:
        note = entry.get("dispute")
        scan_id = scans.get(entry["id"], {}).get("scanId")
        if not note or not scan_id:
            continue
        try:
            api.post(f"/api/v1/scans/{scan_id}/dispute", {"note": note})
            count += 1
        except ApiError as err:
            if err.code != "SCAN_ALREADY_DISPUTED":
                raise
    print(f"  dispute: {count} danh dau moi")
    return count


def ensure_reassign(api: Api, scans: dict[str, dict], cat_ids: dict[str, str]) -> int:
    count = 0
    for entry in SCAN_PLAN:
        slot = entry.get("reassign_to")
        scan_id = scans.get(entry["id"], {}).get("scanId")
        if not slot or not scan_id:
            continue
        current = api.get(f"/api/v1/scans/{scan_id}")
        # reassignRemaining = 3 nghia la chua doi lan nao (ScanThresholds.REASSIGN_MAX_COUNT).
        if current.get("reassignRemaining") != 3:
            continue
        api.post(f"/api/v1/scans/{scan_id}/reassign-cat",
                 {"toAssignment": "ASSIGNED", "toCatId": cat_ids[slot]})
        count += 1
    print(f"  doi meo: {count} lan moi")
    return count


def ensure_reminders(api: Api, cat_ids: dict[str, str]) -> int:
    wanted = [
        {"catId": cat_ids["A"], "type": "SCAN_ROUTINE", "scheduleKind": "INTERVAL",
         "intervalDays": 3, "preferredTimeStart": "08:00", "preferredTimeEnd": "10:00",
         "channels": ["PUSH", "EMAIL"], "source": "USER"},
        {"catId": cat_ids["B"], "type": "SCAN_ROUTINE", "scheduleKind": "INTERVAL",
         "intervalDays": 7, "preferredTimeStart": "19:00", "preferredTimeEnd": "21:00",
         "channels": ["PUSH"], "source": "SUGGESTED"},
        {"type": "CREDIT_EXPIRY", "scheduleKind": "INTERVAL", "intervalDays": 1,
         "channels": ["EMAIL"], "source": "SUGGESTED"},
    ]
    # Bat bien I23 (partial unique index) chi ep MOT reminder active moi (cat, type) khi
    # catId KHONG null — ReminderService bo qua kiem tra do voi CREDIT_EXPIRY (cap tai khoan,
    # catId = null). Khong tu loc o day thi moi lan chay lai se de them mot CREDIT_EXPIRY.
    existing = {(item.get("catId"), item.get("type"))
                for item in api.get("/api/v1/reminders")["items"]}
    count = 0
    for body in wanted:
        if (body.get("catId"), body["type"]) in existing:
            continue
        try:
            api.post("/api/v1/reminders", body)
            count += 1
        except ApiError as err:
            if err.code != "REMINDER_LIMIT_REACHED":
                raise
    print(f"  nhac nho: {count} tao moi, {len(existing)} da co")
    return count


def ensure_export(api: Api, cat_ids: dict[str, str]) -> tuple[str | None, str | None]:
    """Mot job xuat PDF. Tra `(jobId, status)` — status lay sau khi doi job chay xong.

    Chi tao khi chua co job nao: `export_job` co UNIQUE(user_id) WHERE status IN
    ('QUEUED','RUNNING') (p4 G1) nen job thu hai luon 409 EXPORT_JOB_IN_PROGRESS.
    """
    existing = api.get("/api/v1/exports")["items"]
    if existing:
        job_id = existing[0]["jobId"]
        print(f"  export: da co {len(existing)} job, bo qua (job dau: {existing[0]['status']})")
        return job_id, existing[0]["status"]
    job = api.post("/api/v1/exports", {"catId": cat_ids["A"], "rangePreset": "90D",
                                       "sections": ["TREND", "SCAN_LOG", "NOTES", "PROFILE"],
                                       "locale": "vi", "timezone": "Asia/Ho_Chi_Minh"})
    job_id = job["jobId"]
    status = job["status"]
    # Sinh PDF chay o thread nen (p13 §13.6.1) nen phai poll; 15s la du rong cho may dev.
    for _ in range(15):
        status = api.get(f"/api/v1/exports/{job_id}")["status"]
        if status not in ("QUEUED", "RUNNING"):
            break
        time.sleep(1)
    print(f"  export: tao job {job_id} -> {status}")
    if status in ("QUEUED", "RUNNING"):
        print("  export: ⚠ job van QUEUED sau 15s — xem log backend "
              "('export_job ... bien mat truoc khi xu ly'): ExportRequestService dat job vao "
              "executor BEN TRONG @Transactional nen worker chay truoc khi COMMIT. Loi co san "
              "cua module export, khong thuoc duong nhap scan thu cong.")
    return job_id, status


# ===========================================================================
# Preflight + main
# ===========================================================================

def require_local_host(base: str) -> None:
    host = re.sub(r"^https?://", "", base).split("/")[0].split(":")[0]
    if host not in ("localhost", "127.0.0.1", "::1", "[::1]"):
        sys.exit(f"TU CHOI CHAY: {base} khong phai localhost. Script nay chi danh cho may dev.")


def require_local_profile(api: Api) -> None:
    """Chan chay nham vao mot moi truong khong phai local/dev.

    `/actuator/env` CAN phien dang nhap (SecurityConfig: anyRequest().authenticated()) nen
    buoc nay phai goi SAU khi dang nhap, va chi duoc mo o profile local
    (application-local.yml mo rong `management.endpoints.web.exposure.include`); staging/prod
    chi co health,info nen se 404 va script dung lai.
    """
    try:
        env = api.get("/actuator/env/spring.profiles.active")
    except (ApiError, RuntimeError) as err:
        sys.exit("TU CHOI CHAY: khong doc duoc /actuator/env/spring.profiles.active "
                 f"({err}). Endpoint nay chi mo o profile local (application-local.yml) — "
                 "khong xac dinh duoc profile thi khong seed.")
    text = json.dumps(env)
    if "local" not in text and "dev" not in text:
        sys.exit(f"TU CHOI CHAY: profile dang chay khong phai local/dev ({text[:200]}).")


def require_dev_endpoint(api: Api) -> None:
    """Endpoint dev-only phai dang bat. 404 = chua bat (hoac dang o prod)."""
    try:
        api.post("/api/v1/dev/scans", {}, {"Idempotency-Key": "seed-demo-probe"})
    except ApiError as err:
        if err.status == 404:
            sys.exit(
                "TU CHOI CHAY: POST /api/v1/dev/scans khong ton tai (mac dinh TAT).\n"
                "  Khoi dong lai backend voi co bat:\n"
                "    cd backend && CATCHECK_DEV_MANUAL_SCAN_ENABLED=true ./run-local.sh")
        if err.status == 401:
            sys.exit("TU CHOI CHAY: endpoint dev tra 401 — phien dang nhap khong duoc thiet lap.")
        # 400 (body rong khong hop le) la dau hieu endpoint CO TON TAI va da qua xac thuc.


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed du lieu demo CatCheck (chi may dev).")
    parser.add_argument("--base", default=os.environ.get("CATCHECK_API", "http://localhost:8080"))
    parser.add_argument("--db-host", default=os.environ.get("PGHOST", "127.0.0.1"))
    parser.add_argument("--db-port", default=os.environ.get("PGPORT", "5432"))
    parser.add_argument("--db-name", default=os.environ.get("PGDATABASE", "catcheck"))
    parser.add_argument("--db-user", default=os.environ.get("PGUSER", "catcheck"))
    parser.add_argument("--db-password",
                        default=os.environ.get("PGPASSWORD", "catcheck_local_only"))
    args = parser.parse_args()

    env_file = read_env_file(REPO_ROOT / ".env")
    otp_pepper = os.environ.get("OTP_PEPPER") or env_file.get("OTP_PEPPER", "")
    activation_pepper = (os.environ.get("ACTIVATION_PEPPER")
                         or env_file.get("ACTIVATION_PEPPER", ""))
    if not otp_pepper or not activation_pepper:
        sys.exit(f"Thieu OTP_PEPPER / ACTIVATION_PEPPER (doc tu {REPO_ROOT / '.env'}).")

    api = Api(args.base)
    db = Psql(args.db_host, args.db_port, args.db_name, args.db_user, args.db_password)

    print(f"CatCheck seed demo -> {args.base}")
    require_local_host(args.base)

    print("[1/8] tai khoan")
    session = ensure_account(api, db, otp_pepper)
    print(f"      userId = {session['user']['id']}")

    print("[2/8] kiem tra profile + endpoint dev-only")
    require_local_profile(api)
    require_dev_endpoint(api)

    print("[3/8] ho so meo")
    cat_ids = ensure_cats(api)

    print("[4/8] credit")
    ensure_credits(api, db, activation_pepper)

    print("[5/8] scan")
    now = datetime.now(timezone.utc).replace(microsecond=0)
    scans = ensure_scans(api, db, activation_pepper, cat_ids, now)

    print("[6/8] dispute + doi meo")
    ensure_disputes(api, scans)
    ensure_reassign(api, scans, cat_ids)

    print("[7/8] nhac nho")
    ensure_reminders(api, cat_ids)

    print("[8/8] export PDF")
    job_id, job_status = ensure_export(api, cat_ids)

    history = api.get("/api/v1/scans?limit=1")
    flags = api.get("/api/v1/health-flags")
    print("\nXong. Trang thai hien tai:")
    print(f"  dang nhap     : {DEMO_EMAIL} / {DEMO_PASSWORD}")
    print(f"  meo           : {cat_ids}")
    print(f"  scan (trang 1): hasMore={history.get('hasMore')}")
    print(f"  health flag   : {len(flags.get('items', []))}")
    print(f"  export job    : {job_id} ({job_status})")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ApiError, RuntimeError) as error:
        sys.exit(f"\nTHAT BAI: {error}")
