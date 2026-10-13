# CatCheck — đặc tả thiết kế lại Buyer và Admin

**Trạng thái:** Bản triển khai v1; phần cần backend/quyết định mới được đánh dấu riêng  
**Ngày:** 12/10/2026  
**Phạm vi:** Web responsive và PWA mobile của Buyer; workspace Admin. Seller chỉ nhận các token và quy tắc tương tác dùng chung.  
**Nguồn đối chiếu:** `src/app/router`, `src/pages/dashboard`, `src/pages/scan`, `src/pages/onboarding`, `src/pages/admin`, `src/features/*/hooks.ts`, `src/shared/auth/roles.ts`, các controller backend.

## 0. Hướng dẫn thực thi cho agent code

Đọc mục này trước khi sửa code. **Pha 1** là phần có thể làm với API và quyền hiện có; **Pha 2** là đề xuất sau khi có quyết định sản phẩm hoặc hợp đồng backend mới. Agent không được tự biến đề xuất Pha 2 thành API giả, dữ liệu giả hoặc route mới.

1. Ưu tiên hoàn thành Pha 1 theo thứ tự ở mục 11; mỗi phần phải build, typecheck và kiểm tra luồng liên quan trước khi sang phần tiếp theo.
2. Giữ URL, payload, enum, quyền, trạng thái onboarding, `scanRequestId`, ETag/`If-Match`, quy tắc credit và nội dung y tế/pháp lý hiện tại. Chỉ thay cách trình bày và điều hướng bên trong hợp đồng sẵn có.
3. Không thêm dependency hay font tải từ mạng. Dùng React, CSS/Tailwind, Radix/Ant Design và GSAP đã có khi thật cần. Trước khi sửa component dùng chung, tìm tất cả nơi gọi nó.
4. Không coi một mockup là bằng chứng backend hỗ trợ. Nếu không thấy endpoint/field trong repo, dùng trạng thái phù hợp hoặc để lại đúng trang hiện có; không phát minh số liệu hay quyền.
5. Không sửa rộng tất cả trang Admin trong một lượt. Dựng mẫu trên dashboard và **một** trang danh sách/chi tiết trước, kiểm tra với role và mobile, rồi mới áp dụng cho trang tương tự.
6. Các chỗ ghi “cần xác nhận” ở mục 12 có **mặc định Pha 1** đi kèm; agent dùng mặc định đó, không chặn toàn bộ việc triển khai để hỏi.
7. Text giao diện mới phải vào namespace JSON tiếng Việt hiện có trong `src/shared/i18n/locales/vi`; không hard-code chuỗi trong JSX. Giữ key cũ nếu nội dung không đổi, để tránh làm hỏng bản dịch và test.

## 1. Mục tiêu và ranh giới

### 1.1 Vấn đề cần giải quyết

Giao diện hiện tại ưu tiên hiển thị nhiều tính năng và nhiều loại dữ liệu cùng lúc. Buyer phải tự tìm đường giữa dashboard, hồ sơ mèo, quét, lịch sử, nhắc nhở và tín dụng. Admin phải đi qua nhiều nhóm menu và bảng trước khi thấy việc cần xử lý. Nhiều bề mặt bo góc, badge và shadow làm mọi phần tử có cùng mức nhấn. Motion trang trí còn tồn tại ở điều hướng, nhưng các thay đổi trạng thái quan trọng thiếu phản hồi rõ.

### 1.2 Kết quả mong muốn

1. Buyer nhìn thấy **một việc chính** và **một thông tin sức khỏe đáng chú ý** ngay ở màn đầu.
2. Buyer có thể đi từ mở app đến chụp ảnh với ít thao tác hơn, nhưng luôn thấy ảnh đang gán cho mèo nào hoặc khay chung nào.
3. Admin mở workspace và thấy lối vào tác vụ thuộc quyền mình; hàng đợi có thứ tự ưu tiên toàn hệ thống chỉ thuộc Pha 2 khi có API tổng hợp.
4. Giao diện có phân cấp chữ, khoảng trắng và bề mặt rõ. Motion giải thích chuyển trạng thái và phản hồi hành động.
5. Không đổi ý nghĩa y tế của kết quả quét, không tạo dữ liệu giả, không nới quyền Admin ở phía client.

### 1.3 Ngoài phạm vi đợt đầu

- Không đổi taxonomy pH, ngưỡng cảnh báo hoặc câu chữ tư vấn y tế khi chưa được chủ sản phẩm duyệt.
- Không tự tạo API tổng hợp phía client bằng cách tải toàn bộ các trang dữ liệu Admin.
- Không đổi route công khai, tên trường form hay hợp đồng backend chỉ để phù hợp mockup. URL cũ phải tiếp tục hoạt động; điều hướng mới có thể dẫn đến cùng URL.
- Không đưa animation vào mọi card, nút hoặc hàng bảng.

