-- ===========================================================================
-- V40: Chỉnh nội dung kho tri thức V39 cho khớp hệ thống và bỏ khẳng định y khoa.
-- - Ngưỡng pH theo ph_classification_band (V9): trong khoảng tham chiếu 6,3–6,6.
-- - Bỏ tên màu cụ thể: màu thật phụ thuộc bảng màu đã hiệu chuẩn (V9 mới là placeholder).
-- - Bỏ việc suy ra nguy cơ sỏi/bệnh từ kết quả quét, bỏ tên thương hiệu sản phẩm.
-- - Hướng dẫn quét khớp app (thẻ màu tham chiếu, 15–25 cm, 5–15 phút).
-- Sửa bằng migration mới thay vì sửa V39 vì V39 đã chạy trên một số DB.
-- Chỉ mục RAG tự đồng bộ theo content_hash khi app khởi động (AiKnowledgeIndexer).
-- ===========================================================================

-- 1) Các bài viết lại toàn bộ.
UPDATE care_tip t
SET title            = v.title,
    summary          = v.summary,
    body_md          = v.body_md,
    tags             = v.tags::jsonb,
    claim_type       = v.claim_type,
    source_reference = v.source_reference,
    updated_at       = now()
FROM (VALUES
    -- 1. Các dải pH trên CatCheck
    ('bang-mau-ph-catcheck-va-y-nghia',
     'Các dải pH trên CatCheck và ý nghĩa của từng dải',
     'CatCheck xếp mỗi lần quét vào một dải pH tham chiếu (6,3–6,6 là trong khoảng). Bài này giải thích từng dải và vì sao một lần lệch đơn lẻ thường chưa nói lên điều gì.',
     $$# Các dải pH trên CatCheck và ý nghĩa của từng dải

Cát CatCheck chứa hạt chỉ thị đổi màu theo độ pH của nước tiểu. Ứng dụng đọc màu hạt cát, đối chiếu với thẻ màu tham chiếu in kèm để bù sai lệch do ánh sáng và camera, rồi xếp kết quả vào một dải pH. Màu bạn nhìn thấy bằng mắt có thể khác nhau tùy ánh sáng và lô cát, vì vậy hãy dựa vào kết quả ứng dụng đọc được thay vì tự đoán theo màu.

## 1. Các dải pH

- **Trong khoảng tham chiếu (pH 6,3 – 6,6).**
- **Hơi thấp (pH 6,0 – dưới 6,3):** thiên axit nhẹ. Thường không đáng lo nếu chỉ xảy ra một lần.
- **Thấp rõ rệt (dưới 6,0):** lệch khá nhiều về phía axit. Nên theo dõi thêm vài lần trong 1–2 ngày tới.
- **Hơi cao (trên 6,6 – 7,0):** thiên kiềm nhẹ. Hay gặp trong vài giờ sau bữa ăn (xem bài về hiện tượng kiềm hóa sau ăn).
- **Cao rõ rệt (trên 7,0):** lệch khá nhiều về phía kiềm. Nên theo dõi thêm vài lần trong 1–2 ngày tới.
- **Không kết luận được:** ảnh chưa đủ rõ để đọc màu tin cậy (thiếu thẻ màu, thiếu sáng, bị lóa). Hãy quét lại.

Kết quả nằm sát mốc 6,3 hoặc 6,6 được đánh dấu "gần ranh giới", vì sai số đo có thể đưa nó sang dải bên cạnh.

## 2. pH cho biết gì và không cho biết gì

pH nước tiểu của mèo dao động trong ngày theo bữa ăn, lượng nước uống và thời điểm đi tiểu. Một lần đo nằm ngoài khoảng tham chiếu thường chưa nói lên điều gì. Đáng chú ý hơn là các lần đo lặp lại cùng lệch về một phía, hoặc lệch xa so với mức thường thấy của chính bé.

pH từ màu cát **không cho biết bé có bệnh hay không, và không xác định được tinh thể hay sỏi**. Những điều đó chỉ bác sĩ thú y xác định được qua thăm khám và xét nghiệm.

## 3. Liên hệ thú y ngay, bất kể chỉ số pH, nếu bé:

- Rặn tiểu, ra vào khay liên tục nhưng chỉ ra vài giọt hoặc không ra nước tiểu.
- Kêu đau khi đi vệ sinh.
- Có vệt đỏ, hồng hoặc nâu trên cát hay trong nước tiểu.
- Bỏ ăn, lừ đừ hoặc nôn.

## 4. Lưu ý khi quét

- Quét trong vòng 5–15 phút sau khi bé đi vệ sinh. Cát để lâu ngoài không khí có thể ngả dần về phía kiềm, làm kết quả sai lệch.
- Luôn đặt thẻ màu tham chiếu trong khung hình.$$,
     '["ph", "dai-ph", "chi-thi", "theo-doi", "suc-khoe"]',
     'MEDICAL', 'Tài liệu Thú y: Hướng dẫn phân tích nước tiểu và sinh lý tiết niệu mèo (AAHA / WSAVA)'),

    -- 2. Khi chỉ số pH lệch khỏi khoảng tham chiếu
    ('xu-ly-khi-ph-thay-doi-bat-thuong',
     'Cần làm gì khi chỉ số pH lệch khỏi khoảng tham chiếu?',
     'Các bước nên làm khi kết quả quét nằm ngoài khoảng 6,3–6,6: loại trừ lỗi chụp, quan sát bé, rà soát ăn uống và theo dõi thêm.',
     $$# Cần làm gì khi chỉ số pH lệch khỏi khoảng tham chiếu?

Khi kết quả quét nằm ngoài khoảng tham chiếu 6,3–6,6, bạn không cần hoảng nhưng cũng đừng bỏ qua. Hãy làm theo các bước sau.

## Bước 1: Loại trừ lỗi chụp
- Bạn có quét trong vòng 5–15 phút sau khi bé đi vệ sinh không? Cát để lâu có thể ngả về phía kiềm.
- Thẻ màu tham chiếu có nằm trong khung hình không? Ánh sáng có đủ trắng, không bị bóng tay hay lóa đèn flash không?
- Nếu kết quả là "không kết luận được" hoặc "gần ranh giới", hãy quét lại ở lần đi vệ sinh tiếp theo.

## Bước 2: Quan sát hành vi đi vệ sinh của bé
- Đi vào khay nhiều lần nhưng mỗi lần chỉ ra vài giọt.
- Rặn tiểu lâu, kêu khi ngồi trong khay.
- Liếm vùng sinh dục liên tục sau khi tiểu.
- Đi tiểu ra ngoài khay.

Nếu bé có một trong các dấu hiệu này, hãy liên hệ bác sĩ thú y ngay, không chờ kết quả quét tiếp theo. Rặn tiểu mà không ra nước tiểu là tình huống cấp cứu.

## Bước 3: Rà soát ăn uống trong 48 giờ qua
- Bé có vừa ăn no trong vài giờ trước khi quét không? pH thường tăng nhẹ sau bữa ăn (xem bài về hiện tượng kiềm hóa sau ăn).
- Bé có vừa đổi thức ăn không?
- Lượng nước bé uống gần đây có giảm không?

## Bước 4: Theo dõi thêm trong 24–48 giờ
Quét thêm 2–3 lần trong 24–48 giờ tới. CatCheck có thể hiện ghi chú theo dõi khi:
- có từ 2 lần đo trong 48 giờ cùng lệch về một phía;
- chỉ số lệch từ 0,5 trở lên so với mức thường thấy của chính bé trong 14 ngày;
- 3 lần quét liên tiếp cùng tăng hoặc cùng giảm.

## Bước 5: Mang dữ liệu theo khi đi khám
Nếu chỉ số lệch kéo dài hoặc bạn thấy lo, hãy dùng "Xuất hồ sơ PDF" để mang lịch sử quét đến bác sĩ thú y. Đây là dữ liệu tham khảo, không thay thế xét nghiệm nước tiểu tại phòng khám.$$,
     '["ph", "bat-thuong", "huong-dan", "theo-doi", "bac-si-thu-y"]',
     'MEDICAL', 'Quy chuẩn theo dõi bệnh học tại nhà cho chủ thú cưng (ISFM Cat Friendly Clinic Guidelines)'),

    -- 4. Sỏi Struvite và Canxi Oxalate
    ('soi-struvite-va-soi-canxi-oxalate',
     'Sỏi Struvite và sỏi Canxi Oxalate ở mèo: những điều chủ nuôi nên biết',
     'Hai loại sỏi tiết niệu thường gặp nhất ở mèo, vì sao chỉ bác sĩ thú y mới xác định được loại sỏi, và pH từ màu cát nằm ở đâu trong bức tranh này.',
     $$# Sỏi Struvite và sỏi Canxi Oxalate ở mèo: những điều chủ nuôi nên biết

Phần lớn sỏi đường tiết niệu dưới ở mèo thuộc hai loại: **Struvite** (magie amoni photphat) và **Canxi Oxalate**. Hai loại này có đặc điểm và hướng xử lý khác nhau, nên việc xác định đúng loại sỏi rất quan trọng.

## 1. Sỏi Struvite
- Liên quan đến nước tiểu có tính kiềm và cô đặc, cùng nồng độ magie, amoni, photphat cao.
- Một số trường hợp có thể được bác sĩ thú y điều trị bằng chế độ ăn trị liệu theo chỉ định, kèm tái khám để kiểm tra.

## 2. Sỏi Canxi Oxalate
- Liên quan đến nước tiểu có tính axit và cô đặc.
- Không tan bằng chế độ ăn. Bác sĩ thú y sẽ quyết định cách xử lý tùy kích thước và vị trí sỏi.

## 3. Chỉ bác sĩ thú y mới xác định được loại sỏi
Không thể biết mèo có sỏi hay không, hay sỏi thuộc loại nào, chỉ dựa vào triệu chứng hoặc độ pH. Bác sĩ thú y cần thăm khám, xét nghiệm nước tiểu và chẩn đoán hình ảnh.

## 4. pH từ màu cát nằm ở đâu
pH nước tiểu chỉ là một trong nhiều yếu tố liên quan đến việc hình thành tinh thể. Mèo có pH trong khoảng tham chiếu vẫn có thể có sỏi, và pH lệch cũng không có nghĩa là bé có sỏi. Vì vậy CatCheck **không dùng pH để đánh giá nguy cơ sỏi** của bé. Giá trị của việc theo dõi là thấy được thay đổi so với mức thường ngày của bé, và có dữ liệu để mang theo khi đi khám.

Nếu bé đã được chẩn đoán sỏi, hãy hỏi bác sĩ thú y mức pH mục tiêu và tần suất theo dõi phù hợp cho bé.$$,
     '["struvite", "oxalate", "soi-tiet-nieu", "ph", "bac-si-thu-y"]',
     'MEDICAL', 'Phác đồ quản lý sỏi niệu ở thú nhỏ (ACVIM Consensus Statement on Urolithiasis)'),

    -- 7. Hướng dẫn chụp ảnh đọc màu
    ('huong-dan-chup-anh-doc-mau-chuan',
     'Làm sao chụp ảnh khay cát để đọc màu hạt chỉ thị chuẩn nhất?',
     'Thời điểm, thẻ màu tham chiếu, ánh sáng và khoảng cách giúp CatCheck đọc màu hạt cát ổn định hơn.',
     $$# Làm sao chụp ảnh khay cát để đọc màu hạt chỉ thị chuẩn nhất?

CatCheck đọc màu hạt chỉ thị trong ảnh và đối chiếu với thẻ màu tham chiếu in kèm để bù sai lệch do ánh sáng và camera. Ảnh càng rõ, kết quả càng đáng tin. Hãy làm theo các nguyên tắc sau.

## 1. Thời điểm: trong vòng 5–15 phút sau khi bé đi tiểu
- Hạt cát cần một chút thời gian để đổi màu hoàn toàn.
- Tránh quét quá muộn: nước tiểu để lâu ngoài không khí có thể bị phân hủy tạo amoniac, làm hạt cát ngả dần về phía kiềm và cho kết quả sai lệch.

## 2. Luôn đặt thẻ màu tham chiếu trong khung hình
Thẻ màu giúp ứng dụng hiệu chỉnh màu theo điều kiện chụp thực tế. Thiếu thẻ màu, kết quả có độ tin cậy thấp hơn hoặc không kết luận được.

## 3. Ánh sáng
- Ưu tiên ánh sáng trắng tự nhiên hoặc đèn phòng ánh sáng trắng.
- Tránh đèn vàng ấm: màu ánh sáng có thể làm lệch màu hạt cát.
- Tránh bật flash chiếu thẳng: hạt cát bóng sẽ bị lóa, mất chi tiết màu.
- Không để bóng tay hoặc điện thoại đổ lên vùng cát.

## 4. Góc chụp và khoảng cách
- Cầm điện thoại song song với mặt cát.
- Giữ khoảng cách 15–25 cm.
- Lấy nét vào vùng cát đã thấm nước tiểu, tránh vùng cát khô.

Nếu nhiều lần quét liên tiếp có độ tin cậy thấp, ứng dụng sẽ nhắc bạn kiểm tra lại các bước trên.$$,
     '["chup-anh", "doc-mau", "scan", "huong-dan", "the-mau"]',
     'NONE', NULL),

    -- 8. Kiềm hóa sinh lý sau ăn
    ('kiem-hoa-sau-an-alkaline-tide',
     'Hiện tượng kiềm hoá sinh lý sau ăn (Alkaline Tide) ở mèo',
     'Vì sao chỉ số pH của mèo có thể lệch lên phía kiềm trong vài giờ sau bữa ăn, và khi nào nên để ý hơn.',
     $$# Hiện tượng kiềm hoá sinh lý sau ăn (Alkaline Tide) ở mèo

Nhiều chủ nuôi lo lắng khi thấy kết quả quét lệch lên phía kiềm ngay sau bữa ăn của bé. Đây rất có thể là hiện tượng sinh lý tự nhiên gọi là **kiềm hóa sau ăn (Alkaline Tide)**.

## 1. Cơ chế
Sau bữa ăn, dạ dày tiết nhiều axit clohydric (HCl) để tiêu hóa thức ăn. Cùng lúc đó, tế bào thành dạ dày đưa ion bicarbonate (có tính kiềm) vào máu. Thận đào thải phần bicarbonate dư qua nước tiểu, khiến pH nước tiểu tăng tạm thời trong vài giờ sau ăn, rồi trở lại mức thường ngày.

## 2. Khi nào thường là bình thường, khi nào nên để ý hơn
- **Thường là hiện tượng sinh lý nếu:**
  - Chỉ số lệch lên phía kiềm trong vài giờ sau bữa ăn chính.
  - Lần quét lúc bé đói (ví dụ sáng sớm trước bữa ăn) trở về mức thường ngày.
  - Bé vẫn ăn uống, chơi đùa và đi vệ sinh bình thường.
- **Nên để ý hơn nếu:**
  - Chỉ số cao kéo dài qua nhiều lần quét, kể cả lúc bé đói.
  - Kèm theo dấu hiệu như đi tiểu nhiều lần, rặn tiểu, nước tiểu có mùi nặng bất thường hoặc có vệt máu. Khi đó hãy liên hệ bác sĩ thú y; chỉ bác sĩ mới xác định được nguyên nhân.

## 3. Lời khuyên
- Ghi lại giờ ăn của bé. Khi so sánh các lần quét, hãy so ở cùng thời điểm so với bữa ăn.
- Đừng vội đổi thức ăn hay dùng thuốc chỉ vì một lần quét sau bữa ăn lệch lên phía kiềm.$$,
     '["alkaline-tide", "kiem-hoa", "sau-an", "ph", "sinh-ly"]',
     'MEDICAL', 'Sinh lý học tiêu hóa và bài tiết của động vật ăn thịt (Veterinary Clinics of North America: Small Animal Practice)'),

    -- 13. Đọc biểu đồ xu hướng pH
    ('huong-dan-theo-doi-bieu-do-xu-huong-ph',
     'Cách đọc biểu đồ xu hướng pH trên ứng dụng CatCheck',
     'Đọc biểu đồ pH theo các dải tham chiếu của CatCheck, hiểu khi nào ứng dụng hiện ghi chú theo dõi và nên quét bao lâu một lần.',
     $$# Cách đọc biểu đồ xu hướng pH trên ứng dụng CatCheck

CatCheck lưu lại mỗi lần quét và vẽ biểu đồ pH theo thời gian. Nhìn xu hướng giúp bạn hiểu bé rõ hơn so với chỉ nhìn một lần quét.

## 1. Vì sao xu hướng quan trọng hơn một lần quét
pH nước tiểu của mèo dao động tự nhiên trong ngày: thường thấp hơn lúc bé đói và tăng nhẹ trong vài giờ sau bữa ăn. Điều kiện chụp của từng lần cũng có thể làm kết quả xê dịch. Vì vậy, một lần đo hơi cao hay hơi thấp không có nghĩa là bé có vấn đề.

## 2. Các dải trên biểu đồ
- **Trong khoảng tham chiếu:** pH 6,3 – 6,6.
- **Hơi thấp / hơi cao:** pH 6,0 – dưới 6,3, hoặc trên 6,6 – 7,0.
- **Thấp rõ rệt / cao rõ rệt:** dưới 6,0, hoặc trên 7,0.

## 3. Khi nào CatCheck hiện ghi chú theo dõi
- **Lặp lại ngoài khoảng:** từ 2 lần đo trong 48 giờ cùng lệch về một phía.
- **Khác với thường ngày:** chỉ số lệch từ 0,5 trở lên so với mức thường thấy của chính bé trong 14 ngày (cần đủ dữ liệu nền).
- **Xu hướng một chiều:** 3 lần quét liên tiếp cùng tăng hoặc cùng giảm.

Những lần quét có độ tin cậy thấp, không kết luận được hoặc nằm sát ranh giới không được tính. Các ghi chú này là quan sát từ màu cát, không phải chẩn đoán.

## 4. Nên quét bao lâu một lần
- Để ứng dụng có đủ dữ liệu nền của bé, hãy quét khoảng 3 lần mỗi tuần, vào thời điểm tương tự nhau so với bữa ăn.
- Khi vừa đổi thức ăn hoặc khi có ghi chú theo dõi: quét mỗi ngày một lần trong vài ngày.
- Nếu bé đang được bác sĩ thú y theo dõi, hãy quét theo tần suất bác sĩ khuyên.

## 5. Mang dữ liệu theo khi đi khám
Dùng "Xuất hồ sơ PDF" để mang lịch sử quét đến bác sĩ thú y. Đây là dữ liệu tham khảo, không thay thế xét nghiệm tại phòng khám.$$,
     '["bieu-do", "xu-huong", "app", "catcheck", "theo-doi"]',
     'NONE', NULL),

    -- 14. Chăm sóc sau điều trị sỏi
    ('cham-soc-meo-sau-khi-dieu-tri-soi',
     'Chăm sóc và phòng ngừa tái phát sau khi mèo điều trị sỏi tiết niệu',
     'Chăm sóc dài hạn sau khi mèo điều trị sỏi: làm đúng chỉ định của thú y, giúp bé uống đủ nước và dùng CatCheck để ghi lại diễn biến.',
     $$# Chăm sóc và phòng ngừa tái phát sau khi mèo điều trị sỏi tiết niệu

Sỏi tiết niệu ở mèo có thể tái phát, vì vậy giai đoạn chăm sóc sau điều trị rất quan trọng. Kế hoạch cụ thể cho bé luôn do bác sĩ thú y điều trị đặt ra; các gợi ý dưới đây giúp bạn thực hiện kế hoạch đó tốt hơn.

## 1. Tuân thủ chế độ ăn theo chỉ định
- Nếu bác sĩ thú y chỉ định thức ăn trị liệu, hãy cho ăn đúng loại và đúng lượng.
- Không cho ăn xen thức ăn khác, đồ ăn vặt hay thức ăn của người khi chưa hỏi bác sĩ, vì có thể làm giảm hiệu quả của chế độ ăn trị liệu.
- Không tự ý ngừng hoặc đổi thức ăn trị liệu.

## 2. Giúp bé uống đủ nước
- Nước tiểu loãng giúp giảm nồng độ khoáng chất có thể kết tinh.
- Dùng đài phun nước, bố trí nhiều bát nước và bổ sung thức ăn ướt nếu bác sĩ đồng ý.

## 3. Dùng CatCheck để ghi lại diễn biến
- Hỏi bác sĩ thú y mức pH mục tiêu (nếu có) và tần suất theo dõi phù hợp cho bé.
- Quét đều đặn và lưu lịch sử trên ứng dụng. Dùng "Xuất hồ sơ PDF" để mang theo mỗi lần tái khám.
- Kết quả pH từ màu cát không cho biết sỏi đã tan hay đã tái phát. Chỉ tái khám mới xác định được.

## 4. Tái khám đúng hẹn
- Đưa bé tái khám theo lịch bác sĩ hẹn.
- Liên hệ thú y ngay nếu bé rặn tiểu, ra vào khay liên tục, chỉ ra vài giọt hoặc không ra nước tiểu, hoặc nước tiểu có máu.$$,
     '["phuc-hoi", "tai-phat", "soi-tiet-nieu", "bac-si-thu-y", "theo-doi"]',
     'MEDICAL', 'Phác đồ kiểm soát và phòng ngừa tái phát bệnh tiết niệu ở thú nhỏ (World Small Animal Veterinary Association)')
) AS v(slug, title, summary, body_md, tags, claim_type, source_reference)
WHERE t.slug = v.slug
  AND t.locale = 'vi';

