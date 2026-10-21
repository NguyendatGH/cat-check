# Thanh toán PayOS + quản lý đơn hàng — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Checkout có 2 lựa chọn, COD và PayOS (QR VietQR). Đơn PayOS chỉ chuyển sang `PAID` khi webhook có chữ ký hợp lệ báo tiền đã vào, và tự huỷ (trả lại tồn kho) nếu sau 5 phút chưa thanh toán. Admin/seller xem và chuyển trạng thái đơn; người mua có danh sách "Đơn của tôi" và huỷ được đơn chưa thanh toán.

**Architecture:** Mọi thứ nằm trong module `shop` (Spring Modulith, chỉ phụ thuộc `shared` và `audit::api`). PayOS SDK nằm sau port `PaymentGateway` (`shop/domain/port`); adapter thật ở `shop/infrastructure/payment`, adapter "tắt" khi thiếu khoá. Tạo link PayOS nằm **trong** transaction checkout: PayOS lỗi thì rollback toàn bộ, đơn, tồn kho và giỏ hàng giữ nguyên. Một job ShedLock chạy mỗi phút để huỷ đơn PayOS quá hạn (thực tế huỷ sau 5–6 phút). Trước khi huỷ, job hỏi PayOS xem đơn đã được thanh toán chưa. Đánh đổi đã chấp nhận: trong lúc gọi PayOS (timeout 10 giây, không retry), các dòng giỏ hàng và sản phẩm của đơn đang bị khoá, nên checkout cùng sản phẩm có thể phải chờ tối đa ~10 giây.

**Tech Stack:** Spring Boot 4.1.1 / JDK 25, JdbcTemplate, Flyway, ShedLock (`JobRunner`), Testcontainers (`TestPostgres.IMAGE`), `vn.payos:payos-java:2.0.1`; React 19 + TanStack Query 5 + i18next, antd 6 (admin).

**Spec:** Quyết định của owner trong hội thoại ngày 2026-10-19 (không có spec file):
1. Giữ COD.
2. Gộp MoMo và chuyển khoản thành "Thanh toán qua PayOS".
3. Gói định kỳ không phải gói credit. `SUBSCRIPTION-3M` là combo hàng vật lý, trả một lần; shop **không** cấp credit (credit vẫn đi qua mã kích hoạt).
4. Đơn chưa thanh toán tự huỷ sau 5 phút.
5. Được tạo migration.
6. Dùng SDK PayOS mới nhất (Maven Central 2025-10-10: `2.0.1`).

## Global Constraints

- SDK: `vn.payos:payos-java:2.0.1`. Chỉ được import `vn.payos..` trong package `com.catcheck.shop.infrastructure.payment` (ArchUnit, Task 4).
- SDK kéo theo `okhttp:4.12.0` (+ Kotlin stdlib) và `jackson-databind:2.20.0`. Backend dùng Jackson 3 (`tools.jackson`), nên không truyền object Jackson giữa hai bên. Webhook đưa **chuỗi JSON thô** cho `webhooks().verify(String)`, vì SDK tự parse bằng Jackson 2 của nó.
- Đơn mới chỉ nhận `paymentMethod` là `COD` hoặc `PAYOS`. `MOMO`, `BANK_TRANSFER` vẫn hợp lệ trong DB và vẫn hiển thị được cho đơn cũ.
- PayOS: thời hạn thanh toán `catcheck.shop.payos.payment-ttl: PT5M`, gửi cả vào `expiredAt` của link (unix giây). **COD không bao giờ tự huỷ.**
- Một đơn chỉ thành `PAID` qua webhook đã xác thực hoặc qua `PaymentGateway.isPaid(...)`. **Không bao giờ** dựa vào việc người mua quay về `returnUrl`.
- Mã đơn PayOS (`orderCode`, kiểu `long`) lấy từ sequence `shop_order_payos_code_seq` bắt đầu từ `100000`. Mô tả chuyển khoản = `"CC" + orderCode`, tối đa 9 ký tự. Giới hạn 9 ký tự cho tài khoản chưa liên kết là theo trí nhớ, phải đối chiếu docs PayOS ở Task 4.
- Mỗi người mua tối đa **3** đơn `PENDING_PAYMENT` cùng lúc (`catcheck.shop.max-open-orders: 3`).
- Migration duy nhất: `V36__shop_payos_payment.sql`. Phải sửa cùng lúc `MigrationNamingTests` (thêm V36 vào danh mục) và `SchemaInvariantTests` (số migration áp dụng `42` → `43`; `EXPECTED_TABLE_COUNT` giữ `79`). Lần merge V28 trước đây đã quên bước này.
- Không ghi PII vào log (CLAUDE.md): chỉ log `orderCode`/`orderId`, không log tên, số điện thoại hay địa chỉ.
- Webhook: `POST /api/v1/payments/payos/webhook`, công khai và miễn CSRF (thêm vào `PUBLIC_INGEST_PATHS` trong `shared/config/SecurityConfig.java`).
- Test tích hợp mới dùng Testcontainers `TestPostgres.IMAGE`, **không** dùng `localhost:5432` như `JdbcShopRepositoryCheckoutRaceIntegrationTest`.
- Chuỗi giao diện: thêm vào cả `frontend/src/shared/i18n/locales/{vi,en}/shop.json` (và `admin.json`, `common` nav nếu cần). Lỗi backend: `backend/src/main/resources/messages/shop_{vi,en}.properties`.
- Commit **không** thêm dòng `Co-Authored-By`/`Claude-Session` (owner yêu cầu ngày 2026-10-19).

## Review Focus