## 2. Nguyên tắc thiết kế

| Nguyên tắc | Quy tắc áp dụng |
|---|---|
| Tác vụ trước tính năng | Vị trí nổi bật dành cho việc người dùng cần làm tiếp theo, không dành cho danh mục chức năng. |
| Một màn hình, một trọng tâm | Mỗi viewport đầu có một CTA chính. Tác vụ phụ dùng link hoặc menu ngữ cảnh. |
| Công khai trạng thái | Đang tải, đang gửi, thành công, thất bại, hết quyền và dữ liệu cũ phải có phản hồi cụ thể. |
| Tiết lộ dần | Chi tiết kỹ thuật, lịch sử đầy đủ và bộ lọc nâng cao chỉ xuất hiện khi người dùng cần. |
| An toàn theo ngữ cảnh | Trước khi gửi ảnh hoặc thao tác Admin, hiển thị đối tượng, hậu quả và khả năng hoàn tác nếu có. |
| Motion có mục đích | Chỉ dùng để chỉ hướng, nối hai trạng thái, hoặc xác nhận kết quả. |
| Đọc được trên mobile | Không buộc người dùng cuộn ngang bảng để hoàn tất tác vụ chính. |

## 3. Kiến trúc thông tin mục tiêu

### 3.1 Buyer

**Pha 1:** giữ các URL và nhóm menu đang hoạt động; giảm số nút chen chúc ở header mobile và làm nổi rõ Quét, Hôm nay, Hồ sơ. Không đổi tên nhãn điều hướng hàng loạt vì chúng có thể gắn với tài liệu hỗ trợ và test. **Pha 2, sau thử nghiệm tìm tác vụ:** điều hướng chính trên mobile đề xuất **Hôm nay**, **Quét**, **Hồ sơ**, **Lịch sử**, **Thêm**. “Thêm” gom cửa hàng, bản đồ, cộng đồng, trợ lý, tín dụng và cài đặt. Desktop dùng cùng nhóm ý nghĩa; URL cũ vẫn hoạt động.

Màn **Hôm nay** có thứ tự cố định về mặt ý nghĩa:

1. Tình trạng cần chú ý: cờ sức khỏe chưa xác nhận, nhắc nhở quá hạn hoặc lỗi cần xử lý. Chỉ hiển thị khi có dữ liệu thật.
2. Hành động chính: “Quét cho [tên mèo]” hoặc “Bắt đầu quét”.
3. Lần quét gần nhất: mèo, thời gian, kết quả, mức tin cậy; link xem chi tiết.
4. Các mèo khác: hàng danh sách ngắn hoặc bộ chuyển mèo; không lặp lại toàn bộ trang hồ sơ.
5. Nội dung khám phá: xu hướng, mẹo chăm sóc, cửa hàng; đặt sau phần hành động và sức khỏe.

Không đặt số dư tín dụng, upsell và nội dung biên tập lên trước việc theo dõi sức khỏe. Khi không có dữ liệu, hiển thị bước bắt đầu cụ thể thay cho nhiều card trống.

### 3.2 Admin

Điều hướng cấp một mục tiêu gồm **Công việc**, **Người dùng & lượt quét**, **Nội dung & danh mục**, **Riêng tư & an ninh**, **Vận hành**. **Pha 1** giữ route/nhãn hiện tại nhưng thêm tìm menu trên desktop và ưu tiên các tác vụ được phép thấy trên trang đầu. **Pha 2** mới đổi nhóm/nhãn sau kiểm thử với từng role.

Trang **Công việc** gồm:

- Việc quá hạn hoặc cần xử lý ngay.
- Việc mới theo loại: yêu cầu quyền riêng tư, báo cáo cộng đồng, sự cố, job thất bại, outbox lỗi. Chỉ hiện loại mà role được phép xem.
- Tình trạng hệ thống tóm tắt; chỉ số tổng đặt sau hàng đợi.
- Bộ lọc `Của tôi / Tất cả được phép xem` chỉ khi backend có thông tin phân công. Không giả lập “Của tôi” từ dữ liệu thiếu.

Desktop có tìm trang/tác vụ trong sidebar. Mobile có nút mở menu và tìm kiếm, nhưng nội dung chính ưu tiên danh sách công việc; không hiển thị menu 20 mục như bước đầu tiên.

## 4. Đặc tả Buyer

### B1 — Màn Hôm nay