-- 2) Các bài chỉ cần sửa vài câu.
-- 5. FIC: bỏ khẳng định quá mức, CatCheck không đo được máu, bỏ tên thương hiệu.
UPDATE care_tip
SET body_md = replace(replace(replace(body_md,
        'Các nghiên cứu hiện đại đã chứng minh FIC thực chất là một phản ứng tâm lý - thần kinh phức tạp do **căng thẳng (stress)** gây ra.',
        '**Căng thẳng (stress)** được xem là một yếu tố quan trọng liên quan đến FIC, thông qua tương tác phức tạp giữa hệ thần kinh, nội tiết và bàng quang.'),
        'Nước tiểu có màu hồng nhạt, đỏ tươi hoặc hạt cát CatCheck đổi màu hồng đậm.',
        'Nước tiểu hoặc vệt trên cát có màu hồng, đỏ hoặc nâu.'),
        'Sử dụng máy xông Feliway Classic để tạo cảm giác an tâm cho mèo trong không gian sống.',
        'Sản phẩm pheromone dành cho mèo có thể giúp một số bé thấy an tâm hơn; hãy hỏi bác sĩ thú y trước khi dùng.')
        || E'\n\nNếu bé có các dấu hiệu trên, hãy đưa bé đi khám thú y. Chỉ bác sĩ mới xác định được nguyên nhân.',
    updated_at = now()
