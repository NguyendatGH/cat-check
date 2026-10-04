# CatCheck backend

Backend Spring Boot 4.1.1 / JDK 25 cho CatCheck.
Xem `spec/04-index.md` ở gốc repo trước khi đọc code — bản đồ tra cứu spec theo mốc/module.

## Yêu cầu môi trường

- JDK 25 (Temurin).
- Maven 3.9.16 — dùng qua wrapper (`./mvnw`), không cần cài Maven thủ công.
- PostgreSQL 18.x chạy native trên máy; Mailpit là tuỳ chọn nếu muốn xem email local.

## Chạy local (đúng thứ tự p18 §18.2)

Profile `local` không cần build Docker. Tạo database/user một lần bằng tài khoản PostgreSQL
quản trị (nếu máy đã có sẵn thì bỏ qua phần đã tồn tại):

```bash
sudo -u postgres psql -d postgres
CREATE ROLE catcheck LOGIN PASSWORD 'catcheck_local_only';
CREATE DATABASE catcheck OWNER catcheck;
\q
```

Sau đó chạy backend native:

```bash
cd backend
./run-local.sh
```

Ứng dụng lắng nghe ở `http://localhost:8080`. Vài endpoint để xác nhận app đã chạy thật với DB:

- `http://localhost:8080/actuator/health` — health check (bao gồm DB, disk...).
- `http://localhost:8080/swagger-ui.html` — Swagger UI (springdoc-openapi).
- `http://localhost:8080/v3/api-docs` — OpenAPI JSON.
- `http://localhost:8080/api/v1/admin/system-status` — endpoint minh hoạ khung module 4 layer
  (`api -> application -> domain <- infrastructure`) của module `admin`, xác nhận app nói
  chuyện được với PostgreSQL thật (`SELECT 1` qua JdbcTemplate).

Cấu hình `application-local.yml` trỏ vào PostgreSQL native ở `localhost:5432` (DB `catcheck`,
user `catcheck`, password `catcheck_local_only`). Mailpit SMTP ở `localhost:1025` là tuỳ chọn;
nếu không chạy Mailpit, đổi `catcheck.notification.email-sink` sang `file` để email được ghi vào
`backend/target/dev-mail/`.

## Build

```bash
./mvnw -DskipTests package
```

**Lưu ý về JDK:** `pom.xml` khai `<maven.compiler.release>25</maven.compiler.release>` theo
đúng BOM đã chốt. Nếu máy chỉ có JDK < 25 (ví dụ JDK 21), lệnh trên sẽ báo lỗi kiểu
`invalid target release: 25` hoặc tương tự — đó là do JDK của máy, không phải lỗi trong code.

Docker image không thuộc quy trình local của project này. Nếu cần CI/container deployment,
hãy dùng pipeline triển khai riêng; không dùng Docker để xác thực các tính năng local.

## Test

```bash
./mvnw test
```

Bộ test kiến trúc nằm ở `src/test/java/com/catcheck/architecture/`:

- `ModularityTests` — `ApplicationModules.of(CatCheckApplication.class).verify()`: xác thực
  toàn bộ khai báo `@ApplicationModule(allowedDependencies = {...})`/`@NamedInterface` ở mọi
  `package-info.java`.
- `ModuleBoundaryRuleTests`, `WebLayerRuleTests`, `ControllerDependencyRuleTests`,
  `CodingStandardRuleTests` — 18 rule ArchUnit (R1–R18, xem comment/`because(...)` trong từng
  rule để tra đúng số hiệu). Một số rule (R6, R9, R10) chưa có class nào để vi phạm ở M0
  (chưa có entity/OpenCV code/ErasureParticipant impl thật) — **PASS vì rỗng là đúng thiết kế,
  không phải bug.**
- `MigrationNamingTests` — kiểm tra tên file trong `db/migration` khớp
  `^V\d+(\.\d+)?__[a-z0-9_]+\.sql$` và không trùng version (R17).

## Cấu trúc module (Spring Modulith)

15 module hoạt động (`shared` kernel + 14 module nghiệp vụ/nền tảng) + 3 khung rỗng Phase 2/3
(`community`, `place`, `shop`). Mỗi module nghiệp vụ có 4 layer:
`api -> application -> domain <- infrastructure`. Xem bảng `allowedDependencies` đầy đủ trong
`package-info.java` của từng module, hoặc tra `spec/parts/p7-backend-architecture.md`.

## 4 profile Spring

| Profile   | Dùng khi                          | Nguồn cấu hình nhạy cảm            |
|-----------|-------------------------------------|-------------------------------------|
| `local`   | Chạy dev trên máy                   | Giá trị không nhạy cảm để thẳng yml |
| `test`    | Test tích hợp (Testcontainers)      | Testcontainers cấp động (M1+)       |
| `staging` | Môi trường staging trên VPS         | Biến môi trường                     |
| `prod`    | Production                          | CHỈ biến môi trường, không secret trong image |

## Ghi chú quan trọng (đọc trước khi sửa migration)

`V4.1__shedlock.sql` KHÔNG khớp literal string "V4b" mà `spec/parts/p4-domain-model-erd.md`
§4.9.2 dùng — xem comment đầu file đó để biết lý do bắt buộc (Flyway không chấp nhận chữ cái ở
phần version). Đã đề nghị owner cập nhật spec.