**Nguồn dữ liệu hiện có:** mèo hoạt động, lịch sử và summary quét, cờ sức khỏe, nhắc nhở, entitlement, tín dụng và care tips. Mỗi vùng chỉ gọi dữ liệu cần thiết; nội dung dưới fold có thể tải sau.

**Các biến thể bắt buộc:**

| Trạng thái | Nội dung đầu màn | Hành động chính |
|---|---|---|
| Khách chưa đăng nhập | Giải thích giá trị bằng một ví dụ ngắn, không dùng dữ liệu bệnh giả | Đăng nhập/đăng ký |
| Tài khoản chưa có mèo | Hướng dẫn tạo mèo đầu tiên và giải thích thời gian cần thiết | Thêm mèo |
| Có một mèo | Tên mèo, tình trạng gần nhất và việc tiếp theo | Quét cho mèo đó |
| Nhiều mèo | Mèo chính đang chọn hiển thị rõ; đổi mèo trong cùng màn | Quét cho mèo đang chọn |
| Có cờ chưa xác nhận hoặc nhắc nhở quá hạn | Hàng cảnh báo ở trên hành động quét, không dùng màu làm dấu hiệu duy nhất | Xem/hoàn thành việc cần xử lý |
| Dữ liệu một vùng lỗi | Giữ các vùng còn hoạt động, báo lỗi và cho thử lại tại vùng lỗi | Theo vùng liên quan |

**Hành vi Pha 1:** trang Hôm nay mặc định dùng mèo chính, nếu không có thì dùng mèo hoạt động đầu tiên; với nhiều mèo, người dùng chọn mèo trong UI. Lựa chọn chỉ tồn tại trong phiên màn hình, không gọi API đổi mèo chính. Mọi truy vấn kết quả/xu hướng theo mèo phải dùng cùng `catId` đang chọn; không ghép kết quả toàn đàn với tên một mèo. CTA quét ghi lựa chọn vào `useScanCaptureStore` rồi đi tới `/scan`. Khi không có mèo đang chọn, đi tới `/scan/select-cat` như hiện tại. Không lưu lựa chọn mới vào tài khoản hoặc `localStorage` ở Pha 1.

### B2 — Luồng quét

Luồng mục tiêu dài hạn: **mở Quét → kiểm tra mèo/khay chung trong cùng màn → chụp hoặc chọn ảnh → xem trước → gửi → phân tích → kết quả**. Pha 1 chỉ rút bớt bước cho CTA Hôm nay đã biết mèo; luồng vào trực tiếp khi store trống vẫn giữ màn `/scan/select-cat` hiện có.

- Nếu chỉ có một mèo hoạt động và đi từ CTA Hôm nay: CTA đặt mèo đó vào `useScanCaptureStore`, mở thẳng `/scan`, hiển thị tên/ảnh và nút “Đổi”. Link trực tiếp tới `/scan/select-cat` vẫn mở màn chọn như hiện tại để người dùng chọn khay chung.
- Nếu có nhiều mèo: CTA Hôm nay dùng mèo người dùng vừa chọn; nếu không có lựa chọn từ màn đó, giữ màn chọn mèo hiện tại. Không tự gán mèo chính khi người dùng mở trực tiếp `/scan` vì có nguy cơ gán nhầm ảnh. Tên mèo phải hiển thị cạnh ảnh xem trước; người dùng đổi được trước khi gửi.
- Khay chung là một lựa chọn hiển thị rõ, không được gán ngầm cho mèo chính.
- Nếu chưa có mèo: **Pha 1 giữ màn thêm mèo hiện tại**. Không mở lối quét khay chung cho tài khoản không có mèo khi chưa có quyết định sản phẩm/backend xác nhận.
- Trước khi gửi: kiểm tra ảnh, loại file, kích thước, quyền chụp và trạng thái tín dụng đang có. Server là nguồn quyết định có trừ credit hay không; UI không hứa về thời điểm trừ trước khi nhận kết quả server.
- Đang phân tích: giữ ảnh xem trước, giải thích đang xử lý, chặn gửi trùng bằng `scanRequestId`. Pha 1 chỉ bảo toàn ID trong phiên SPA như store hiện tại; khi request lỗi mạng mà chưa rõ server đã nhận chưa, tra `/scans/by-request/{scanRequestId}` trước khi cho gửi lại. Không hứa khôi phục sau reload/tab đóng khi chưa có lưu bền vững được thiết kế và kiểm thử.
- Kết quả không kết luận: giữ giải thích chất lượng ảnh, CTA chụp lại, và thông báo tín dụng không bị trừ theo hợp đồng hiện tại.
- Kết quả kết luận: kết quả pH/màu, mức tin cậy, thời gian, mèo, hành động tiếp theo. Tranh chấp, gán lại mèo và xóa đặt trong menu chi tiết.