WHERE slug = 'hoi-chung-flutd-va-fic' AND locale = 'vi';

-- 12. Stress: bỏ tên thương hiệu.
UPDATE care_tip
SET body_md = replace(body_md,
        'Cắm máy xông tinh dầu Pheromone Feliway trong phòng ngủ để tạo tín hiệu an toàn và thư thái cho mèo.',
        'Sản phẩm pheromone dành cho mèo có thể giúp một số bé thấy an tâm hơn; hãy hỏi bác sĩ thú y trước khi dùng.'),
    updated_at = now()
WHERE slug = 'tam-ly-va-stress-anh-huong-tiet-nieu' AND locale = 'vi';

-- 6, 11: bài claim_type NONE (không có nguồn) không được khẳng định nhân quả y khoa.
UPDATE care_tip
SET body_md = replace(body_md,
        'Nhịn tiểu là nguyên nhân trực tiếp dẫn đến kết tinh sỏi và viêm bàng quang.',
        'Nhịn tiểu lâu không tốt cho đường tiết niệu của bé.'),
    updated_at = now()
WHERE slug = 'quy-tac-ve-sinh-va-thay-cat' AND locale = 'vi';

UPDATE care_tip
SET body_md = replace(body_md,
        'dẫn đến việc phải nhịn tiểu lâu ngày gây lắng đọng sỏi Struvite.',
        'dẫn đến việc phải nhịn tiểu lâu ngày, điều không tốt cho đường tiết niệu của bé.'),
    updated_at = now()
WHERE slug = 'so-luong-va-vi-tri-dat-khay-cat' AND locale = 'vi';
