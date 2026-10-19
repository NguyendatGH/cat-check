-- Publish bảng màu pH chuẩn làm bảng ACTIVE của dòng STANDARD, thay bảng placeholder seed ở V9,
-- để profile prod khởi động được (ActiveChartPlaceholderGuard). Chạy một lần sau khi Flyway đã tạo
-- bảng (docs/DEPLOYMENT.md mục 3):
--
--   docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U catcheck -d catcheck \
--     < backend/tools/publish-standard-color-chart.sql
--
-- Làm đúng như POST /api/v1/admin/color-charts/{id}/publish (ColorChartAdminService.publish): bảng
-- ACTIVE cũ cùng dòng/lô -> ARCHIVED, bảng này -> ACTIVE. Là script vận hành, không phải migration:
-- CLAUDE.md chỉ cho thêm migration có trong danh mục p4 §4.9.2.
--
-- Màu: thang chỉ thị pH chuẩn (vàng ở pH thấp -> xanh lục -> xanh lam ở pH cao), cùng 7 mức với
-- placeholder V9; Lab = ColorSpace.hexToLab(hex). KHÔNG đo trên cát CatCheck: khi có số đo thật, tạo
-- bảng mới trong /admin/ph-color-chart rồi publish (bảng này sẽ tự chuyển ARCHIVED).
--
-- Chạy lại an toàn: không làm gì nếu CHART-STANDARD v1 đã tồn tại.

BEGIN;

UPDATE color_chart
   SET status = 'ARCHIVED', effective_to = now(), updated_at = now()
 WHERE status = 'ACTIVE'
   AND product_line = 'STANDARD'
   AND production_batch IS NULL
   AND NOT EXISTS (SELECT 1 FROM color_chart WHERE code = 'CHART-STANDARD' AND version = 1);

INSERT INTO color_chart (id, code, version, name, product_line, production_batch,
                         card_layout_id, status, is_placeholder, illuminant, observer,
                         delta_e_formula, params, source, effective_from, published_at)
SELECT '00000000-0000-0000-0000-000000000003'::uuid, 'CHART-STANDARD', 1, 'Bảng màu pH chuẩn',
       'STANDARD', NULL, '00000000-0000-0000-0000-000000000001'::uuid, 'ACTIVE', false, 'D65', '2',
       'CIEDE2000', '{"kL":2,"kC":1,"kH":1,"matchScaleDE":15}'::jsonb, 'MANUAL_HEX', now(), now()
 WHERE NOT EXISTS (SELECT 1 FROM color_chart WHERE code = 'CHART-STANDARD' AND version = 1);

INSERT INTO color_chart_point (id, chart_id, ph_value, sort_order, lab_l, lab_a, lab_b,
                               tolerance_delta_e, hex_srgb, display_hex, display_name_vi, display_name_en)
VALUES
    ('00000000-0000-0000-0000-000000000040'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 5.5, 1, 81.124, -3.229, 72.989, 4.0, '#E8C832', '#E8C832', 'Vàng', 'Yellow'),
    ('00000000-0000-0000-0000-000000000041'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 6.0, 2, 80.250, -17.893, 60.596, 4.0, '#C9CE4F', '#C9CE4F', 'Vàng lục', 'Yellow-green'),
    ('00000000-0000-0000-0000-000000000042'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 6.3, 3, 69.786, -32.070, 56.085, 4.0, '#8FB83C', '#8FB83C', 'Xanh lục vàng', 'Green-yellow'),
    ('00000000-0000-0000-0000-000000000043'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 6.6, 4, 63.978, -48.542, 39.735, 4.0, '#4CAF50', '#4CAF50', 'Xanh lục', 'Green'),
    ('00000000-0000-0000-0000-000000000044'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 7.0, 5, 58.990, -34.457, -1.182, 4.0, '#2E9E8F', '#2E9E8F', 'Xanh ngọc', 'Teal'),
    ('00000000-0000-0000-0000-000000000045'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 7.5, 6, 47.191, 11.271, -52.665, 4.0, '#2E6FC9', '#2E6FC9', 'Xanh lam', 'Blue'),
    ('00000000-0000-0000-0000-000000000046'::uuid, '00000000-0000-0000-0000-000000000003'::uuid, 8.0, 7, 31.051, 28.499, -59.481, 4.0, '#1F3FA8', '#1F3FA8', 'Xanh lam đậm', 'Deep blue')
ON CONFLICT (id) DO NOTHING;

COMMIT;