**Không đưa “xem chi tiết” thành bước bắt buộc sau kết quả.** Màn kết quả phải đủ để người dùng hiểu kết quả chính và việc nên làm tiếp theo.

### B3 — Onboarding

Giữ điều hướng server theo `user.onboarding.nextStep` và route hiện hành. Phân biệt trong giao diện:

1. **Bắt đầu dùng:** thông tin mèo tối thiểu và xác nhận pháp lý cần thiết.
2. **Hoàn thiện sau:** khảo sát sức khỏe và mã kích hoạt khi được phép bỏ qua theo hợp đồng hiện tại.

Backend hiện điều hướng theo trạng thái tuần tự. Pha 1 giữ các route và API `skipped` của khảo sát; làm nhãn “Để sau” rõ hơn mà không bỏ request ghi nhận bỏ qua. Kích hoạt mã giữ nút bỏ qua hiện tại. Không bỏ xác nhận pháp lý, nội dung điều khoản hoặc cơ chế bắt cuộn hết trong Pha 1. Chỉ xem xét thay cơ chế cuộn sau khi pháp lý/chủ sản phẩm xác nhận.

Sau khi hoàn tất, màn hướng dẫn phải nêu một hành động đầu tiên; không hiển thị lại roadmap ba bước trên dashboard nếu người dùng đã hoàn thành chúng.

### B4 — Hồ sơ, lịch sử và khám phá (Pha 2 sau các gói 1–5)

- Hồ sơ mèo: tóm tắt sức khỏe, lần quét và ghi chú gần nhất ở đầu; thông tin hành chính đi sau.
- Lịch sử: mặc định nhóm theo mèo và thời gian, cho lọc nhanh; mở một bản ghi giữ ngữ cảnh quay lại.
- Xu hướng: giải thích phạm vi ngày, số điểm dữ liệu và trường hợp quá ít dữ liệu; không vẽ đường xu hướng gây hiểu lầm khi dữ liệu thiếu.
- Cộng đồng, cửa hàng, bản đồ và trợ lý là khu vực khám phá, không chen vào hành trình quét trừ khi có liên quan trực tiếp.

## 5. Đặc tả Admin

### A1 — Trang Công việc

**Mục tiêu Pha 2 khi có queue API:** quá hạn → cần hành động → đang chờ → thông tin. Mỗi item có loại, tiêu đề dễ hiểu, thời điểm, trạng thái, hạn xử lý nếu API trả về, và CTA mở chi tiết. Pha 1 không tạo danh sách trộn hoặc thứ tự ưu tiên xuyên module ở client. Không dùng số lượng từ trang kết quả đầu tiên như tổng toàn hệ thống.

**Pha 1 dùng API hiện có:** trang đầu là các **lối vào theo công việc**, không tự gọi là “hàng đợi thống nhất”. Dùng KPI `/admin/metrics/dashboard` và `/admin/system-status` như hiện tại; tạo link rõ đến DSAR, báo cáo cộng đồng, job và outbox nếu role có quyền. Chỉ hiện số đếm khi một endpoint thực sự trả đúng số đếm đó. Không tính một chỉ số bằng cách lấy trang 20 bản ghi đầu tiên, không gọi hàng loạt mọi danh sách khi mount trang đầu, không tự suy ra deadline của loại việc không có `dueAt`.

**Giới hạn role Pha 1:** `/admin` hiện chỉ cho `ROLES.dashboard`; `MODERATOR` đi vào `/admin/community/reports`, `ADMIN_CATALOG` có landing `/seller`. Không chuyển mọi role tới `/admin` và không mở rộng `ROLES.dashboard` chỉ để tất cả thấy trang đầu mới. Các lối vào trên trang đầu phải lọc bằng capability hiện có.

**Pha 2 để có hàng đợi thống nhất:** cần backend cung cấp API tổng hợp có phân quyền ở server, ví dụ `GET /api/v1/admin/work-queue` (đề xuất mới, **chưa tồn tại**), trả `id`, `type`, `title`, `status`, `priority`, `dueAt?`, `createdAt`, `assignedTo?` và tổng theo loại. API phải lọc theo role/ownership, phân trang, trả thời điểm tạo snapshot. Client tự ánh xạ `type + id` sang route nội bộ được cho phép; không nhận URL tự do từ API rồi điều hướng tới đó.

### A2 — Mẫu danh sách và chi tiết (thử trên Người dùng trước)

**Desktop ≥ 1024px:** thanh tiêu đề và một hành động chính; filter ngắn dạng chip/ô tìm kiếm; danh sách rộng tối đa phù hợp nội dung; panel chi tiết cạnh danh sách khi có lựa chọn. Cột chỉ giữ thông tin cần để quyết định mở item. Bộ lọc nâng cao ẩn sau một nút.

