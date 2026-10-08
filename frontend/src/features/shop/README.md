# features/shop

- Làm gì: Cửa hàng CatCheck — danh sách/chi tiết sản phẩm, giỏ hàng, đặt đơn, xem đơn.
- Route dùng: /shop, /shop/products/:productId, /cart, /checkout, /orders/:orderId
- API: `GET /shop/products`, `GET /shop/products/{id}`, `GET|DELETE /cart`, `PUT|DELETE /cart/{productId}`,
  `POST /orders`, `GET /orders/{id}`. Không có `GET /orders` (danh sách đơn) — đừng gọi.
- `api.ts`: client fetch + kiểu DTO. `hooks.ts`: query/mutation TanStack; giỏ hàng là một cache duy nhất
  ghi nguyên văn phản hồi máy chủ (tổng tiền/phí vận chuyển không tính ở frontend).