1. **Webhook gửi trùng hoặc gửi song song cho cùng một `orderCode`:** đơn chỉ chuyển `PAID` một lần, lần sau không lỗi. Test ở Task 3 (`markPaidIsIdempotent`) và Task 6 (2 lần gọi liên tiếp).
2. **Tiền vào sau khi đơn đã bị huỷ vì quá hạn:** đơn giữ `CANCELLED`, ghi `paid_at`, `payment_reference` và `refund_needed = true`. Admin thấy cờ "Cần hoàn tiền". Test ở Task 3 (`markPaidAfterCancelFlagsRefund`) và Task 10 (nhãn "Cần hoàn tiền").
3. **Người mua trả đúng lúc hết hạn:** job hỏi `isPaid` trước khi huỷ. Đã trả thì đánh dấu `PAID`, không huỷ. Test ở Task 7 (`paidJustBeforeExpiryIsMarkedPaidNotCancelled`).
4. **PayOS lỗi hoặc timeout khi checkout:** không tạo đơn, tồn kho và giỏ hàng giữ nguyên, trả `503 shop/payment-unavailable`. Frontend gợi ý chọn COD. Test ở Task 5 (`gatewayFailureRollsBackOrderStockAndCart`).
5. **Body webhook sai chữ ký hoặc số tiền lệch:** sai chữ ký → `400`, không đổi gì. Lệch số tiền → không đánh dấu `PAID`, trả `200` để PayOS không gửi lại mãi, ghi log `orderCode`. Webhook của `orderCode` lạ (PayOS gửi thử lúc xác nhận URL) → `200`. Test ở Task 6.

---

## File map

**Backend** (`backend/src/main/java/com/catcheck/shop/…`)

| File | Trách nhiệm |
|---|---|
| `resources/db/migration/V36__shop_payos_payment.sql` | Cột thanh toán, sequence, index, ràng buộc phương thức |
| `domain/Order.java` (sửa) | Thêm trường thanh toán/huỷ |
| `domain/OrderStatusPolicy.java` (mới) | Luật chuyển trạng thái (thuần, không Spring) |
| `domain/PaymentLink.java`, `VerifiedPayment.java`, `MarkPaidResult.java`, `ExpiredPayment.java`, `InvalidWebhookException.java` (mới) | Kiểu dữ liệu của port và repository |
| `domain/port/PaymentGateway.java` (mới) | Port gọi cổng thanh toán |
| `domain/port/ShopRepository.java` (sửa) | Thêm hàm đơn hàng |
| `infrastructure/persistence/JdbcShopRepository.java` (sửa) | SQL cho các hàm trên |
| `infrastructure/payment/PayOsPaymentGateway.java`, `DisabledPaymentGateway.java`, `PaymentGatewayConfiguration.java` (mới) | Adapter SDK + chọn adapter theo cấu hình |
| `application/ShopService.java` (sửa) | Checkout PayOS, đơn của tôi, người mua huỷ |
| `application/PaymentWebhookService.java` (mới) | Xử lý webhook |
| `application/AdminOrderService.java` (mới) | Admin liệt kê và chuyển trạng thái, có audit |
| `application/ShopPaymentExpiryJob.java` (mới) | Job huỷ đơn PayOS quá hạn |
| `api/ShopController.java`, `api/dto/*` (sửa/mới) | Endpoint người mua |
| `api/PayOsWebhookController.java` (mới) | Endpoint webhook |
| `api/AdminOrderController.java` (mới) | Endpoint admin |
| `api/ShopErrorCode.java` (sửa) + `messages/shop_{vi,en}.properties` | Mã lỗi mới |

**Frontend** (`frontend/src/…`): `pages/shop/shopFormat.ts`, `CartPage.tsx`, `CheckoutPage.tsx`, `OrderTrackingPage.tsx`, `OrdersPage.tsx` (mới); `features/shop/{api,hooks,index}.ts`; `pages/admin/AdminOrdersPage.tsx` (mới); `features/admin/{api,hooks,index}.ts`; `app/router/{routes,lazy,router}.ts(x)`; `app/layouts/{AdminLayout,SellerLayout}.tsx`; locale `shop.json`, `admin.json`.

**Vận hành:** `backend/pom.xml`, `application.yml`, `.env.prod.example`, `docker-compose.prod.yml`, `docs/DEPLOYMENT.md`.

---

### Task 1: Migration V36 + cập nhật test migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V36__shop_payos_payment.sql`
- Modify: `backend/src/test/java/com/catcheck/architecture/MigrationNamingTests.java:83` (thêm `"V36__shop_payos_payment.sql"` sau V35)
- Modify: `backend/src/test/java/com/catcheck/architecture/SchemaInvariantTests.java:48` (`42` → `43`)
- Test: `backend/src/test/java/com/catcheck/shop/infrastructure/persistence/ShopPayosSchemaIntegrationTest.java` (mới)

**Interfaces:**
- Produces: các cột trên `shop_order`: `payos_order_code BIGINT UNIQUE`, `payos_payment_link_id VARCHAR(64)`, `payos_checkout_url TEXT`, `payment_expires_at TIMESTAMPTZ`, `paid_at TIMESTAMPTZ`, `payment_reference VARCHAR(64)`, `cancelled_at TIMESTAMPTZ`, `cancel_reason VARCHAR(24)`, `refund_needed BOOLEAN NOT NULL DEFAULT false`; sequence `shop_order_payos_code_seq` (`START 100000`).