**Tablet 768–1023px:** danh sách toàn chiều rộng; chi tiết mở trong sheet nếu trang hiện tại chưa có route chi tiết, hoặc dùng route chi tiết hiện có. Không tạo route mới chỉ để đạt bố cục.

**Mobile < 768px:** mỗi item là một hàng có tiêu đề, trạng thái, thời gian và tín hiệu khẩn cấp; nhấn mở route chi tiết hiện có hoặc sheet, theo đúng quy tắc tablet. Bộ lọc dùng sheet. Hành động chính ở cuối vùng chi tiết, luôn có nhãn và trạng thái disabled giải thích được.

Giữ URL, filter và trang hiện tại khi quay từ chi tiết; Pha 1 dùng query string cho filter/page nếu trang đang có route chi tiết, và giữ state khi chỉ mở sheet trong cùng trang. Sort trên một trang phải được ghi rõ là “trang hiện tại”; sort toàn bộ cần backend hỗ trợ. Không thêm bulk action mới ở Pha 1; giữ các bulk action đã có và kiểm tra xung đột từng item.

### A3 — Thực hiện hành động nhạy cảm

Flow chung: **chọn item → xem dữ liệu và hậu quả → chọn hành động → nhập lý do khi bắt buộc → xác nhận → trạng thái mới**.

- Giữ role/capability ở router và API. UI ẩn hành động không được phép; backend vẫn là nguồn quyết định cuối.
- Giữ `reason` tối thiểu 10 ký tự đối với thao tác ghi thuộc hợp đồng hiện tại. Chỉ hỏi ở thời điểm hành động, không đặt textarea lý do thường trực trên đầu trang danh sách.
- Thao tác phá hủy hoặc khó hoàn tác hiển thị tên đối tượng, hậu quả, và bước xác nhận riêng. Không làm thao tác trông giống nút lọc.
- Với ETag/`If-Match`, khi gặp 412: giữ dữ liệu người dùng vừa nhập, cho so sánh hoặc tải bản mới, không xóa form.
- 401/403: giải thích đăng nhập/quyền; 409/412/428: giải thích xung đột hoặc thiếu điều kiện; 5xx: giữ thao tác để thử lại. Không dùng toast “Lỗi” chung cho mọi trường hợp.
- Thành công: invalidate đúng query key đang có, hiển thị trạng thái server trả về và thông báo bằng text/`aria-live`; không tự cập nhật số đếm hoặc hàng đợi chưa có dữ liệu xác thực.

### A4 — Nội dung, thuật ngữ và quyền

Ẩn `specRef`, mã endpoint và giá trị enum thô khỏi header và bộ lọc dành cho người vận hành. Giữ trong tài liệu kỹ thuật, màn debug hoặc tooltip hỗ trợ khi thật sự cần. Dùng từ nghiệp vụ nhất quán: cùng một trạng thái có cùng nhãn trên danh sách, chi tiết, filter và thông báo.

Tài liệu phân quyền dùng bảng `ROLES` hiện tại làm điểm bắt đầu. Mọi thay đổi menu theo vai trò phải kiểm tra đủ `ADMIN_SUPER`, `ADMIN_SUPPORT`, `ADMIN_CATALOG`, `DPO`, `MODERATOR`; không mặc định mọi Admin thấy cùng một trang Công việc.

## 6. Hệ thị giác

### 6.1 Chữ

**Mặc định Pha 1 để agent code được ngay:** Buyer giữ Nunito đã self-host nhưng giảm lạm dụng bold; Admin chuyển sang `system-ui, sans-serif` cho bảng/form và số liệu để tăng độ rõ, không tải font mới. Làm hai mẫu thử với dữ liệu tiếng Việt thật trên 360px và desktop; nếu người dùng đánh giá font Buyer vẫn không phù hợp, chọn font mới ở Pha 2 sau khi kiểm tra dấu tiếng Việt, chiều rộng nhãn, độ rõ ở 14px và dung lượng. Font mới phải self-host và `font-display: swap`.

Thang chữ mục tiêu: tiêu đề trang 28/36 desktop và 24/32 mobile; tiêu đề vùng 20/28; nội dung 16/24; bảng và phụ đề 14/20; chú thích 12/16 chỉ cho metadata. Không dùng 10–11px cho thông tin cần đọc hoặc hành động. Số liệu dùng `tabular-nums`; mã kỹ thuật dùng monospace có giới hạn.

### 6.2 Bề mặt và màu

