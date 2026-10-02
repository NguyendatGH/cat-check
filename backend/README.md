# CatCheck backend

Backend Spring Boot 4.1.1 / JDK 25 cho CatCheck (M0 — khung kiến trúc, chưa có nghiệp vụ thật).
Xem `spec/04-index.md` ở gốc repo trước khi đọc code — bản đồ tra cứu spec theo mốc/module.

## Yêu cầu môi trường

- JDK 25 (Temurin) — dùng qua Docker nếu máy chưa có JDK 25 thật (xem mục "Build bằng Docker").
- Maven 3.9.16 — dùng qua wrapper (`./mvnw`), không cần cài Maven thủ công.
- Docker + Docker Compose (chạy PostgreSQL 18.6 + Mailpit cho profile `local`).

## Chạy local (đúng thứ tự p18 §18.2)

```bash
# ở gốc repo (docker-compose.yml nằm ngoài backend/, do phần khác của dự án quản lý)
docker compose up -d postgres mailpit

# ở backend/
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Ứng dụng lắng nghe ở `http://localhost:8080`. Vài endpoint để xác nhận app đã chạy thật với DB:

- `http://localhost:8080/actuator/health` — health check (bao gồm DB, disk...).
- `http://localhost:8080/swagger-ui.html` — Swagger UI (springdoc-openapi).
- `http://localhost:8080/v3/api-docs` — OpenAPI JSON.
- `http://localhost:8080/api/v1/admin/system-status` — endpoint minh hoạ khung module 4 layer
  (`api -> application -> domain <- infrastructure`) của module `admin`, xác nhận app nói
  chuyện được với PostgreSQL thật (`SELECT 1` qua JdbcTemplate).

Cấu hình `application-local.yml` trỏ vào `localhost:5432` (DB `catcheck`, user/password
`catcheck`/`catcheck`) và Mailpit SMTP giả ở `localhost:1025` — khớp với service `postgres` +
`mailpit` mà `docker compose up -d postgres mailpit` khởi động (cổng expose ra host).

## Build

```bash
./mvnw -DskipTests package
```

**Lưu ý về JDK:** `pom.xml` khai `<maven.compiler.release>25</maven.compiler.release>` theo
đúng BOM đã chốt. Nếu máy chỉ có JDK < 25 (ví dụ JDK 21), lệnh trên sẽ báo lỗi kiểu
`invalid target release: 25` hoặc tương tự — đó là do JDK của máy, KHÔNG phải lỗi trong code.
Cách xác thực đúng là build qua Docker (mục dưới), image `eclipse-temurin:25-jdk-noble` có
JDK 25 thật.

### Build bằng Docker (khuyến nghị để xác thực đúng JDK 25)

```bash
docker build -f backend/Dockerfile backend
```

Image runtime dùng `eclipse-temurin:25-jre-noble` (Ubuntu Noble, glibc) — **không phải alpine**,
vì OpenCV native (bytedeco) cần glibc, chạy trên musl libc (alpine) sẽ SIGSEGV.

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