- [ ] **Step 1: Viết test schema thất bại** `ShopPayosSchemaIntegrationTest` (Testcontainers `TestPostgres.IMAGE`, Flyway migrate):
  - `payosOrderRequiresOrderCode`: insert `payment_method='PAYOS'` với `payos_order_code` NULL → ném `DataIntegrityViolationException`.
  - `cancelReasonIsRestricted`: `cancel_reason='FOO'` → vi phạm CHECK; `PAYMENT_EXPIRED`, `BUYER`, `ADMIN` hợp lệ.
  - `legacyMethodsStillAllowed`: insert `MOMO` và `BANK_TRANSFER` thành công; `VNPAY` bị từ chối.
  - `sequenceStartsAt100000`: `SELECT nextval('shop_order_payos_code_seq')` = `100000`.
- [ ] **Step 2: Chạy, xác nhận FAIL** — `./mvnw -B test -Dtest=ShopPayosSchemaIntegrationTest` → lỗi cột/sequence không tồn tại.
- [ ] **Step 3: Viết `V36__shop_payos_payment.sql`.** Thêm các cột trên. Drop rồi tạo lại `ck_shop_order_payment` với `('COD','PAYOS','MOMO','BANK_TRANSFER')`. Thêm các CHECK: `ck_shop_order_payos_code` (`payment_method <> 'PAYOS' OR payos_order_code IS NOT NULL`) và `ck_shop_order_cancel_reason` (`cancel_reason IS NULL OR cancel_reason IN ('PAYMENT_EXPIRED','BUYER','ADMIN')`). Tạo `CREATE SEQUENCE shop_order_payos_code_seq START 100000`. Thêm index `idx_shop_order_payos_expiry ON shop_order (payment_expires_at) WHERE status = 'PENDING_PAYMENT' AND payment_method = 'PAYOS'` và `idx_shop_order_status_created ON shop_order (status, created_at DESC)`. Grant theo mẫu khối `DO $$ … catcheck_app …` ở V20, thêm `GRANT USAGE ON SEQUENCE shop_order_payos_code_seq`. Comment đầu file theo phong cách V31/V32.
- [ ] **Step 4: Sửa 2 test migration** (V36 vào danh mục, `42` → `43`).
- [ ] **Step 5: Chạy, xác nhận PASS** — `./mvnw -B test -Dtest='ShopPayosSchemaIntegrationTest,MigrationNamingTests,SchemaInvariantTests'`.
- [ ] **Step 6: Commit** — `git commit -m "feat(shop): V36 payment columns for PayOS orders"`.

### Task 2: Luật trạng thái đơn và các kiểu dữ liệu domain

**Files:**
- Create: `shop/domain/OrderStatusPolicy.java`, `PaymentLink.java`, `VerifiedPayment.java`, `MarkPaidResult.java`, `ExpiredPayment.java`, `InvalidWebhookException.java`
- Modify: `shop/domain/Order.java`
- Test: `backend/src/test/java/com/catcheck/shop/domain/OrderStatusPolicyTest.java`

**Interfaces:**
- Produces:
  - `record Order(UUID id, String orderCode, String status, String paymentMethod, String receiverName, String receiverPhone, String shippingAddress, long subtotalVnd, long discountVnd, long shippingFeeVnd, long totalVnd, List<CartLine> lines, Instant createdAt, Long payosOrderCode, String checkoutUrl, Instant paymentExpiresAt, Instant paidAt, String paymentReference, String cancelReason, boolean refundNeeded)`
  - `record PaymentLink(String paymentLinkId, String checkoutUrl)`
  - `record VerifiedPayment(long payosOrderCode, long amountVnd, String reference, boolean success)` (`success` = `code == "00"`)
  - `enum MarkPaidResult { PAID, ALREADY_PAID, PAID_AFTER_CANCEL, AMOUNT_MISMATCH, UNKNOWN_ORDER }`
  - `record ExpiredPayment(UUID orderId, long payosOrderCode, long totalVnd)`
  - `class InvalidWebhookException extends RuntimeException`
  - `final class OrderStatusPolicy`: `static List<String> adminNextStatuses(String paymentMethod, String status)`, `static boolean buyerCanCancel(String status)`

- [ ] **Step 1: Viết test thất bại** `OrderStatusPolicyTest`:
  - `codFlow`: `adminNextStatuses("COD","PENDING_PAYMENT")` = `[PACKING, CANCELLED]`; `("COD","PACKING")` = `[SHIPPING, CANCELLED]`; `("COD","SHIPPING")` = `[DELIVERED]`; `("COD","DELIVERED")` = `[]`.
  - `payosCannotBeMarkedPaidByAdmin`: `("PAYOS","PENDING_PAYMENT")` = `[CANCELLED]`; `("PAYOS","PAID")` = `[PACKING, CANCELLED]`.
  - `cancelledIsTerminal`: mọi phương thức, `("…","CANCELLED")` = `[]`.
  - `buyerCancel`: `buyerCanCancel("PENDING_PAYMENT")` = true; `PAID`, `PACKING` = false.
- [ ] **Step 2: Chạy, xác nhận FAIL** (`OrderStatusPolicy` chưa tồn tại).
- [ ] **Step 3: Viết các kiểu và `OrderStatusPolicy`** đúng các bảng trên. Sửa `Order` và mọi chỗ tạo `Order` (`JdbcShopRepository.mapOrder`, `OrderResponse.from`) để khớp chữ ký mới (trường mới tạm đọc NULL/false; Task 3 đọc thật).
- [ ] **Step 4: Chạy `./mvnw -B test -Dtest='OrderStatusPolicyTest,ShopControllerTest'`** → PASS.
- [ ] **Step 5: Commit** — `feat(shop): order status policy and payment domain types`.

### Task 3: Repository: checkout có hạn mức, đánh dấu đã trả, huỷ và trả tồn kho, truy vấn đơn

