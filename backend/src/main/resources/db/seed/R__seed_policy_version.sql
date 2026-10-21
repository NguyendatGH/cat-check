-- Official Phase 1 legal documents for CATCHECK
-- Compliant with Vietnamese Law No. 91/2025/QH15 and veterinary health guidance.

WITH seed(id, policy_type, title, content_md, affected_purposes) AS (
    VALUES
        ('01990000-0000-7000-8000-000000000001'::uuid,
         'TERMS',
         'Điều khoản sử dụng dịch vụ CATCHECK',
         E'# Điều khoản sử dụng dịch vụ CATCHECK\n\n' ||
         E'Chào mừng bạn đến với CATCHECK. Bằng việc truy cập hoặc sử dụng ứng dụng và website CATCHECK, bạn đồng ý tuân thủ các điều khoản và điều kiện được nêu dưới đây.\n\n' ||
         E'## 1. Giới thiệu dịch vụ\n\n' ||
         E'CATCHECK là nền tảng giải pháp hỗ trợ chăm sóc sức khỏe mèo cưng thông qua công nghệ cát vệ sinh chỉ thị màu pH và ứng dụng di động phân tích hình ảnh. Dịch vụ bao gồm:\n' ||
         E'- Quét và nhận diện màu hạt cát chỉ thị pH từ ảnh chụp.\n' ||
         E'- Lưu trữ lịch sử và hiển thị biểu đồ xu hướng pH theo thời gian.\n' ||
         E'- Cung cấp danh bạ cơ sở thú y, đặt lịch hẹn và hướng dẫn chăm sóc mèo.\n' ||
         E'- Cung cấp sản phẩm cát thông minh và phụ kiện chăm sóc mèo qua Cửa hàng CATCHECK.\n\n' ||
         E'## 2. Tài khoản và trách nhiệm người dùng\n\n' ||
         E'- Bạn chịu trách nhiệm bảo mật thông tin tài khoản, mật khẩu và mã xác thực OTP.\n' ||
         E'- Bạn cam kết cung cấp thông tin chính xác khi tạo tài khoản và đặt hàng.\n' ||
         E'- Không sử dụng ứng dụng vào các mục đích gian lận, phá hoại hoặc can thiệp trái phép vào hệ thống.\n\n' ||
         E'## 3. Miễn trừ trách nhiệm y tế (Quan trọng)\n\n' ||
         E'- CATCHECK **hỗ trợ theo dõi sức khỏe tại nhà, không phải là thiết bị chẩn đoán y tế và không thay thế việc thăm khám của bác sĩ thú y**.\n' ||
         E'- Màu sắc hạt cát chỉ thị phản ánh xu hướng pH nước tiểu và có thể bị ảnh hưởng bởi ánh sáng, góc chụp hoặc tạp chất.\n' ||
         E'- Khi mèo có dấu hiệu bất thường (bí tiểu, tiểu ra máu, kêu la khi tiểu), bạn cần đưa bé đến cơ sở thú y gần nhất ngay lập tức.\n\n' ||
         E'## 4. Mua hàng, thanh toán và giao hàng\n\n' ||
         E'- CATCHECK hỗ trợ thanh toán khi nhận hàng (COD) và thanh toán trực tuyến qua cổng PayOS.\n' ||
         E'- Đơn hàng COD được xác nhận ngay sau khi đặt; quý khách thanh toán tiền mặt cho nhân viên giao hàng khi nhận kiện hàng.\n' ||
         E'- Mã kích hoạt lượt quét (credit) in trên bao bì có hiệu lực theo thời hạn của từng gói cát (Túi 1 kg: 10 credit/7 ngày; Túi 2,5 kg: 36 credit/30 ngày).\n\n' ||
         E'## 5. Quyền sở hữu trí tuệ\n\n' ||
         E'Toàn bộ thương hiệu CATCHECK, logo, giao diện, mã nguồn, thuật toán nhận diện màu và tài liệu hướng dẫn thuộc quyền sở hữu độc quyền của CATCHECK. Mọi hành vi sao chép trái phép đều bị nghiêm cấm.\n\n' ||
         E'## 6. Liên hệ hỗ trợ\n\n' ||
         E'Mọi thắc mắc về điều khoản, xin vui lòng gửi email về `support@catcheck.vn` hoặc hotline hỗ trợ khách hàng.',
         ARRAY[]::varchar(48)[]),

        ('01990000-0000-7000-8000-000000000002'::uuid,
         'PRIVACY',
         'Chính sách quyền riêng tư & Bảo vệ dữ liệu cá nhân',
         E'# Chính sách quyền riêng tư CATCHECK\n\n' ||
         E'CATCHECK cam kết bảo vệ dữ liệu cá nhân của người dùng tuân thủ theo Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15 và Nghị định số 356/2025/NĐ-CP.\n\n' ||
         E'## 1. Dữ liệu chúng tôi thu thập\n\n' ||
         E'- **Thông tin tài khoản:** Họ tên, địa chỉ email, số điện thoại, mật khẩu mã hóa BCrypt.\n' ||
         E'- **Hồ sơ thú cưng:** Tên bé mèo, giống, ngày sinh, cân nặng, tiền sử sức khỏe và lịch tiêm phòng.\n' ||
         E'- **Dữ liệu quét:** Ảnh chụp khay cát, giá trị dải màu pH nhận diện và thời gian ghi nhận.\n' ||
         E'- **Dữ liệu giao dịch:** Địa chỉ giao hàng, lịch sử đơn hàng và trạng thái thanh toán.\n' ||
         E'- **Dữ liệu vị trí:** Tọa độ GPS khi bạn cho phép (để tìm phòng khám thú y gần nhất quanh bạn).\n\n' ||
         E'## 2. Mục đích xử lý dữ liệu\n\n' ||
         E'- Cung cấp tính năng phân tích màu cát và theo dõi xu hướng pH.\n' ||
         E'- Xử lý đơn đặt hàng, giao hàng và đối soát thanh toán.\n' ||
         E'- Gửi thông báo nhắc nhở kiểm tra định kỳ cho bé mèo.\n' ||
         E'- Cải thiện độ chính xác của mô hình nhận diện màu (chỉ khi bạn đồng ý).\n\n' ||
         E'## 3. Quyền của chủ thể dữ liệu\n\n' ||
         E'Theo quy định pháp luật, bạn có đầy đủ các quyền sau trong Trung tâm quyền riêng tư (Privacy Center):\n' ||
         E'- **Quyền được biết & truy cập:** Xem toàn bộ dữ liệu CATCHECK lưu trữ về bạn.\n' ||
         E'- **Quyền rút lại sự đồng ý:** Bật/tắt các mục đích xử lý dữ liệu bất cứ lúc nào.\n' ||
         E'- **Quyền xuất dữ liệu (Data Export):** Tải về bản sao dữ liệu cá nhân định dạng nén chuẩn ZIP.\n' ||
         E'- **Quyền yêu cầu xóa tài khoản (Data Erasure):** Yêu cầu xóa vĩnh viễn tài khoản và toàn bộ hình ảnh, hồ sơ thú cưng sau 7 ngày ân hạn.\n\n' ||
         E'## 4. Bảo mật dữ liệu\n\n' ||
         E'Chúng tôi áp dụng các tiêu chuẩn an ninh nghiêm ngặt: mã hóa đầu cuối thông tin nhạy cảm, giao thức HTTPS/TLS, mã hóa mật khẩu một chiều BCrypt và xác thực hai lớp (2FA/TOTP) cho tài khoản quản trị.\n\n' ||
         E'## 5. Cán bộ bảo vệ dữ liệu (DPO)\n\n' ||
         E'Email liên hệ phụ trách bảo vệ dữ liệu: `dpo@catcheck.vn`. CATCHECK cam kết phản hồi các yêu cầu dữ liệu cá nhân trong vòng 02 ngày làm việc.',
         ARRAY[
             'SERVICE_CORE', 'SCAN_IMAGE_RETAIN', 'ALGO_IMPROVEMENT',
             'HEALTH_REMINDER_PUSH', 'HEALTH_REMINDER_EMAIL', 'MARKETING_EMAIL',
             'MARKETING_PUSH', 'PRODUCT_ANALYTICS', 'COOKIE_ANALYTICS'
         ]::varchar(48)[]),

        ('01990000-0000-7000-8000-000000000003'::uuid,
         'MEDICAL_DISCLAIMER',
         'Tuyên bố miễn trừ trách nhiệm y tế',
         E'# Tuyên bố miễn trừ trách nhiệm y tế\n\n' ||
         E'> **CATCHECK hỗ trợ theo dõi tại nhà — không thay thế chẩn đoán hoặc điều trị của bác sĩ thú y.**\n\n' ||
         E'## 1. Bản chất của thông tin theo dõi\n\n' ||
         E'CATCHECK ghi nhận màu sắc của các hạt chỉ thị trong cát vệ sinh và phân loại vào các dải tham chiếu pH. Kết quả hiển thị chỉ mang tính chất tham khảo, giúp chủ nuôi nhận biết sự thay đổi so với mức thường ngày của chính bé mèo.\n\n' ||
         E'## 2. Các yếu tố ảnh hưởng đến màu hạt cát\n\n' ||
         E'- Ánh sáng phòng và chất lượng camera điện thoại khi chụp.\n' ||
         E'- Thức ăn, nước uống, thuốc hoặc thực phẩm chức năng mèo đang sử dụng.\n' ||
         E'- Độ ẩm môi trường và thời gian tiếp xúc của nước tiểu với hạt cát.\n\n' ||
         E'## 3. Dấu hiệu khẩn cấp cần đến bác sĩ thú y NGAY LẬP TỨC\n\n' ||
         E'Nếu bạn nhận thấy các dấu hiệu sau ở mèo, **đừng chờ theo dõi thêm mà hãy đưa bé đến bệnh viện thú y ngay**:\n' ||
         E'- Bé rặn tiểu nhiều lần nhưng không ra nước tiểu hoặc chỉ ra vài giọt.\n' ||
         E'- Kêu gào, đau đớn khi bước vào khay vệ sinh.\n' ||
         E'- Có máu tươi hoặc vệt hồng sẫm trong nước tiểu.\n' ||
         E'- Bỏ ăn, nôn ói liên tục, lờ đờ hoặc bụng căng cứng.\n\n' ||
         E'Tắc nghẽn đường tiết niệu ở mèo là tình trạng cấp cứu nguy hiểm đến tính mạng trong vòng 24–48 giờ nếu không được can thiệp kịp thời.',
         ARRAY[]::varchar(48)[]),

        ('01990000-0000-7000-8000-000000000004'::uuid,
         'COOKIE',
         'Chính sách cookie',
         E'# Chính sách cookie CATCHECK\n\n' ||
         E'CATCHECK chỉ sử dụng cookie cần thiết nhằm đảm bảo hoạt động an toàn của ứng dụng:\n\n' ||
         E'- **Cookie phiên đăng nhập (Session cookie):** Giúp duy trì trạng thái đăng nhập của bạn giữa các trang.\n' ||
         E'- **Cookie bảo mật XSRF-TOKEN:** Bảo vệ tài khoản chống lại các cuộc tấn công giả mạo yêu cầu (CSRF).\n\n' ||
         E'Hiện tại CATCHECK không sử dụng cookie theo dõi quảng cáo từ bên thứ ba.',
         ARRAY[]::varchar(48)[])
)
INSERT INTO policy_version (
    id, policy_type, version, locale, title, content_md, content_hash,
    summary_of_changes, requires_reconsent, affected_purposes, effective_from
)
SELECT
    id, policy_type, '1.0', 'vi', title, content_md,
    encode(digest(content_md, 'sha256'), 'hex'),
    'Bản chính thức hoàn thiện cho CATCHECK', false, affected_purposes,
    TIMESTAMPTZ '2026-01-01 00:00:00+07'
FROM seed
ON CONFLICT (policy_type, version, locale) DO UPDATE SET
    title = EXCLUDED.title,
    content_md = EXCLUDED.content_md,
    content_hash = EXCLUDED.content_hash,
    summary_of_changes = EXCLUDED.summary_of_changes,
    requires_reconsent = EXCLUDED.requires_reconsent,
    affected_purposes = EXCLUDED.affected_purposes,
    effective_from = EXCLUDED.effective_from;