- Tối đa ba mức bo: 4–6px cho ô nhập/bảng, 8–10px cho panel, 12px cho card trọng tâm. Pill chỉ dùng cho trạng thái/chip.
- Ưu tiên khoảng trắng và đường phân cách nhẹ; không lồng card trong card quá một cấp. Shadow chỉ dùng để chỉ độ nổi thật như drawer, sticky bar hoặc popover.
- Một màu hành động chính cho mỗi workspace. Màu success/warning/danger dành cho trạng thái, không dùng làm trang trí.
- Buyer có nhịp nhẹ và ấm hơn; Admin có mật độ dữ liệu cao hơn, bề mặt phẳng và độ tương phản rõ. Vẫn dùng token chung cho accessibility.
- Ảnh mèo và ảnh quét là nội dung thật; không thay bằng icon lớn hoặc gradient chỉ để lấp khoảng trống.

## 7. Motion và phản hồi

| Sự kiện | Chuyển động | Giới hạn |
|---|---|---|
| Chuyển tab/nhóm điều hướng | Marker/underline dịch hoặc đổi màu 120–180ms | Không chạy lại toàn bộ menu; không stagger từng item. |
| Chọn mèo để quét | Tên/ảnh cập nhật trong vùng preview, fade 120–160ms | Không che lựa chọn đang hoạt động. |
| Ảnh được chọn | Preview xuất hiện 180–240ms bằng opacity/transform | Giữ kích thước vùng ảnh để tránh layout shift. |
| Gửi và phân tích | Pha 1 dùng trạng thái chờ không xác định phần trăm vì `fetch` hiện không cung cấp upload progress; chỉ dùng phần trăm nếu backend/client thực sự có dữ liệu tiến độ | Không tạo progress giả hoặc animation vô hạn thu hút chú ý. |
| Kết quả sẵn sàng | Vùng kết quả hiện theo thứ tự: trạng thái → số liệu → việc tiếp theo, tổng ≤ 350ms | Không làm kết quả bị trì hoãn để chờ animation. |
| Admin hoàn tất việc | Hàng cập nhật trạng thái và số đếm, highlight ≤ 600ms | Có text thông báo tương ứng; không tự xóa item trước khi xác nhận server. |
| Mở chi tiết/drawer | Translate/opacity 180–240ms | Focus chuyển vào panel, Escape và Back hoạt động. |

Chỉ animate `transform` và `opacity` ở phần lớn trường hợp. Không gắn listener motion toàn cục lên mọi link/nút; không dùng `transition-all`. Với `prefers-reduced-motion: reduce`, bỏ chuyển động dịch/scale và vẫn giữ phản hồi trạng thái tức thời. Mobile cấu hình thấp phải được kiểm tra trực tiếp.

## 8. Hiệu năng và khả năng tiếp cận

- Đo baseline trước khi tối ưu: LCP, INP, CLS trên dashboard Buyer, luồng quét và một trang danh sách Admin; dùng thiết bị Android tầm trung và mạng chậm. Mục tiêu tham chiếu: LCP < 2,5 giây, INP < 200ms, CLS < 0,1 ở điều kiện thử đã ghi lại.
- Buyer dashboard tải vùng đầu tiên trước. Dữ liệu cho xu hướng/care tips và vùng dưới fold có thể trì hoãn sau khi đã giữ đúng hành vi offline/cache; nếu chưa kiểm chứng điều kiện đó thì giữ query hiện tại, chỉ đổi bố cục. Không làm mất trạng thái stale/error.
- Không tải lại toàn bộ dữ liệu khi chỉ đổi tab trình bày. Không remount menu Admin khi chuyển nhóm nếu không cần.
- Kiểm tra precache PWA hiện gom tất cả JS/CSS khớp glob; đặt ngân sách tải cài đặt và chiến lược cache cho chunk ít dùng, không làm mất hành vi offline đã có.
- Vùng chạm ≥ 44×44px; điều hướng bàn phím đầy đủ; focus rõ; heading đúng cấp; contrast văn bản thường tối thiểu 4.5:1, văn bản lớn 3:1.
- Bảng trên mobile không được phụ thuộc vào cuộn ngang để xem hoặc thực hiện hành động cốt lõi. Mọi icon hành động phải có nhãn accessible và, nếu cần đoán nghĩa, có nhãn nhìn thấy.
- Skeleton giữ kích thước gần nội dung thật. Empty/error states có hướng xử lý; không dùng spinner toàn màn cho một vùng dữ liệu lỗi.

## 9. Ma trận dữ liệu và phụ thuộc backend