**Files:**
- Modify: `shop/domain/port/ShopRepository.java`, `shop/infrastructure/persistence/JdbcShopRepository.java`
- Test: `backend/src/test/java/com/catcheck/shop/infrastructure/persistence/JdbcShopRepositoryOrderIntegrationTest.java` (mới, Testcontainers)

**Interfaces:**
- Consumes: Task 1 (cột), Task 2 (kiểu).
- Produces (trong `ShopRepository`):
  - `Order createOrder(UUID userId, String paymentMethod, String receiverName, String receiverPhone, String address, Instant paymentExpiresAt, int maxOpenOrders)`: thay chữ ký cũ. Với `PAYOS` thì gán `payos_order_code = nextval(...)`. Khi số đơn `PENDING_PAYMENT` của user ≥ `maxOpenOrders` thì ném `ConflictException(ShopErrorCode.ORDER_LIMIT_REACHED)`.
  - `void attachPaymentLink(UUID orderId, String paymentLinkId, String checkoutUrl)`
  - `List<Order> orders(UUID userId, int limit)`: mới nhất trước.
  - `List<Order> adminOrders(String statusOrNull, int limit, int offset)`, `Optional<Order> adminOrder(UUID orderId)`
  - `boolean transition(UUID orderId, String fromStatus, String toStatus)`: `UPDATE … WHERE id=? AND status=?`; trả true nếu cập nhật được 1 dòng.
  - `boolean cancelAndRestock(UUID orderId, String expectedStatus, String cancelReason, Instant at)`: một transaction; khoá đơn `FOR UPDATE`, kiểm tra trạng thái, set `CANCELLED`/`cancelled_at`/`cancel_reason`, cộng lại `stock_quantity` theo `shop_order_line`.
  - `MarkPaidResult markPaid(long payosOrderCode, long amountVnd, String reference, Instant paidAt)`: khoá đơn theo `payos_order_code` `FOR UPDATE`. Không thấy → `UNKNOWN_ORDER`. `amountVnd != total_vnd` → `AMOUNT_MISMATCH`. `PENDING_PAYMENT` → `PAID` + `paid_at`/`payment_reference` → `PAID`. Đã `PAID` hoặc sau đó → `ALREADY_PAID`. `CANCELLED` → ghi `paid_at`/`payment_reference`/`refund_needed=true` → `PAID_AFTER_CANCEL`.
  - `List<ExpiredPayment> expiredPayosOrders(Instant now, int limit)`

- [ ] **Step 1: Viết test thất bại** (mỗi test tự seed sản phẩm có tồn kho 5 và giỏ hàng):
  - `payosCheckoutAssignsOrderCodeAndExpiry`: `createOrder(..., "PAYOS", ..., now+5m, 3)` → `payosOrderCode >= 100000`, `paymentExpiresAt` đúng, tồn kho giảm, giỏ hàng trống.
  - `codCheckoutHasNoOrderCodeOrExpiry`.
  - `fourthOpenOrderIsRejected`: 3 đơn `PENDING_PAYMENT` → đơn thứ 4 ném `ORDER_LIMIT_REACHED`. Sau khi 1 đơn chuyển `PACKING`, đặt tiếp được.
  - `cancelAndRestockReturnsStock`: tồn kho trở lại 5; gọi lần 2 với `expectedStatus=PENDING_PAYMENT` → false, tồn kho vẫn 5.
  - `markPaidIsIdempotent`: lần 1 → `PAID`, lần 2 → `ALREADY_PAID`, `paid_at` không đổi.
  - `markPaidAmountMismatch`: số tiền khác `total_vnd` → `AMOUNT_MISMATCH`, trạng thái vẫn `PENDING_PAYMENT`.
  - `markPaidAfterCancelFlagsRefund`: sau `cancelAndRestock` → `PAID_AFTER_CANCEL`, `refundNeeded=true`, trạng thái vẫn `CANCELLED`.
  - `markPaidUnknownOrder` → `UNKNOWN_ORDER`.
  - `expiredQueryIgnoresCodAndFuture`: chỉ trả đơn PAYOS `PENDING_PAYMENT` có `payment_expires_at < now`.
  - `ordersAreUserScoped`: `orders(userB)` không thấy đơn của userA.
- [ ] **Step 2: Chạy, xác nhận FAIL.**
- [ ] **Step 3: Viết SQL** trong `JdbcShopRepository` (giữ kiểu viết hiện tại). `createOrder` giữ `FOR UPDATE` trên giỏ hàng như cũ; đếm số đơn mở trong cùng transaction, ngay sau khi khoá giỏ. `mapOrder` đọc các cột mới.
- [ ] **Step 4: Chạy, xác nhận PASS** + `JdbcShopRepositoryCheckoutRaceIntegrationTest` vẫn xanh.
- [ ] **Step 5: Commit** — `feat(shop): order persistence for PayOS payment, cancel and restock`.

### Task 4: Port `PaymentGateway` + adapter PayOS SDK

**Files:**
- Modify: `backend/pom.xml` (thêm `vn.payos:payos-java:2.0.1`)
- Create: `shop/domain/port/PaymentGateway.java`, `shop/infrastructure/payment/PayOsPaymentGateway.java`, `DisabledPaymentGateway.java`, `PaymentGatewayConfiguration.java`
- Modify: `backend/src/main/resources/application.yml`, `backend/src/test/java/com/catcheck/architecture/ModuleBoundaryRuleTests.java`
- Test: `backend/src/test/java/com/catcheck/shop/infrastructure/payment/PayOsPaymentGatewayTest.java`

