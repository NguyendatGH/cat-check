-- Seed `holiday_calendar` — ngày lễ Việt Nam năm hiện tại và năm sau
-- (p4 §4.9.2 bảng seed: `R__seed_holiday_calendar.sql`, "Ngày lễ VN năm hiện tại và năm sau.
-- Nạp thêm mỗi năm").
--
-- VÌ SAO BẢNG NÀY QUAN TRỌNG: `dsar_request.ack_due_at` tính theo NGÀY LÀM VIỆC
-- (p15 REQ-DSAR-02) nên thiếu dữ liệu lễ ⇒ SLA DSAR tính SAI. `HolidayCalendarReminderJob`
-- (p12 §12.6.5) nhắc DPO nạp lịch năm N+1 từ 01/12 và cảnh báo cứng nếu tới 29/12 vẫn thiếu.
--
-- ⚠ NGUỒN DỮ LIỆU: ngày lễ cố định theo Điều 112 Bộ luật Lao động 2019; các mốc âm lịch và số
-- ngày nghỉ của Tết / Quốc khánh theo thông báo nghỉ lễ của Chính phủ cho từng năm. Các mốc âm
-- lịch KHÔNG suy ra được bằng công thức trong SQL, nên **DPO phải đối chiếu lại với văn bản
-- thông báo chính thức mỗi năm trước khi tin vào kết quả tính SLA** — đây chính là lý do p15
-- đặt dữ liệu này vào bảng cấu hình thay vì hard-code.
--
-- `source` chỉ nhận 'OFFICIAL' | 'COMPANY' (CHECK ở V6). Toàn bộ dòng dưới đây là ngày nghỉ
-- theo quy định nhà nước ⇒ 'OFFICIAL'. Ngày nghỉ riêng của công ty (nếu có) do admin thêm sau
-- với source = 'COMPANY' và KHÔNG thuộc file seed này.

INSERT INTO holiday_calendar (country_code, holiday_date, name_vi, source)
VALUES
    -- ===== 2026 =====
    ('VN', DATE '2026-01-01', 'Tết Dương lịch', 'OFFICIAL'),
    -- Tết Nguyên đán Bính Ngọ: mùng 1 là 17/02/2026; Chính phủ chốt nghỉ liền 9 ngày 14–22/02.
    ('VN', DATE '2026-02-14', 'Tết Nguyên đán Bính Ngọ (ngày nghỉ trước Tết)', 'OFFICIAL'),
    ('VN', DATE '2026-02-15', 'Tết Nguyên đán Bính Ngọ (ngày nghỉ trước Tết)', 'OFFICIAL'),
    ('VN', DATE '2026-02-16', 'Tết Nguyên đán Bính Ngọ (ngày nghỉ trước Tết)', 'OFFICIAL'),
    ('VN', DATE '2026-02-17', 'Tết Nguyên đán Bính Ngọ (mùng 1)', 'OFFICIAL'),
    ('VN', DATE '2026-02-18', 'Tết Nguyên đán Bính Ngọ (mùng 2)', 'OFFICIAL'),
    ('VN', DATE '2026-02-19', 'Tết Nguyên đán Bính Ngọ (mùng 3)', 'OFFICIAL'),
    ('VN', DATE '2026-02-20', 'Tết Nguyên đán Bính Ngọ (mùng 4)', 'OFFICIAL'),
    ('VN', DATE '2026-02-21', 'Tết Nguyên đán Bính Ngọ (ngày nghỉ sau Tết)', 'OFFICIAL'),
    ('VN', DATE '2026-02-22', 'Tết Nguyên đán Bính Ngọ (ngày nghỉ sau Tết)', 'OFFICIAL'),
    -- 10/3 âm lịch năm 2026 rơi vào Chủ Nhật 26/04 ⇒ nghỉ bù thứ Hai 27/04.
    ('VN', DATE '2026-04-26', 'Giỗ Tổ Hùng Vương (10/3 âm lịch)', 'OFFICIAL'),
    ('VN', DATE '2026-04-27', 'Nghỉ bù Giỗ Tổ Hùng Vương', 'OFFICIAL'),
    ('VN', DATE '2026-04-30', 'Ngày Chiến thắng', 'OFFICIAL'),
    ('VN', DATE '2026-05-01', 'Ngày Quốc tế Lao động', 'OFFICIAL'),
    -- Quốc khánh 2026: 02/09 (thứ Tư) + 01 ngày liền kề theo Điều 112.1.đ BLLĐ 2019.
    ('VN', DATE '2026-09-01', 'Quốc khánh (ngày liền kề)', 'OFFICIAL'),
    ('VN', DATE '2026-09-02', 'Quốc khánh', 'OFFICIAL'),

    -- ===== 2027 =====
    ('VN', DATE '2027-01-01', 'Tết Dương lịch', 'OFFICIAL'),
    ('VN', DATE '2027-01-02', 'Tết Dương lịch (nghỉ liền kề)', 'OFFICIAL'),
    ('VN', DATE '2027-01-03', 'Tết Dương lịch (nghỉ liền kề)', 'OFFICIAL'),
    -- Tết Nguyên đán Đinh Mùi: mùng 1 là 06/02/2027; Chính phủ chốt nghỉ liền 7 ngày 04–10/02.
    ('VN', DATE '2027-02-04', 'Tết Nguyên đán Đinh Mùi (28 tháng Chạp)', 'OFFICIAL'),
    ('VN', DATE '2027-02-05', 'Tết Nguyên đán Đinh Mùi (29 tháng Chạp)', 'OFFICIAL'),
    ('VN', DATE '2027-02-06', 'Tết Nguyên đán Đinh Mùi (mùng 1)', 'OFFICIAL'),
    ('VN', DATE '2027-02-07', 'Tết Nguyên đán Đinh Mùi (mùng 2)', 'OFFICIAL'),
    ('VN', DATE '2027-02-08', 'Tết Nguyên đán Đinh Mùi (mùng 3)', 'OFFICIAL'),
    ('VN', DATE '2027-02-09', 'Tết Nguyên đán Đinh Mùi (mùng 4)', 'OFFICIAL'),
    ('VN', DATE '2027-02-10', 'Tết Nguyên đán Đinh Mùi (mùng 5)', 'OFFICIAL'),
    -- 10/3 âm lịch năm 2027 rơi vào thứ Sáu 16/04.
    ('VN', DATE '2027-04-16', 'Giỗ Tổ Hùng Vương (10/3 âm lịch)', 'OFFICIAL'),
    ('VN', DATE '2027-04-30', 'Ngày Chiến thắng', 'OFFICIAL'),
    ('VN', DATE '2027-05-01', 'Ngày Quốc tế Lao động', 'OFFICIAL'),
    -- Quốc khánh 2027: Chính phủ chốt nghỉ liền 4 ngày 02–05/09.
    ('VN', DATE '2027-09-02', 'Quốc khánh', 'OFFICIAL'),
    ('VN', DATE '2027-09-03', 'Quốc khánh (nghỉ liền kề)', 'OFFICIAL'),
    ('VN', DATE '2027-09-04', 'Quốc khánh (nghỉ liền kề)', 'OFFICIAL'),
    ('VN', DATE '2027-09-05', 'Quốc khánh (nghỉ liền kề)', 'OFFICIAL')

ON CONFLICT (country_code, holiday_date) DO UPDATE SET
    name_vi = EXCLUDED.name_vi,
    source  = EXCLUDED.source;