| Nhu cầu UI | Dữ liệu hiện có | Ghi chú triển khai |
|---|---|---|
| Buyer Hôm nay | Cats, scan history/summary, health flags, reminders, entitlement, care tips | Cần chọn dữ liệu trên fold và tránh gọi tất cả ngay khi mount. |
| Quét và kết quả | `scan/config`, submit scan, scan by ID/request, analysis, recent scans, credits | Giữ quy tắc inconclusive không tiêu credit và idempotency request ID. |
| Onboarding | Server `onboarding.nextStep`, tạo mèo, khảo sát có `skipped`, kích hoạt mã | Không bỏ route/bước server trước khi hợp đồng thay đổi. |
| Admin KPI | `/admin/metrics/dashboard`, `/admin/system-status` | Chỉ phản ánh số liệu endpoint thực có. |
| Admin module công việc | Privacy requests, community reports, job runs, outbox, incidents | Dùng danh sách riêng có phân quyền trong giai đoạn đầu. |
| Hàng đợi Admin thống nhất | **Chưa có API tổng hợp** | Cần hợp đồng mới, paging/filter/role và định nghĩa ưu tiên trước khi hiển thị như một queue duy nhất. |

## 10. Kiểm thử và tiêu chí nghiệm thu

### 10.1 Kiểm thử tác vụ

Thử bản hiện tại và prototype mới với cùng kịch bản, tối thiểu 5 Buyer và 3 người có vai trò Admin phù hợp. Ghi thời gian, số bước, lỗi, điểm do dự và khả năng giải thích kết quả. Mục tiêu đề xuất cần xác nhận sau baseline:

- Buyer đã đăng nhập, có một mèo, đã cấp quyền camera: từ Hôm nay → đến camera/chọn ảnh trong tối đa 2 tương tác rõ ràng; không tính hộp thoại quyền của hệ điều hành.
- Buyer nhiều mèo: trước khi gửi, 100% người thử xác định đúng ảnh sẽ gán cho mèo/khay nào.
- Buyer sau kết quả: đa số người thử nêu đúng ý nghĩa chính và hành động tiếp theo mà không mở trang chi tiết.
- Admin: từ trang Công việc đến một yêu cầu cần xử lý trong tối đa 2 lần chọn.
- Admin: người thử phân biệt được thao tác chỉ xem, thay đổi được và khó hoàn tác; không thực hiện nhầm trên mẫu thử.

### 10.2 Tiêu chí kỹ thuật

- Không làm mất khả năng truy cập các URL và tác vụ hiện có.
- Không giảm ràng buộc role, reason, step-up, ETag hoặc xác nhận pháp lý.
- Mọi biến thể Guest/zero/one/multiple cats, loading/error, no-credit/inconclusive/success được thiết kế và test.
- Desktop 1440px, tablet 768px, mobile 360px và 390px không tràn ngang nội dung chính.
- Reduced motion, keyboard, screen reader, độ tương phản và vùng chạm đạt các quy tắc ở mục 8.
- Có kiểm thử E2E cho các hành trình Buyer và role Admin trọng tâm, test trạng thái mạng/API lỗi, và so sánh chỉ số hiệu năng trước/sau trên cùng điều kiện.

## 11. Gói triển khai theo thứ tự

Mỗi gói là một diff có thể review. Không bắt đầu gói sau khi gói trước còn lỗi build, test luồng hoặc lỗi quyền. Thiết kế prototype có ích nhưng không là điều kiện chặn code Pha 1; nếu chưa có người dùng thử, dùng mặc định trong tài liệu và ghi lại giả định.