**Interfaces:**
- Produces `interface PaymentGateway`:
  - `boolean enabled()`
  - `PaymentLink createLink(long payosOrderCode, long amountVnd, String description, Instant expiresAt, URI returnUrl, URI cancelUrl)`: dùng `paymentRequests().create(CreatePaymentLinkRequest.builder()…expiredAt(expiresAt.getEpochSecond())…build())`; trả `getPaymentLinkId()`, `getCheckoutUrl()`.
  - `void cancelLink(long payosOrderCode, String reason)`: `paymentRequests().cancel(Long, String)`.
  - `boolean isPaid(long payosOrderCode)`: `paymentRequests().get(Long).getStatus()` là `PAID`.
  - `VerifiedPayment verifyWebhook(String rawBody)`: `webhooks().verify(rawBody)`; mọi `WebhookException`/`IllegalArgumentException` đổi thành `InvalidWebhookException`.
- Cấu hình: `catcheck.shop.payos.client-id: ${PAYOS_CLIENT_ID:}`, `api-key: ${PAYOS_API_KEY:}`, `checksum-key: ${PAYOS_CHECKSUM_KEY:}`, `timeout-ms: 10000`, `max-retries: 0`, `payment-ttl: PT5M`. Đủ 3 khoá → `PayOsPaymentGateway` (dựng bằng `new PayOS(ClientOptions.builder()…build())`); thiếu bất kỳ khoá nào → `DisabledPaymentGateway` (`enabled()=false`, các hàm khác ném `IllegalStateException`). Theo mẫu `notification/infrastructure/push/PushConfiguration`.

- [ ] **Step 1: Kiểm tra phụ thuộc** — thêm dependency rồi chạy `./mvnw -B dependency:tree -Dincludes=com.fasterxml.jackson.core,com.squareup.okhttp3,org.jetbrains.kotlin`. Ghi lại version thực tế (Boot BOM có thể ép version Jackson 2). Rồi chạy `./mvnw -B test -Dtest=ApplicationContextSmokeTest` → context vẫn khởi động được.
- [ ] **Step 2: Viết test thất bại** `PayOsPaymentGatewayTest` (không gọi mạng; dựng `PayOS` với khoá giả `checksum = "test-checksum-key"`):
  - `validWebhookIsVerified`: tạo `WebhookData` (orderCode `100123`, amount `245000`, code `"00"`, reference `"FT123"`), ký bằng `client.getCrypto().createSignatureFromObj(data, checksum)`, ghép JSON `{code,desc,success,data,signature}` → `verifyWebhook(json)` trả `VerifiedPayment(100123, 245000, "FT123", true)`.
  - `tamperedAmountIsRejected`: sửa `amount` sau khi ký → ném `InvalidWebhookException`.
  - `nonSuccessCodeIsNotSuccess`: code `"01"` (ký hợp lệ) → `success == false`.
  - `missingKeysGiveDisabledGateway`: cấu hình thiếu `checksum-key` → bean là `DisabledPaymentGateway`, `enabled()==false`.
- [ ] **Step 3: Viết ArchUnit rule** trong `ModuleBoundaryRuleTests`: class ngoài `com.catcheck.shop.infrastructure.payment..` không được phụ thuộc `vn.payos..` (đặt mã rule tiếp theo theo dãy R hiện có).
- [ ] **Step 4: Chạy, xác nhận FAIL**, viết adapter + configuration, chạy lại → PASS (gồm `ModuleBoundaryRuleTests`, `ModularityTests`).
- [ ] **Step 5: Đối chiếu docs PayOS** (payos.vn/docs): giới hạn độ dài `description`, đơn vị `expiredAt`, khoảng thời gian tối thiểu của `expiredAt`. Ghi kết quả vào comment một dòng ở `createLink`. Nếu 9 ký tự sai thì sửa hằng mô tả ở Task 5.
- [ ] **Step 6: Commit** — `feat(shop): PayOS payment gateway adapter (payos-java 2.0.1)`.

### Task 5: Checkout PayOS + "Đơn của tôi" + người mua huỷ đơn

**Files:**
- Modify: `shop/application/ShopService.java`, `shop/api/ShopController.java`, `shop/api/dto/CheckoutRequest.java` (pattern `(?i)COD|PAYOS`), `shop/api/dto/OrderResponse.java`, `shop/api/ShopErrorCode.java`, `messages/shop_{vi,en}.properties`, `application.yml` (`catcheck.shop.web-base-url: ${CATCHECK_SHOP_WEBBASEURL:http://localhost:5173}`, `catcheck.shop.max-open-orders: 3`)
- Test: `backend/src/test/java/com/catcheck/shop/application/ShopServicePayosTest.java` (Testcontainers + `PaymentGateway` giả), sửa `ShopControllerTest.java`

