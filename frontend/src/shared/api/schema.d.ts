/**
 * TODO: sinh thật bằng `npm run api:gen` (openapi-typescript) khi backend Spring Boot đã
 * chạy và expose OpenAPI (`/v3/api-docs`). Để trống/placeholder ở M0 vì chưa có backend.
 *
 * openapi-fetch cần một type `paths` hợp lệ để suy luận request/response — đặt placeholder
 * tối thiểu (không có endpoint nào) để shared/api/client.ts compile được ở M0.
 */

// eslint-disable-next-line @typescript-eslint/no-empty-object-type -- placeholder chờ api:gen sinh thật
export interface paths {}

// eslint-disable-next-line @typescript-eslint/no-empty-object-type -- placeholder chờ api:gen sinh thật
export interface components {
  schemas: Record<string, never>;
}