| Gói | File chính | Kết quả bắt buộc | Kiểm tra tối thiểu |
|---|---|---|---|
| 0 — Khóa baseline | `src/app/styles/index.css`, `src/app/layouts/adminTheme.ts`, layout Buyer/Admin | Chốt token chữ/khoảng cách/radius; ghi ảnh và số đo hiện tại trước khi đổi. Không thêm thư viện. | Build, typecheck; ảnh 360/390/768/1440px. |
| 1 — Buyer Hôm nay | `src/pages/dashboard/DashboardPage.tsx`, `HomeGuide.tsx`, i18n liên quan | Một CTA chính theo trạng thái guest/zero/one/multiple cats; cảnh báo thật và kết quả gần nhất trước nội dung khám phá. Dùng cùng `catId` cho tên, scan và trend. | Unit test chọn mèo/trạng thái; E2E CTA của một và nhiều mèo. |
| 2 — Quét | `src/pages/scan/SelectCatPage.tsx`, `ScanPage.tsx`, `ScanResultPage.tsx`, `src/features/scan/store.ts` | CTA một mèo từ Hôm nay mở `/scan` với mèo đã đặt; nhiều mèo chưa chọn và link trực tiếp giữ màn chọn; kết quả hiển thị đủ thông tin chính; lỗi mạng không gây gửi trùng. | E2E một/nhiều mèo, shared tray, hết credit, inconclusive, lỗi mạng. |
| 3 — Admin trang đầu | `src/pages/admin/AdminDashboardPage.tsx`, `src/app/layouts/AdminLayout.tsx`, i18n Admin | Lối vào tác vụ lọc bằng capability và KPI hiện có; không có queue/count giả; tìm menu desktop; moderator không bị chuyển tới `/admin`. | Test role `ADMIN_SUPER`, `ADMIN_SUPPORT`, `ADMIN_CATALOG`, `DPO`, `MODERATOR`; mobile menu. |
| 4 — Mẫu danh sách Admin | `src/pages/admin/AdminUsersPage.tsx`, `AdminUserDetailPage.tsx`, `src/features/admin/dense.tsx` | Danh sách desktop đọc nhanh; mobile mở route chi tiết hiện có để khóa/mở người dùng, không buộc cuộn ngang bảng. Chuyển reason của khóa/mở sang lúc bắt đầu hành động; giữ tách riêng reason và quyền của thao tác xem PII/thu hồi phiên. Sau khi đạt tiêu chí mới áp dụng mẫu cho trang Admin khác. | Test filter/page/back, `ADMIN_SUPER` và role chỉ đọc, reason thiếu, lỗi API thực tế của endpoint. |
| 5 — Onboarding và motion | `src/pages/onboarding/*`, `src/shared/lib/motion/*`, CSS liên quan | Nhãn bước tùy chọn rõ; giữ request skip/consent; motion theo mục 7 cho scan và hành động Admin, có reduced-motion. | E2E onboarding skip/resume và quét; kiểm tra keyboard/reduced-motion. |

**Ranh giới Pha 2:** đổi hẳn nhóm/nhãn nav, font Buyer mới, queue Admin thống nhất, mở quét khay chung khi chưa có mèo, thay đổi điều khoản/cuộn bắt buộc và đổi URL chi tiết. Chỉ triển khai khi các quyết định/hợp đồng ở mục 12 đã được chốt.

### 11.1 Quy tắc bàn giao của mỗi gói

- Nêu màn/role đã đổi, ảnh trước/sau ở mobile và desktop, và danh sách trạng thái đã kiểm tra.
- Chạy `npm run typecheck`, `npm run build`, test liên quan. Nếu lint toàn repo có lỗi tồn tại sẵn, chạy lint cho file đã sửa và liệt kê lỗi còn lại; không âm thầm bỏ qua lỗi mới.
- Không xóa test hợp đồng cũ để làm test mới qua. Không sửa API/backend ngoài gói chỉ vì UI cần thêm dữ liệu.
- Với thay đổi có phân nhánh logic, thêm ít nhất một test cho trường hợp dễ gán sai mèo hoặc lộ hành động sai quyền.
- Nếu một yêu cầu không thể làm với API hiện có, giữ hành vi an toàn đang chạy và ghi `BLOCKED_BY_API` cùng endpoint/field thiếu trong báo cáo bàn giao; không dựng fixture giả trong production.

## 12. Mặc định an toàn và quyết định Pha 2

| Vấn đề | Mặc định Pha 1, agent phải làm theo | Điều kiện để đổi ở Pha 2 |
|---|---|---|
| Quét khay chung khi chưa có mèo | Giữ CTA thêm mèo hiện tại. | Chủ sản phẩm và backend xác nhận `SHARED_UNKNOWN` hợp lệ ở trạng thái này. |
| Chọn sẵn mèo | Chỉ đặt sẵn khi CTA Hôm nay đã có lựa chọn rõ, đặc biệt trường hợp một mèo; link mở quét trực tiếp vẫn cần chọn nếu store trống. | Test người dùng xác nhận auto-select nhiều mèo không gây gán nhầm. |
| Disclaimer bắt cuộn | Giữ cơ chế và nội dung hiện tại. | Pháp lý/chủ sản phẩm xác nhận thay thế được. |
| Admin queue có thứ tự ưu tiên | Chỉ dùng lối vào module và KPI thật. | Backend/chủ sản phẩm thống nhất `priority`, `dueAt`, role và API tổng hợp. |
| Nhãn điều hướng | Giữ nhãn/route đang chạy trong Pha 1, cải thiện thứ bậc và số nút mobile. | Kiểm thử tìm tác vụ và rà analytics/tài liệu hỗ trợ. |
| Font Buyer | Giữ Nunito trong Pha 1, sửa thang chữ và trọng lượng; Admin dùng system font. | Kiểm thử mẫu chữ tiếng Việt và chốt asset self-host mới. |