**Interfaces:**
- Consumes: Task 3 (`createOrder`, `attachPaymentLink`, `orders`, `cancelAndRestock`), Task 4 (`PaymentGateway`).
- Produces:
  - `ShopService.checkout(UUID userId, String paymentMethod, String name, String phone, String address)`: `@Transactional`. Với `PAYOS`: gateway tắt → `PAYMENT_UNAVAILABLE` trước khi ghi gì. Ngược lại `createOrder(..., now + ttl, maxOpenOrders)` → `createLink(code, total, "CC" + code, expiresAt, {webBaseUrl}/orders/{id}?payment=return, {webBaseUrl}/orders/{id}?payment=cancel)` → `attachPaymentLink`. Gateway ném lỗi → bọc thành `BusinessRuleException(PAYMENT_UNAVAILABLE)` để transaction rollback.
  - `List<Order> myOrders(UUID userId)` (limit 50); `Order cancelByBuyer(UUID userId, UUID orderId)`: không phải đơn của user → `ORDER_NOT_FOUND`; `!buyerCanCancel` → `ORDER_NOT_CANCELLABLE`; PAYOS thì gọi `cancelLink` (lỗi chỉ ghi log), rồi `cancelAndRestock(orderId, "PENDING_PAYMENT", "BUYER", now)`.
  - Endpoint: `GET /api/v1/orders` → `List<OrderResponse>`; `POST /api/v1/orders/{orderId}/cancel` → `OrderResponse`.
  - `OrderResponse` thêm `checkoutUrl` (chỉ khi còn `PENDING_PAYMENT`), `paymentExpiresAt`, `paidAt`, `cancelReason`.
  - `ShopErrorCode`: `PAYMENT_UNAVAILABLE("shop/payment-unavailable", 503)`, `ORDER_LIMIT_REACHED("shop/order-limit-reached", 409)`, `ORDER_NOT_CANCELLABLE("shop/order-not-cancellable", 409)`, `ORDER_TRANSITION_INVALID("shop/order-transition-invalid", 409)`, `CANCEL_REASON_REQUIRED("shop/cancel-reason-required", 400)`.

- [ ] **Step 1: Viết test thất bại** `ShopServicePayosTest`:
  - `payosCheckoutCreatesLinkWithFiveMinuteExpiry`: gateway giả ghi lại tham số → `amountVnd == totalVnd`, `description == "CC" + code`, `expiresAt == now + 5m` (dùng `Clock` cố định), returnUrl kết thúc bằng `/orders/{id}?payment=return`; đơn có `checkoutUrl`.
  - `gatewayFailureRollsBackOrderStockAndCart`: gateway ném lỗi → `PAYMENT_UNAVAILABLE`; không có dòng `shop_order` mới, tồn kho và giỏ hàng như trước.
  - `disabledGatewayRejectsPayosButCodWorks`.
  - `buyerCancelRestocksAndCancelsLink`; `buyerCannotCancelPaidOrder` → `ORDER_NOT_CANCELLABLE`; `buyerCannotCancelSomeoneElsesOrder` → `ORDER_NOT_FOUND`.
  - `ShopControllerTest`: `paymentMethod="MOMO"` → 400; `GET /orders` trả danh sách.
- [ ] **Step 2: Chạy, xác nhận FAIL.**
- [ ] **Step 3: Viết code + chuỗi lỗi vi/en** (`MessageBundleParityTest` phải xanh).
- [ ] **Step 4: Chạy `./mvnw -B test -Dtest='ShopServicePayosTest,ShopControllerTest,MessageBundleParityTest'`** → PASS.
- [ ] **Step 5: Commit** — `feat(shop): PayOS checkout, my orders and buyer cancel`.

### Task 6: Webhook PayOS

**Files:**
- Create: `shop/application/PaymentWebhookService.java`, `shop/api/PayOsWebhookController.java`
- Modify: `shared/config/SecurityConfig.java` (thêm `/api/v1/payments/payos/webhook` vào `PUBLIC_INGEST_PATHS`)
- Test: `backend/src/test/java/com/catcheck/shop/api/PayOsWebhookControllerTest.java`

**Interfaces:**
- Consumes: Task 3 (`markPaid`), Task 4 (`verifyWebhook`).
- Produces: `PaymentWebhookService.handle(String rawBody): MarkPaidResult` (trả `null` khi `success == false`). Controller: `POST /api/v1/payments/payos/webhook`, nhận `@RequestBody String`. `InvalidWebhookException` → `400`; mọi kết quả khác → `200` `{"success":true}`. Log WARN cho `AMOUNT_MISMATCH`, `PAID_AFTER_CANCEL`, `UNKNOWN_ORDER`, chỉ kèm `orderCode`.

- [ ] **Step 1: Viết test thất bại** (MockMvc, gateway giả):
  - `invalidSignatureIs400AndChangesNothing`.
  - `paidWebhookMarksOrderPaid` → 200, đơn `PAID`.
  - `duplicateWebhookIsHarmless`: gọi 2 lần → cả hai 200, `paid_at` không đổi.
  - `unknownOrderCodeIs200` (PayOS gửi thử khi xác nhận URL).
  - `amountMismatchIs200ButNotPaid`.
  - `webhookNeedsNoSessionOrCsrf`: không cookie, không header CSRF → không 401/403.
- [ ] **Step 2: Chạy, xác nhận FAIL** → viết code → chạy lại → PASS.
- [ ] **Step 3: Commit** — `feat(shop): verified PayOS webhook marks orders paid`.

### Task 7: Job huỷ đơn PayOS quá 5 phút

**Files:**
- Create: `shop/application/ShopPaymentExpiryJob.java`
- Modify: `application.yml` (`catcheck.jobs.shop-payment-expiry: { enabled: true, cron: "0 * * * * *", lock-at-most-for: PT50S, batch-size: 100, dry-run: false }`)
- Test: `backend/src/test/java/com/catcheck/shop/application/ShopPaymentExpiryJobTest.java`

**Interfaces:**
- Consumes: Task 3 (`expiredPayosOrders`, `markPaid`, `cancelAndRestock`), Task 4 (`isPaid`, `cancelLink`).
- Produces: `ShopPaymentExpiryJob.JOB_NAME = "ShopPaymentExpiryJob"`. Theo đúng mẫu `export/application/ReportPdfTimeoutJob.java` (`@Scheduled` + `@SchedulerLock` + `jobRunner.runScheduled(JOB_NAME, dryRun, …)` trả `JobOutcome`). Với mỗi `ExpiredPayment`: `isPaid` true → `markPaid(p.payosOrderCode(), p.totalVnd(), "late-check", now)` (`payment_reference = "late-check"` để admin biết đơn được xác nhận qua job, không qua webhook); ngược lại `cancelLink` (lỗi chỉ ghi log) rồi `cancelAndRestock(id, "PENDING_PAYMENT", "PAYMENT_EXPIRED", now)`. Một đơn lỗi không chặn các đơn khác.

