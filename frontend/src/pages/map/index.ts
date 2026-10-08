/**
 * Barrel của module trang Bản đồ.
 *
 * Dữ liệu đến hoàn toàn từ `GET /api/v1/places` (+ `/places/{id}`, `/places/{id}/reviews`);
 * không còn fixture thiết kế nào trong module này — `mockData.ts` đã bị xoá vì toàn bộ nội
 * dung trong đó (ảnh cơ sở, bác sĩ, bảng giá, chứng nhận, giờ mở cửa, "dịch vụ hỗ trợ nhanh")
 * không có endpoint tương ứng và vi phạm quy tắc không bịa nội dung UI của `CLAUDE.md`.
 */
export { CatCareMapPage } from "./CatCareMapPage";
export { ClinicDetailPage } from "./ClinicDetailPage";