- [ ] **Step 1: Viết test thất bại:**
  - `expiredUnpaidOrderIsCancelledAndRestocked`.
  - `paidJustBeforeExpiryIsMarkedPaidNotCancelled` (`isPaid` → true).
  - `codOrdersAreNeverTouched` (đơn COD cũ hơn 5 phút giữ `PENDING_PAYMENT`).
  - `gatewayErrorOnOneOrderDoesNotStopOthers`.
  - `dryRunChangesNothing`.
- [ ] **Step 2: Chạy, xác nhận FAIL** → viết code → PASS.
- [ ] **Step 3: Commit** — `feat(shop): cancel unpaid PayOS orders after 5 minutes`.

### Task 8: API admin cho đơn hàng (có audit)

**Files:**
- Create: `shop/application/AdminOrderService.java`, `shop/api/AdminOrderController.java`, `shop/api/dto/AdminOrderResponse.java`, `shop/api/dto/AdminOrderStatusRequest.java`
- Test: `backend/src/test/java/com/catcheck/shop/api/AdminOrderControllerTest.java`

**Interfaces:**
- Consumes: Task 2 (`OrderStatusPolicy`), Task 3 (`adminOrders`, `adminOrder`, `transition`, `cancelAndRestock`).
- Produces:
  - `GET /api/v1/admin/shop/orders?status=&page=` (20/trang) → `List<AdminOrderResponse>`; `GET /api/v1/admin/shop/orders/{orderId}`.
  - `POST /api/v1/admin/shop/orders/{orderId}/status`, body `AdminOrderStatusRequest(String status, String reason)`. `CANCELLED` bắt buộc `reason` ≥ 5 ký tự (`CANCEL_REASON_REQUIRED`). Trạng thái không có trong `adminNextStatuses` → `ORDER_TRANSITION_INVALID`.
  - `AdminOrderResponse`: các trường của `Order` (trừ `checkoutUrl`) + `allowedNextStatuses`.
  - Quyền: `Set.of("ADMIN_SUPER", "ADMIN_CATALOG")`, giống `AdminShopController.ROLES`. Audit: action `SHOP_ORDER_STATUS_CHANGED` / `SHOP_ORDER_CANCELLED`, meta `orderId`, `from`, `to` (không meta PII). Theo mẫu `AdminShopService.audit(...)`.

- [ ] **Step 1: Viết test thất bại:** `userRoleIsForbidden` (403); `codOrderMovesToPacking`; `payosPendingCannotBeMarkedPaidByAdmin` (`PAID` → 409); `cancelWithoutReasonIs400`; `adminCancelRestocks`; `refundNeededIsExposed`; `auditEventWritten`.
- [ ] **Step 2: Chạy, xác nhận FAIL** → viết code → PASS.
- [ ] **Step 3: Commit** — `feat(shop): admin order list and status transitions`.

### Task 9: Frontend: chọn PayOS, chuyển sang trang thanh toán, trang đơn hàng

**Files:**
- Modify: `frontend/src/pages/shop/shopFormat.ts`, `CartPage.tsx`, `CheckoutPage.tsx`, `OrderTrackingPage.tsx`, `features/shop/{api,hooks,index}.ts`, locale `shop.json` (vi/en)
- Test: `frontend/src/pages/shop/shopFormat.test.ts` (mới), `OrderTrackingPage.test.tsx` (sửa)

**Interfaces:**
- Consumes: Task 5 API (`checkoutUrl`, `paymentExpiresAt`, `POST /orders/{id}/cancel`).
- Produces:
  - `SHOP_PAYMENT_METHOD_IDS = ["COD", "PAYOS"] as const` (để chọn); `SHOP_ORDER_PAYMENT_METHODS = ["COD", "PAYOS", "MOMO", "BANK_TRANSFER"]` (để hiển thị đơn cũ).
  - `nextStepAfterOrder(order: OrderApi): { kind: "redirect"; url: string } | { kind: "order"; path: string }`
  - `OrderApi` thêm `checkoutUrl: string | null`, `paymentExpiresAt: string | null`, `paidAt: string | null`, `cancelReason: string | null`.
  - Hook `useCancelOrder()`; `useShopOrder` có `refetchInterval: 3000` khi `status === "PENDING_PAYMENT" && paymentMethod === "PAYOS"` (dừng khi quá `paymentExpiresAt`).
  - Chuỗi mới (vi): `payment.methods.PAYOS.name` = "Chuyển khoản / quét mã QR (PayOS)"; `.note` = "Quét mã VietQR bằng app ngân hàng hoặc ví điện tử. Đơn tự huỷ nếu chưa thanh toán sau 5 phút."; `order.payAgain` = "Tiếp tục thanh toán"; `order.cancel` = "Huỷ đơn"; `order.expiresIn` = "Còn {{time}} để thanh toán"; `order.cancelReason.PAYMENT_EXPIRED` = "Đơn đã tự huỷ vì quá 5 phút chưa thanh toán"; `errors.shop/payment-unavailable` = "PayOS đang gián đoạn. Bạn có thể chọn thanh toán khi nhận hàng." (+ bản en). Chưa có screen inventory, nên owner duyệt lại câu chữ.

- [ ] **Step 1: Viết test thất bại:**
  - `shopFormat.test.ts`: `nextStepAfterOrder({paymentMethod:"PAYOS", checkoutUrl:"https://pay.payos.vn/web/abc", …})` → `{kind:"redirect", url:"https://pay.payos.vn/web/abc"}`; với COD → `{kind:"order", path:"/orders/<id>"}`; PAYOS nhưng `checkoutUrl` null → `{kind:"order", …}`.
  - `OrderTrackingPage.test.tsx`: đơn PAYOS chờ thanh toán hiện nút "Tiếp tục thanh toán" + đếm ngược; vào `?payment=cancel` tự gọi huỷ đúng **một** lần; đơn `MOMO` cũ vẫn hiện nhãn.
- [ ] **Step 2: Chạy `npx vitest run src/pages/shop`** → FAIL.
- [ ] **Step 3: Viết code.** `CheckoutPage`: `onSuccess` gọi `nextStepAfterOrder`; `redirect` → `window.location.assign(url)`. `CartPage` lặp qua `SHOP_PAYMENT_METHOD_IDS` mới. Lỗi `shop/payment-unavailable` hiện toast kèm gợi ý chọn COD.
- [ ] **Step 4: Chạy `npx vitest run && npm run lint && npm run typecheck`** → PASS.
- [ ] **Step 5: Commit** — `feat(shop): PayOS checkout redirect and live order status`.

### Task 10: Frontend: "Đơn của tôi" + trang quản lý đơn cho admin/seller

**Files:**
- Create: `frontend/src/pages/shop/OrdersPage.tsx`, `frontend/src/pages/admin/AdminOrdersPage.tsx`
- Modify: `app/router/{routes.ts,lazy.ts,router.tsx}` (`orderList: "/orders"`, `adminOrders: "/admin/orders"`, `sellerOrders: "/seller/orders"`), `app/layouts/AdminLayout.tsx` (mục `orders` trong nhóm `catalog`, `roles: ROLES.products`), `app/layouts/SellerLayout.tsx`, `features/admin/{api,hooks,index}.ts`, `pages/shop/ShopPage.tsx` (link "Đơn của tôi"), locale `shop.json`, `admin.json`
- Test: `frontend/src/pages/admin/AdminOrdersPage.test.tsx`, `frontend/src/pages/shop/OrdersPage.test.tsx`

**Interfaces:**
- Consumes: Task 5 (`GET /orders`), Task 8 (API admin).
- Produces: `listAdminOrders(status?: string, page?: number)`, `changeAdminOrderStatus(orderId: string, status: string, reason?: string)` trong `features/admin/api.ts`. `/seller/orders` dùng lại `LazyPages.adminOrders` (giống `/seller/color-chart` dùng lại trang admin).

- [ ] **Step 1: Viết test thất bại:**
  - `AdminOrdersPage.test.tsx`: chỉ hiện nút cho `allowedNextStatuses`; chọn "Huỷ" bắt nhập lý do; đơn `refundNeeded` hiện nhãn "Cần hoàn tiền".
  - `OrdersPage.test.tsx`: danh sách rỗng hiện `EmptyState`; mỗi dòng link tới `/orders/{id}`.
- [ ] **Step 2: Chạy** → FAIL → viết code (antd `Table` như `AdminProductsPage`) → PASS.
- [ ] **Step 3: `npm run lint && npm run typecheck && npx vitest run`** → PASS.
- [ ] **Step 4: Commit** — `feat(shop): my orders page and admin order management`.

### Task 11: Cấu hình triển khai + hướng dẫn PayOS

**Files:**
- Modify: `.env.prod.example` (khối `PAYOS_CLIENT_ID=`, `PAYOS_API_KEY=`, `PAYOS_CHECKSUM_KEY=` kèm comment), `docker-compose.prod.yml` (`CATCHECK_SHOP_WEBBASEURL: https://${DOMAIN}` trong `environment:` của backend), `docs/DEPLOYMENT.md` (mục mới "PayOS"), `docs/VPS_SETUP.md` (B3 nhắc 3 biến PayOS)

- [ ] **Step 1: Viết mục "PayOS" trong DEPLOYMENT.md:**
  1. Tạo kênh thanh toán trên my.payos.vn, liên kết tài khoản ngân hàng.
  2. Lấy Client ID / API Key / Checksum Key cho vào `.env`.
  3. Đặt webhook URL `https://<DOMAIN>/api/v1/payments/payos/webhook`: dashboard PayOS sẽ gửi một request thử, endpoint trả 200.
  4. Thử một đơn thật số tiền nhỏ và kiểm tra đơn chuyển `PAID`.
  5. Nếu PayOS gián đoạn: checkout PayOS trả 503, COD vẫn hoạt động.
- [ ] **Step 2: Kiểm tra** `docker compose --env-file <file thử> -f docker-compose.prod.yml config -q` → hợp lệ, có `CATCHECK_SHOP_WEBBASEURL`.
- [ ] **Step 3: Chạy toàn bộ** `cd backend && ./mvnw -B clean test` (0 fail) và `cd frontend && npm run lint && npm run typecheck && npx vitest run && npm run build` (0 lỗi).
- [ ] **Step 4: Commit** — `docs(deploy): PayOS keys, webhook URL and shop base URL`.

---

## Ngoài phạm vi (cố ý không làm)

- Cấp credit khi mua hàng online (credit vẫn qua mã kích hoạt trong túi cát, theo quyết định 3).
- Thanh toán định kỳ hằng tháng.
- Tự động hoàn tiền qua PayOS. Đơn `refundNeeded` được hoàn tay; admin chỉ thấy cờ.
- Email/thông báo khi đơn đổi trạng thái.
- Hoá đơn điện tử (`invoice` trong SDK).
