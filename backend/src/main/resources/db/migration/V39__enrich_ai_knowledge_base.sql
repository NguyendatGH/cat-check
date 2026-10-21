-- ===========================================================================
-- V39: Làm giàu kho tri thức RAG (Enrich AI Knowledge Base)
-- Bổ sung 14 tài liệu chuyên sâu về sức khỏe tiết niệu, dải màu pH cát CatCheck,
-- sỏi Struvite/Oxalate, hội chứng FLUTD/FIC, hydration và hướng dẫn sử dụng.
-- Tuân thủ bất biến I31 (claim_type <> NONE yêu cầu source_reference >= 10 ký tự).
-- ===========================================================================

INSERT INTO care_tip (
    id, slug, translation_group, locale, kind, category, title, summary, body_md,
    tags, status, claim_type, source_reference, sort_weight, published_at
)
VALUES
    -- 1. Bảng màu pH CatCheck và ý nghĩa
    (uuidv7(), 'bang-mau-ph-catcheck-va-y-nghia', uuidv7(), 'vi', 'ARTICLE', 'GENERAL',
     'Bảng dải màu chỉ thị pH của cát CatCheck và ý nghĩa sức khỏe',
     'Hướng dẫn tra cứu các mức pH từ dải màu hạt cát CatCheck: màu vàng bình thường (pH 6.0-6.5), xanh lá trung tính, xanh dương kiềm cao và đỏ axit.',
     $$# Bảng dải màu chỉ thị pH của cát CatCheck và ý nghĩa sức khỏe

Công nghệ cát vệ sinh thông minh CatCheck sử dụng các hạt silica tích hợp chất chỉ thị màu pH nhạy cảm, có khả năng phản ứng tức thì khi tiếp xúc với nước tiểu của mèo. Việc theo dõi màu sắc hạt cát giúp phát hiện sớm các bất thường về nồng độ pH – một trong những chỉ số cảnh báo sớm quan trọng nhất đối với các bệnh lý đường tiết niệu ở mèo (FLUTD).

## 1. Dải màu chuẩn và ngưỡng pH

Nước tiểu của một chú mèo hoàn toàn khỏe mạnh thường có tính axit nhẹ, dao động tối ưu trong khoảng **pH 6.0 đến 6.5**. Dưới đây là bảng quy đổi màu sắc hạt cát CatCheck:

- **Màu Vàng chanh đến Vàng tươi (pH 6.0 – 6.5) – Vùng Lý Tưởng:**
  Đây là mức pH sinh lý bình thường của mèo khỏe mạnh. Ở ngưỡng này, nước tiểu có tính axit vừa đủ để ức chế sự kết tinh của tinh thể sỏi Struvite mà không làm tăng nguy cơ lắng đọng tinh thể Canxi Oxalate.
- **Màu Xanh lá nhạt (pH 6.6 – 7.0) – Vùng Cần Theo Dõi:**
  Nước tiểu trung tính đến hơi kiềm. Hiện tượng này thường xảy ra tự nhiên sau bữa ăn giàu protein (hiện tượng kiềm hóa sinh lý Alkaline Tide) hoặc khi mèo uống nhiều nước. Nếu màu xanh lá chỉ xuất hiện thoáng qua và trở lại màu vàng ở lần đi tiểu tiếp theo, bạn không cần lo lắng.
- **Màu Xanh lục đậm đến Xanh dương (pH > 7.0 – 8.0) – Vùng Cảnh Báo Kiềm:**
  Nước tiểu có tính kiềm cao kéo dài. Đây là môi trường thuận lợi nhất để tinh thể **Struvite (Magnesium Ammonium Phosphate)** kết tủa và hình thành sỏi bàng quang hoặc nút nhầy niệu đạo. Nguyên nhân có thể do nhiễm trùng đường tiết niệu, thức ăn chứa nhiều tinh bột/đạm thực vật, hoặc mèo nhịn tiểu quá lâu.
- **Màu Cam đậm đến Đỏ cam (pH < 6.0) – Vùng Cảnh Báo Axit:**
  Nước tiểu có tính axit cao. Môi trường này làm tăng nguy cơ kết tinh sỏi **Canxi Oxalate (Calcium Oxalate)**. Loại sỏi này không thể tự hòa tan bằng thức ăn và nguy cơ gây tổn thương bàng quang rất cao.
- **Vệt Đỏ tươi, Hồng đậm hoặc Nâu sẫm – Cảnh Báo Nguy Cấp:**
  Dấu hiệu cho thấy có sự xuất hiện của hồng cầu trong nước tiểu (tiểu máu - Hematuria), viêm bàng quang xuất huyết cấp tính hoặc chấn thương niệu đạo. Bạn cần liên hệ ngay cơ sở thú y gần nhất.

## 2. Lưu ý khi quan sát màu cát

- Hãy quan sát và chụp ảnh phân tích trong vòng **5 đến 15 phút** sau khi mèo đi vệ sinh.
- Không để cát đã thấm nước tiểu qua đêm ngoài không khí vì vi khuẩn môi trường sẽ phân hủy ure thành amoniac, làm tăng pH giả tạo sang màu xanh dương.$$,
     '["ph", "bang-mau", "chi-thi", "struvite", "oxalate", "suc-khoe"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Tài liệu Thú y: Hướng dẫn phân tích nước tiểu và sinh lý tiết niệu mèo (AAHA / WSAVA)', 60, now()),

    -- 2. Xử lý khi pH thay đổi bất thường
    (uuidv7(), 'xu-ly-khi-ph-thay-doi-bat-thuong', uuidv7(), 'vi', 'TIP', 'GENERAL',
     'Cần làm gì khi màu pH của cát thay đổi bất thường?',
     'Quy trình 5 bước xử lý khoa học khi phát hiện hạt cát CatCheck đổi màu khác với mức thường ngày của bé mèo.',
     $$# Cần làm gì khi màu pH của cát thay đổi bất thường?

Khi thấy hạt cát CatCheck ngả sang màu xanh dương (kiềm) hoặc cam đỏ (axit), người nuôi mèo không nên quá hoảng sợ nhưng cũng không được chủ quan. Hãy thực hiện quy trình kiểm tra 5 bước sau:

## Bước 1: Kiểm tra lại yếu tố môi trường và kỹ thuật
- Thời gian chụp: Bạn có chụp ảnh trong vòng 5-15 phút sau khi bé đi vệ sinh không? Cát để lâu ngoài không khí trên 30-60 phút thường chuyển sang màu xanh do amoniac tự nhiên.
- Ánh sáng: Đảm bảo chụp dưới ánh sáng trắng tự nhiên, không bị đèn vàng ấm hoặc bóng tay làm sai lệch màu sắc nhận diện.

## Bước 2: Quan sát hành vi đi vệ sinh của mèo
Kiểm tra xem bé có bất kỳ dấu hiệu khó chịu nào sau đây không:
- Đi vào khay cát nhiều lần nhưng mỗi lần chỉ ra vài giọt.
- Rặn tiểu lâu, kêu gào hoặc gầm gừ khi ngồi trong khay.
- Liếm bộ phận sinh dục liên tục sau khi tiểu.
- Đi tiểu ra ngoài khay vệ sinh (trên thảm, giường, góc nhà).

## Bước 3: Rà soát lại chế độ ăn và nước uống trong 48 giờ qua
- Bé có vừa ăn một bữa no cách đây 2-4 tiếng không? (Xem bài hiện tượng Alkaline Tide).
- Bé có bị đổi loại hạt thức ăn mới đột ngột không?
- Lượng nước bé uống trong 1-2 ngày gần đây có bị giảm sút không?

## Bước 4: Theo dõi liên tục 2-3 lần đi vệ sinh tiếp theo
Chụp ảnh và lưu kết quả trên ứng dụng CatCheck trong 24 đến 48 giờ tới. Một lần đổi màu đơn lẻ có thể chỉ là dao động sinh lý tạm thời. Nhưng nếu màu cát duy trì ở mức kiềm (xanh dương) hoặc axit (cam đỏ) liên tục từ 3 lần đo trở lên, đây là xu hướng bất thường thực sự.

## Bước 5: Tham vấn bác sĩ thú y với hồ sơ CatCheck
Nếu màu bất thường kéo dài quá 48 giờ hoặc bé có biểu hiện đau đớn, hãy sử dụng tính năng "Xuất hồ sơ sức khỏe" trên ứng dụng CatCheck để gửi lịch sử theo dõi trực quan cho bác sĩ thú y. Điều này giúp bác sĩ chẩn đoán chính xác nguyên nhân mà không phải phỏng đoán.$$,
     '["ph", "bat-thuong", "huong-dan", "theo-doi", "bac-si-thu-y"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Quy chuẩn theo dõi bệnh học tại nhà cho chủ thú cưng (ISFM Cat Friendly Clinic Guidelines)', 70, now()),

    -- 3. Mẹo giúp mèo uống nhiều nước
    (uuidv7(), 'meo-giup-meo-uong-nhieu-nuoc', uuidv7(), 'vi', 'TIP', 'HYDRATION',
     '7 mẹo khoa học giúp mèo uống nhiều nước hơn mỗi ngày',
     'Bí quyết đáp ứng nhu cầu 50-60ml nước/kg thể trọng cho mèo bằng đài phun, thức ăn ướt và bố trí bát nước thông minh.',
     $$# 7 mẹo khoa học giúp mèo uống nhiều nước hơn mỗi ngày

Tổ tiên của mèo nhà là mèo rừng châu Phi (Felis lybica) sống ở sa mạc khô hạn, tiến hóa để lấy phần lớn lượng nước từ con mồi tươi sống. Do đó, mèo có cơ chế cảm nhận cơn khát rất kém và nước tiểu cô đặc tự nhiên. Khi chuyển sang ăn hạt khô (chỉ chứa 8-10% độ ẩm), mèo rất dễ bị thiếu nước mạn tính, dẫn đến lắng đọng sỏi tiết niệu và suy thận.

## Nhu cầu nước tiêu chuẩn của mèo
Một chú mèo trưởng thành cần trung bình **50 đến 60 ml nước cho mỗi 1 kg thể trọng mỗi ngày**. 
Ví dụ:
- Mèo nặng 3 kg cần khoảng 150 - 180 ml nước/ngày.
- Mèo nặng 4 kg cần khoảng 200 - 240 ml nước/ngày.
- Mèo nặng 5 kg cần khoảng 250 - 300 ml nước/ngày.

## 7 mẹo tăng lượng nước uống hiệu quả

1. **Sử dụng đài phun nước tự động:**
   Mèo bị kích thích bởi âm thanh và chuyển động của dòng nước chảy. Nước trong đài phun tuần hoàn qua màng lọc than hoạt tính sẽ sạch hơn, giàu oxy hòa tan và mát hơn nước để trong bát tĩnh.
2. **Quy tắc tách rời: Để bát nước xa bát thức ăn ít nhất 1.5 - 2 mét:**
   Trong tự nhiên, động vật săn mồi không bao giờ uống nước gần nơi ăn để tránh nguồn nước bị nhiễm khuẩn từ xác con mồi. Đặt bát nước ngay cạnh bát hạt thường khiến mèo bỏ qua bát nước.
3. **Tuyệt đối không đặt bát nước gần khay cát vệ sinh:**
   Mèo rất nhạy cảm với mùi hôi và bụi cát. Nước đặt gần khay vệ sinh sẽ bị mèo coi là nguồn nước ô nhiễm.
4. **Bổ sung thức ăn ướt (Pate / Soup thưởng):**
   Pate chứa từ 75% đến 82% độ ẩm. Chỉ cần bổ sung 1 gói pate 80g mỗi ngày, bé đã nạp thêm được hơn 60ml nước mà không cần ép buộc.
5. **Thêm nước ấm hoặc nước luộc thịt vào thức ăn:**
   Khuấy 2-3 thìa canh nước ấm hoặc nước luộc ức gà (không nêm muối, không hành tỏi) vào hạt khô hoặc pate. Nước ấm làm dậy mùi thơm của thức ăn, kích thích vị giác của mèo.
6. **Chọn bát nước rộng vành bằng gốm sứ hoặc inox:**
   Tránh dùng bát nhựa vì dễ bám mùi và tích tụ vi khuẩn gây mụn cằm. Chọn bát có lòng nông và vành rộng để ria mép của mèo không chạm vào thành bát khi uống, tránh hội chứng "mỏi ria mép" (whisker fatigue).
7. **Bố trí nhiều điểm uống nước quanh nhà:**
   Đặt 3-4 bát nước ở các góc yên tĩnh, trên lối đi thường ngày của mèo để bé luôn tiện miệng uống nước ở bất cứ đâu.$$,
     '["uong-nuoc", "hydration", "pate", "dai-phun", "phong-ngua-soi"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Nghiên cứu lâm sàng về thói quen uống nước và phòng ngừa FLUTD ở mèo (Journal of Feline Medicine and Surgery)', 80, now()),

    -- 4. Phân biệt sỏi Struvite và Canxi Oxalate
    (uuidv7(), 'soi-struvite-va-soi-canxi-oxalate', uuidv7(), 'vi', 'ARTICLE', 'GENERAL',
     'Phân biệt sỏi Struvite và sỏi Canxi Oxalate ở mèo',
     'So sánh nguyên nhân, mức pH thuận lợi và phương pháp điều trị giữa hai loại sỏi bàng quang phổ biến nhất ở mèo.',
     $$# Phân biệt sỏi Struvite và sỏi Canxi Oxalate ở mèo

Hơn 85% các ca sỏi bàng quang và tinh thể niệu đạo ở mèo thuộc về hai loại: **Sỏi Struvite** và **Sỏi Canxi Oxalate (Calcium Oxalate)**. Hai loại sỏi này hình thành trong hai môi trường pH hoàn toàn trái ngược nhau, đòi hỏi phương pháp can thiệp và dinh dưỡng khác biệt.

## 1. Sỏi Struvite (Magnesium Ammonium Phosphate)

- **Đặc tính và môi trường hình thành:**
  Sỏi Struvite hình thành khi nước tiểu có tính kiềm cao (**pH > 7.0 đến 8.0**), kết hợp với nồng độ khoáng chất Magie, Amoni và Photphat dư thừa.
- **Đối tượng thường gặp:**
  Mèo trẻ tuổi (dưới 6 tuổi), mèo ăn các loại thức ăn hạt kém chất lượng có tỷ lệ tinh bột/ngũ cốc cao, hoặc mèo bị nhiễm trùng đường tiết niệu sản sinh enzym urease làm kiềm hóa nước tiểu.
- **Khả năng làm tan:**
  **Có thể làm tan hoàn toàn bằng chế độ ăn.** Bác sĩ thú y thường chỉ định các dòng hạt dinh dưỡng trị liệu (như Royal Canin Urinary S/O, Hill's c/d hoặc s/d) có công thức đặc biệt giúp axit hóa nước tiểu về mức pH 6.0 - 6.3 và hạ thấp nồng độ Magie. Sau 2 đến 6 tuần, sỏi Struvite có thể tự tiêu biến mà không cần phẫu thuật.

## 2. Sỏi Canxi Oxalate (Calcium Oxalate)

- **Đặc tính và môi trường hình thành:**
  Sỏi Canxi Oxalate kết tủa khi nước tiểu có tính axit cao (**pH < 6.2**) và quá cô đặc do thiếu nước, dẫn đến ion Canxi và Oxalate bão hòa kết tinh thành các cạnh sắc nhọn.
- **Đối tượng thường gặp:**
  Mèo trung niên và mèo lớn tuổi (trên 7 tuổi), các giống mèo lông dài (Ba Tư, Anh lông dài), mèo béo phì lười vận động hoặc mèo bị mất nước mạn tính.
- **Khả năng làm tan:**
  **KHÔNG THỂ hòa tan bằng chế độ ăn.** Một khi sỏi Canxi Oxalate đã hình thành trong bàng quang hoặc kẹt ở niệu đạo, không có loại thuốc hay thức ăn nào có thể làm tan được sỏi. Phương pháp duy nhất là phẫu thuật mở bàng quang gắp sỏi hoặc tán sỏi bằng laser.

## 3. Vai trò của việc theo dõi pH với CatCheck

Nước tiểu của mèo cần được giữ cân bằng ở khoảng an toàn **pH 6.0 đến 6.5**:
- Nếu pH nghiêng sang kiềm (> 7.0, cát đổi màu xanh): Cảnh báo sớm nguy cơ tinh thể Struvite.
- Nếu pH nghiêng sang axit (< 6.0, cát đổi màu cam đỏ): Cảnh báo sớm nguy cơ tinh thể Canxi Oxalate.
Bằng việc theo dõi thường xuyên bằng cát CatCheck, bạn có thể điều chỉnh chế độ ăn và lượng nước uống kịp thời trước khi tinh thể lắng đọng thành những viên sỏi nguy hiểm.$$,
     '["struvite", "oxalate", "soi-bang-quang", "ph", "benh-tiet-nieu"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Phác đồ quản lý sỏi niệu ở thú nhỏ (ACVIM Consensus Statement on Urolithiasis)', 90, now()),

    -- 5. Hội chứng FLUTD và FIC
    (uuidv7(), 'hoi-chung-flutd-va-fic', uuidv7(), 'vi', 'ARTICLE', 'GENERAL',
     'Hội chứng FLUTD và Viêm bàng quang vô căn (FIC) ở mèo',
     'Tìm hiểu nguyên nhân, triệu chứng và giải pháp kiểm soát căng thẳng để bảo vệ hệ tiết niệu của mèo cưng.',
     $$# Hội chứng FLUTD và Viêm bàng quang vô căn (FIC) ở mèo

Bệnh đường tiết niệu dưới ở mèo (FLUTD - Feline Lower Urinary Tract Disease) là một trong những lý do hàng đầu khiến người nuôi mèo phải đưa thú cưng đi cấp cứu. Trong số các nguyên nhân gây bệnh, **Viêm bàng quang vô căn (FIC)** chiếm tỷ lệ cao nhất.

## 1. Cơ cấu các bệnh thuộc hội chứng FLUTD

Thống kê lâm sàng thú y cho thấy tỷ lệ các nguyên nhân gây FLUTD gồm:
- **Viêm bàng quang vô căn (FIC):** Chiếm khoảng **55% - 60%** các ca bệnh.
- **Sỏi bàng quang / Tinh thể niệu đạo:** Chiếm khoảng **15% - 20%** (chủ yếu là Struvite và Canxi Oxalate).
- **Nút nhầy niệu đạo (Urethral Plugs):** Chiếm khoảng **15% - 20%** (đặc biệt nguy hiểm ở mèo đực).
- **Nhiễm trùng đường tiết niệu do vi khuẩn (UTI):** Chỉ chiếm khoảng **1% - 2%** ở mèo trẻ, nhưng có thể tăng lên trên mèo già bị suy thận hoặc tiểu đường.
- **Dị tật giải phẫu hoặc khối u:** Dưới **2%**.

## 2. Viêm bàng quang vô căn (FIC) là gì?

Từ "vô căn" (idiopathic) có nghĩa là không tìm thấy nguyên nhân vi khuẩn hay bệnh lý thực thể rõ ràng. Các nghiên cứu hiện đại đã chứng minh FIC thực chất là một phản ứng tâm lý - thần kinh phức tạp do **căng thẳng (stress)** gây ra.

Khi mèo bị căng thẳng kéo dài, trục thần kinh giao cảm bị kích thích quá mức, giải phóng các chất trung gian gây viêm vào thành bàng quang. Lớp màng bảo vệ glycosaminoglycan (GAG) lót mặt trong bàng quang bị tổn thương, khiến nước tiểu có tính axit tiếp xúc trực tiếp với lớp cơ nhạy cảm bên dưới, gây bỏng rát, co thắt dữ dội và chảy máu vi thể.

## 3. Các dấu hiệu điển hình của FIC và FLUTD
- **Tiểu rắt, tiểu khó (Dysuria):** Mèo vào khay cát nhiều lần trong ngày, ngồi lâu, rặn tiểu nhưng chỉ ra từng giọt.
- **Tiểu máu (Hematuria):** Nước tiểu có màu hồng nhạt, đỏ tươi hoặc hạt cát CatCheck đổi màu hồng đậm.
- **Tiểu sai chỗ (Periuria):** Mèo đi tiểu ra sàn gạch mát, thảm, chăn ga hoặc bồn tắm thay vì khay cát (vì mèo liên kết khay cát với cảm giác đau buốt khi đi tiểu).
- **Liếm vùng kín quá mức:** Do cảm giác ngứa ngáy và đau rát ở đầu niệu đạo.

## 4. Cách phòng ngừa và kiểm soát FIC tại nhà
- **Giảm thiểu stress môi trường:** Giữ nhịp sinh hoạt ổn định, tránh thay đổi đột ngột thức ăn hay chỗ ở. Cung cấp trụ cào móng, kệ leo trèo và chỗ ẩn nấp an toàn.
- **Tối đa hóa lượng nước uống:** Nước tiểu loãng giúp giảm nồng độ các chất kích ứng tiếp xúc với thành bàng quang.
- **Liệu pháp Pheromone:** Sử dụng máy xông Feliway Classic để tạo cảm giác an tâm cho mèo trong không gian sống.$$,
     '["flutd", "fic", "viem-bang-quang", "stress", "tiet-nieu"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Hướng dẫn chẩn đoán và điều trị FIC ở mèo (ISFM / AAFP Feline Urinary Guidelines)', 100, now()),

    -- 6. Quy tắc vệ sinh và thay cát
    (uuidv7(), 'quy-tac-ve-sinh-va-thay-cat', uuidv7(), 'vi', 'FAQ', 'LITTER',
     'Bao lâu nên thay cát vệ sinh cho mèo một lần?',
     'Chu kỳ dọn dẹp hằng ngày, bổ sung cát và thay toàn bộ khay cát CatCheck giúp duy trì vệ sinh và độ nhạy hạt màu.',
     $$# Bao lâu nên thay cát vệ sinh cho mèo một lần?

Việc duy trì khay cát sạch sẽ không chỉ bảo vệ khứu giác nhạy cảm của mèo mà còn là chìa khóa để hạt chỉ thị màu CatCheck hoạt động chính xác nhất. Dưới đây là lịch trình dọn dẹp chuẩn chuyên gia:

## 1. Dọn dẹp chất thải rắn: 1 đến 2 lần mỗi ngày
- Mỗi ngày bạn nên dùng xẻng xúc bỏ phân và các khối cát vón bẩn ít nhất 1-2 lần.
- Mèo là loài động vật cực kỳ ưa sạch sẽ. Một khay cát còn vương chất thải cũ sẽ khiến mèo nhịn tiểu hoặc đi tiểu bậy ra ngoài. Nhịn tiểu là nguyên nhân trực tiếp dẫn đến kết tinh sỏi và viêm bàng quang.

## 2. Duy trì độ dày lớp cát từ 5 đến 7 cm
- Sau khi xúc bỏ chất thải, hãy đổ bổ sung thêm cát mới để duy trì độ dày từ 5 đến 7 cm.
- Độ dày này đảm bảo mèo có đủ không gian đào bới theo bản năng tự nhiên và giúp nước tiểu được thấm hút hoàn toàn vào đáy khay mà không bị đọng vũng.

## 3. Chu kỳ thay mới toàn bộ khay cát
- **Túi CatCheck 1 kg (Gói dùng thử / Theo dõi ngắn ngày):**
  Lượng cát trong túi 1 kg được thiết kế tối ưu cho 1 bé mèo sử dụng trong vòng **7 ngày**. Sau 7 ngày, bạn nên đổ bỏ toàn bộ và thay túi mới để đảm bảo độ nhạy chỉ thị màu sắc tốt nhất.
- **Túi CatCheck 2.5 kg (Gói tiêu chuẩn tháng):**
  Đối với 1 bé mèo trưởng thành, túi 2.5 kg duy trì hiệu quả hút ẩm và chỉ thị màu trong khoảng **3 đến 4 tuần**. Sau khoảng thời gian này, bụi và độ ẩm môi trường sẽ làm giảm dần độ nhạy của hạt cát, bạn nên thay toàn bộ cát mới.

## 4. Cách vệ sinh khay khi thay cát mới
- Đổ sạch cát cũ vào túi rác sinh hoạt (không xả cát silica vào bồn cầu).
- Rửa sạch khay bằng nước ấm và xà phòng dịu nhẹ hoặc nước rửa chén.
- **Tránh dùng chất tẩy rửa chứa Clo hoặc amoniac nồng nặc:** Mùi clo nồng gắt làm mèo sợ hãi, còn mùi amoniac khiến mèo tưởng là mùi nước tiểu của mèo lạ và có xu hướng đánh dấu lãnh thổ bậy.
- Lau hoặc phơi khô khay cát hoàn toàn dưới ánh nắng trước khi đổ cát mới vào.$$,
     '["ve-sinh", "thay-cat", "khay-cat", "litter", "cham-soc"]'::jsonb,
     'PUBLISHED', 'NONE', NULL, 110, now()),

    -- 7. Hướng dẫn chụp ảnh đọc màu chuẩn
    (uuidv7(), 'huong-dan-chup-anh-doc-mau-chuan', uuidv7(), 'vi', 'TIP', 'SCAN_HOWTO',
     'Làm sao chụp ảnh khay cát để đọc màu hạt chỉ thị chuẩn nhất?',
     '3 nguyên tắc vàng về thời điểm, ánh sáng và góc chụp giúp AI CatCheck phân tích chính xác độ pH.',
     $$# Làm sao chụp ảnh khay cát để đọc màu hạt chỉ thị chuẩn nhất?

Hệ thống thị giác máy tính của CatCheck phân tích không gian màu (RGB và HSV) của các hạt chỉ thị để ước lượng mức pH nước tiểu. Để thuật toán đưa ra kết quả chuẩn xác nhất, bạn chỉ cần áp dụng 3 quy tắc đơn giản sau:

## 1. Thời điểm vàng: Trong vòng 5 đến 15 phút sau khi mèo tiểu
- **Lý do:** Khi mèo vừa tiểu, nước tiểu ngấm vào hạt silica và phản ứng đổi màu diễn ra hoàn chỉnh sau khoảng 1-2 phút. 
- **Tránh chụp quá muộn (> 30-60 phút):** Nước tiểu để lâu ngoài không khí sẽ bị vi khuẩn phân hủy phân tử ure thành khí amoniac (NH3) có tính kiềm, làm hạt cát ngả dần sang màu xanh kiềm giả tạo, dẫn đến chẩn đoán sai lệch.

## 2. Ánh sáng: Đủ sáng và màu sắc chân thực
- **Ưu tiên ánh sáng trắng tự nhiên:** Ban ngày mở cửa sổ hoặc bật đèn phòng có ánh sáng trắng là điều kiện lý tưởng nhất.
- **Tránh ánh đèn vàng ấm:** Đèn vàng trang trí sẽ làm màu vàng bình thường bị ngả sang cam đỏ (báo động axit giả).
- **Tránh bật đèn flash máy ảnh trực tiếp:** Đèn flash chiếu thẳng vào hạt silica bóng sẽ tạo các đốm lóa trắng (glare) làm mất chi tiết màu.
- **Không để bóng điện thoại che khuất:** Giữ góc cầm điện thoại sao cho bóng của chính bạn hoặc điện thoại không đổ trực tiếp lên vùng cát.

## 3. Góc chụp và khoảng cách
- Cầm điện thoại song song với mặt đất, vuông góc 90 độ với bề mặt khay cát.
- Giữ khoảng cách từ 20 đến 30 cm so với mặt cát.
- Lấy nét trực tiếp vào vùng cát vừa đổi màu (chọn vùng màu đậm và rõ ràng nhất, tránh vùng cát khô chưa thấm nước tiểu).$$,
     '["chup-anh", "doc-mau", "scan", "huong-dan", "chinh-xac"]'::jsonb,
     'PUBLISHED', 'PERFORMANCE', 'Tiêu chuẩn kỹ thuật nhận diện ảnh màu cát CatCheck Colorimetric System v2.0', 120, now()),

    -- 8. Hiện tượng kiềm hóa sinh lý sau ăn
    (uuidv7(), 'kiem-hoa-sau-an-alkaline-tide', uuidv7(), 'vi', 'ARTICLE', 'DIET',
     'Hiện tượng kiềm hoá sinh lý sau ăn (Alkaline Tide) ở mèo',
     'Giải thích vì sao nước tiểu mèo có thể hơi kiềm sau bữa ăn và cách phân biệt hiện tượng tự nhiên này với bệnh lý.',
     $$# Hiện tượng kiềm hoá sinh lý sau ăn (Alkaline Tide) ở mèo

Nhiều người nuôi mèo giật mình khi thấy cát vệ sinh CatCheck chuyển sang màu xanh lá hoặc xanh lam nhạt sau bữa ăn của mèo. Tuy nhiên, đây rất có thể là một hiện tượng sinh lý hoàn toàn tự nhiên được y học gọi là **Alkaline Tide (Hiện tượng triều kiềm sau ăn)**.

## 1. Cơ chế sinh học của hiện tượng Alkaline Tide

Khoảng 2 đến 4 giờ sau khi mèo thưởng thức một bữa ăn giàu chất đạm, dạ dày của mèo phải sản xuất một lượng lớn axit clohydric (HCl) để kích hoạt enzym pepsin tiêu hóa thịt.

Để tổng hợp HCl đưa vào lòng dạ dày, các tế bào thành dạ dày sẽ lấy ion Clo từ máu và đồng thời giải phóng các ion bicarbonate (HCO3-, có tính kiềm) ngược vào dòng máu. Lượng bicarbonate dư thừa này nhanh chóng được thận lọc và đào thải qua nước tiểu, khiến độ pH của nước tiểu tăng vọt tạm thời từ mức bình thường 6.2 lên **pH 6.8 đến 7.4** (làm cát CatCheck chuyển sang màu xanh lá cây hoặc xanh lục nhạt).

Sau khi thức ăn rời khỏi dạ dày xuống ruột non (sau khoảng 4 đến 6 giờ), quá trình này kết thúc và pH nước tiểu tự động trở lại mức axit nhẹ bình thường (pH 6.0 - 6.5).

## 2. Cách phân biệt kiềm sinh lý và kiềm bệnh lý

- **Kiềm sinh lý sau ăn (Alkaline Tide):**
  - Chỉ xuất hiện trong vòng 2 - 4 giờ sau bữa ăn chính.
  - Trở lại màu vàng bình thường khi mèo đi tiểu vào lúc bụng đói (ví dụ buổi sáng sớm trước khi ăn).
  - Mèo hoàn toàn vui vẻ, ăn uống bình thường, không có dấu hiệu đau đớn hay tiểu rắt.
- **Kiềm bệnh lý (Nguy cơ sỏi Struvite hoặc nhiễm trùng):**
  - Hạt cát hiển thị màu xanh dương đậm liên tục (pH > 7.4) qua nhiều lần đi vệ sinh, kể cả trước và sau khi ăn.
  - Thường kèm theo các triệu chứng: đi tiểu nhiều lần, rặn tiểu, nước tiểu có mùi khai nồng nặc hoặc có vệt máu.

## 3. Lời khuyên cho chủ nuôi
Nếu phát hiện cát đổi màu xanh sau khi bé vừa ăn no, hãy ghi chú lại và kiểm tra màu cát ở lần đi tiểu tiếp theo. Đừng vội vàng đổi thức ăn hay dùng thuốc khi chưa xác định rõ đây là hiện tượng Alkaline Tide sinh lý bình thường.$$,
     '["alkaline-tide", "kiem-hoa", "sau-an", "ph", "sinh-ly"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Sinh lý học tiêu hóa và bài tiết của động vật ăn thịt (Veterinary Clinics of North America: Small Animal Practice)', 130, now()),

    -- 9. Chế độ dinh dưỡng cho mèo tiết niệu
    (uuidv7(), 'che-do-dinh-duong-cho-meo-tiet-nieu', uuidv7(), 'vi', 'ARTICLE', 'DIET',
     'Chế độ dinh dưỡng bảo vệ sức khỏe thận và đường tiết niệu cho mèo',
     'Các nguyên tắc lựa chọn thức ăn, kiểm soát Magie/Photpho và cân bằng dinh dưỡng giúp mèo phòng ngừa sỏi.',
     $$# Chế độ dinh dưỡng bảo vệ sức khỏe thận và đường tiết niệu cho mèo

Dinh dưỡng đóng vai trò quyết định đến độ pH và thành phần khoáng chất trong nước tiểu của mèo. Một chế độ ăn không cân đối có thể biến bàng quang của mèo thành môi trường lý tưởng để kết tủa sỏi chỉ trong vài tuần.

## 1. Bốn nguyên tắc dinh dưỡng bảo vệ hệ tiết niệu

### Nguyên tắc 1: Tối đa hóa hàm lượng nước trong khẩu phần
Nước tiểu càng loãng thì nồng độ các chất hòa tan càng thấp, tinh thể khoáng chất không có cơ hội kết dính lại với nhau thành sỏi. 
- Hãy duy trì chế độ ăn hỗn hợp: ít nhất **50% khẩu phần hằng ngày là thức ăn ướt (pate, thịt tươi nấu chín)**.
- Nếu chỉ cho ăn hạt khô, bắt buộc phải tăng cường đài phun nước và pha thêm nước vào pate thưởng.

### Nguyên tắc 2: Kiểm soát hàm lượng khoáng chất (Magie, Canxi, Photpho)
- Tinh thể Struvite cấu tạo từ Magie, Amoni và Photphat. Các loại thức ăn hạt giá rẻ thường sử dụng phụ phẩm xương nhiều tro (ash), làm dư thừa Magie và Photpho trong nước tiểu.
- Hãy chọn thức ăn có hàm lượng Magie được kiểm soát ở mức an toàn (< 0.08% - 0.1% tính theo chất khô).

### Nguyên tắc 3: Ưu tiên nguồn đạm động vật chất lượng cao
Mèo là động vật ăn thịt bắt buộc. Chế độ ăn giàu đạm động vật giúp duy trì độ pH nước tiểu sinh lý tự nhiên ở mức axit nhẹ (pH 6.2 - 6.5). Ngược lại, thức ăn chứa nhiều đạm thực vật (bột ngô, đậu nành, gluten lúa mì) thường làm kiềm hóa nước tiểu (pH > 7.0), tạo điều kiện cho sỏi Struvite hình thành.

### Nguyên tắc 4: Tuyệt đối không cho mèo ăn thức ăn thừa của người
Thức ăn của người chứa nhiều muối natri, đường, hành tây, tỏi và bột ngọt:
- Hàm lượng muối cao bắt thận mèo phải làm việc quá tải, làm tăng bài tiết canxi qua nước tiểu, kích thích sỏi Canxi Oxalate kết tủa.
- Hành và tỏi chứa chất độc phá hủy hồng cầu của mèo gây thiếu máu tán huyết nghiêm trọng.$$,
     '["dinh-duong", "thuc-an", "pate", "magie", "photpho", "than-tiet-nieu"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Dinh dưỡng lâm sàng cho mèo mắc bệnh tiết niệu dưới (AAHA Nutritional Assessment Guidelines)', 140, now()),

    -- 10. Dấu hiệu cấp cứu tắc nghẽn đường tiểu
    (uuidv7(), 'dau-hieu-cap-cuu-tac-nghen-duong-tieu', uuidv7(), 'vi', 'ARTICLE', 'GENERAL',
     'Dấu hiệu nhận biết tắc nghẽn niệu đạo khẩn cấp ở mèo đực',
     'Tình huống cấp cứu sống còn: nhận biết triệu chứng bí tiểu hoàn toàn và các hành động cần thực hiện ngay lập tức.',
     $$# Dấu hiệu nhận biết tắc nghẽn niệu đạo khẩn cấp ở mèo đực

Tắc nghẽn đường tiểu (Urethral Obstruction - UO) là một trong những **tình huống cấp cứu tối khẩn cấp trong y học thú y**. Nếu không được can thiệp kịp thời, mèo có thể tử vong do suy thận cấp và nhiễm độc kali trong vòng **24 đến 48 giờ**.

## 1. Vì sao mèo đực có nguy cơ cao nhất?
Niệu đạo của mèo đực dài, hẹp và uốn cong hơn nhiều so với niệu đạo ngắn và rộng của mèo cái. Chỉ cần một viên sỏi nhỏ li ti, một đám tinh thể hoặc một nút nhầy (hỗn hợp tế bào viêm và protein) là đủ để làm bít tắc hoàn toàn đường thoát nước tiểu.

## 2. 5 dấu hiệu cấp cứu đỏ không thể bỏ qua
Nếu bạn quan sát thấy bất kỳ dấu hiệu nào sau đây, hãy đưa mèo đến bệnh viện thú y NGAY LẬP TỨC:

1. **Rặn tiểu liên tục nhưng khay cát vẫn khô:**
   Mèo ra vào khay cát liên tục, ngồi xổm rặn tiểu rất lâu, căng thẳng nhưng không ra được giọt nước tiểu nào hoặc chỉ rỉ ra vài giọt có lẫn máu.
2. **Kêu la thất thanh vì đau buốt:**
   Mèo phát ra những tiếng kêu đau đớn, dữ dội khi cố gắng đi vệ sinh.
3. **Liếm láp vùng hạ vị và bộ phận sinh dục cuồng nhiệt:**
   Do cảm giác căng tức bàng quang và bỏng rát niệu đạo.
4. **Bụng căng cứng như quả bóng bàn:**
   Khi sờ nhẹ vào vùng bụng dưới của mèo, bạn cảm nhận được một khối tròn, căng cứng và rất đau. Đó là bàng quang đang bị ứ nước tiểu cực đại, có nguy cơ vỡ nếu bị ấn mạnh.
5. **Triệu chứng nhiễm độc toàn thân:**
   Lờ đờ, nằm bẹp một chỗ, nôn mửa, bỏ ăn, hạ thân nhiệt (tai và chân lạnh ngắt), thở gấp.

## 3. Điều cần làm và điều TUYỆT ĐỐI KHÔNG làm
- **CẦN LÀM:** Đặt mèo nhẹ nhàng vào balo/lồng vận chuyển và đưa ngay đến phòng khám hoặc bệnh viện thú y có dịch vụ cấp cứu 24/7 gần nhất. Bác sĩ sẽ tiến hành đặt ống thông niệu đạo (catheter) để giải áp bàng quang và truyền dịch đào thải độc tố.
- **TUYỆT ĐỐI KHÔNG:** Không được tự ý bóp nặn bụng hay ấn bàng quang tại nhà (nguy cơ làm vỡ bàng quang gây viêm phúc mạc tử vong ngay tại chỗ). Không tự cho mèo uống thuốc của người hay thuốc lợi tiểu.$$,
     '["cap-cuu", "tac-nieu-dao", "bi-tieu", "khan-cap", "meo-duc"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Cấp cứu tắc nghẽn đường tiểu ở thú cưng (VECCS Emergency and Critical Care Guidelines)', 150, now()),

    -- 11. Số lượng và vị trí đặt khay cát
    (uuidv7(), 'so-luong-va-vi-tri-dat-khay-cat', uuidv7(), 'vi', 'TIP', 'LITTER',
     'Quy tắc N+1 và cách bố trí khay cát chuẩn cho mèo',
     'Số lượng khay cát tối ưu và vị trí đặt khay hợp lý giúp mèo thoải mái đi vệ sinh, giảm nguy cơ nhịn tiểu sinh bệnh.',
     $$# Quy tắc N+1 và cách bố trí khay cát chuẩn cho mèo

Rất nhiều trường hợp mèo đi tiểu bậy ngoài khay hoặc nhịn tiểu sinh bệnh bắt nguồn từ việc khay cát bị bố trí sai cách. Hiểu được tâm lý loài mèo sẽ giúp bạn sắp xếp khu vực vệ sinh lý tưởng nhất cho thú cưng.

## 1. Quy tắc vàng N+1 về số lượng khay cát
Các chuyên gia hành vi động vật quốc tế khuyến nghị công thức:
**Số khay cát = Số lượng mèo trong nhà + 1**

Cụ thể:
- Nhà nuôi 1 bé mèo: Cần **2 khay cát**.
- Nhà nuôi 2 bé mèo: Cần ít nhất **3 khay cát**.
- Nhà nuôi 3 bé mèo: Cần ít nhất **4 khay cát**.

Mèo có tính phân chia lãnh thổ tự nhiên. Nhiều bé mèo thích đi nặng ở một khay và đi nhẹ ở một khay khác. Trong gia đình nuôi nhiều mèo, bé mèo yếu thế có thể bị bé mèo đầu đàn chặn đường ra vào khay, dẫn đến việc phải nhịn tiểu lâu ngày gây lắng đọng sỏi Struvite.

## 2. Kích thước khay cát đạt chuẩn
- Chiều dài khay cát tối thiểu phải bằng **1.5 lần chiều dài cơ thể mèo** (tính từ chóp mũi đến gốc đuôi).
- Khay cát quá nhỏ sẽ làm mèo cảm thấy chật chội, khó xoay người đào bới, dễ khiến mèo tìm đến các góc thoáng hơn trong nhà như thảm hay giường ngủ để giải quyết.

## 3. Vị trí đặt khay cát chuẩn phong thủy của mèo
- **Yên tĩnh và thông thoáng:** Đặt ở góc phòng ít người qua lại, tránh xa các thiết bị phát ra tiếng ồn hoặc rung lắc bất ngờ như máy giặt, máy sấy, loa thùng.
- **Có tầm nhìn và lối thoát lui:** Mèo luôn cảm thấy dễ bị tổn thương nhất khi đang đi vệ sinh. Tránh đặt khay ở góc chết hẹp khiến mèo lo sợ bị dồn vào chân tường.
- **Tách biệt với khu vực ăn uống:** Không bao giờ đặt khay cát gần bát hạt và bát nước của mèo.
- **Đối với nhà cao tầng:** Nên bố trí ít nhất 1 khay cát ở mỗi tầng sinh hoạt. Mèo con hoặc mèo già thường lười leo cầu thang khi buồn tiểu, việc có sẵn khay ở tầng sẽ hạn chế việc mèo đi bậy.$$,
     '["khay-cat", "quy-tac-n-1", "bo-tri", "litter-box", "hanh-vi"]'::jsonb,
     'PUBLISHED', 'NONE', NULL, 160, now()),

    -- 12. Tâm lý và stress ảnh hưởng tiết niệu
    (uuidv7(), 'tam-ly-va-stress-anh-huong-tiet-nieu', uuidv7(), 'vi', 'TIP', 'GENERAL',
     'Căng thẳng tâm lý ảnh hưởng thế nào đến bàng quang của mèo?',
     'Khám phá mối quan hệ giữa stress môi trường và bệnh tiết niệu, cùng các liệu pháp giải tỏa tâm lý cho mèo.',
     $$# Căng thẳng tâm lý ảnh hưởng thế nào đến bàng quang của mèo?

Mèo là loài động vật cực kỳ nhạy cảm với sự thay đổi của môi trường sống. Một biến động nhỏ mà con người không để ý cũng có thể khiến mèo bị stress dữ dội, và cơ quan đầu tiên gánh chịu hậu quả thường chính là bàng quang.

## 1. Trục Não bộ - Bàng quang (Brain-Bladder Axis)

Bàng quang không chỉ là một túi chứa nước tiểu thụ động, mà là một cơ quan có mạng lưới thụ thể thần kinh dày đặc liên kết chặt chẽ với hệ thần kinh trung ương.

Khi mèo bị căng thẳng (stress), não bộ sẽ kích hoạt tuyến thượng thận tiết ra hormone cortisol và catecholamine. Ở những chú mèo có cơ địa nhạy cảm, phản ứng này dẫn đến:
- Co thắt các mạch máu nuôi dưỡng niêm mạc bàng quang, làm giảm lượng máu cung cấp.
- Tổn thương lớp màng bảo vệ glycosaminoglycan (GAG), khiến các ion axit trong nước tiểu ngấm sâu gây viêm loét thành bàng quang.
- Kích thích phản xạ buồn tiểu liên tục dù bàng quang chỉ có vài giọt nước tiểu.

## 2. Những nguồn gây căng thẳng phổ biến cho mèo
- **Thay đổi không gian sống:** Chuyển nhà mới, sửa chữa nhà cửa, thay đổi vị trí đồ đạc.
- **Người lạ hoặc thú cưng mới:** Khách đến chơi nhà, có em bé mới sinh hoặc đón thêm một bé chó/mèo mới về.
- **Xung đột ngầm giữa các bé mèo:** Tranh giành bát ăn, khay cát hoặc địa bàn leo trèo.
- **Môi trường nghèo nàn, buồn chán:** Mèo bị nhốt trong phòng kín, không có đồ chơi cào móng, không có ánh sáng tự nhiên và view ngắm chim ngoài cửa sổ.
- **Khay cát bẩn:** Khay cát không được dọn kịp thời khiến mèo phải chịu đựng mùi hôi mỗi lần đi vệ sinh.

## 3. Cách giải tỏa stress bảo vệ bàng quang mèo
- **Làm giàu không gian sống (Environmental Enrichment):** Lắp đặt kệ gắn tường, tháp leo trèo (cat tree) và trụ cào móng ở các vị trí cao để mèo quan sát lãnh thổ.
- **Thời gian chơi đùa tương tác:** Dành ít nhất 15 - 20 phút mỗi ngày chơi cùng mèo với cần câu lông vũ hoặc đồ chơi laser để giải phóng năng lượng săn mồi dư thừa.
- **Sử dụng Pheromone xoa dịu:** Cắm máy xông tinh dầu Pheromone Feliway trong phòng ngủ để tạo tín hiệu an toàn và thư thái cho mèo.
- **Duy trì lịch trình cố định:** Cho ăn và dọn khay cát vào các khung giờ cố định hằng ngày.$$,
     '["stress", "tam-ly", "bang-quang", "moi-truong", "cham-soc"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Tác động của stress lên hệ tiết niệu mèo (Feline Behavioral Health and Welfare Guidelines)', 170, now()),

    -- 13. Hướng dẫn theo dõi biểu đồ xu hướng pH
    (uuidv7(), 'huong-dan-theo-doi-bieu-do-xu-huong-ph', uuidv7(), 'vi', 'TIP', 'SCAN_HOWTO',
     'Cách đọc biểu đồ xu hướng pH trên ứng dụng CatCheck',
     'Tận dụng tính năng biểu đồ và cảnh báo thông minh của CatCheck để phát hiện sớm các dấu hiệu bất thường.',
     $$# Cách đọc biểu đồ xu hướng pH trên ứng dụng CatCheck

Một trong những thế mạnh lớn nhất của ứng dụng CatCheck là khả năng lưu trữ lịch sử và vẽ biểu đồ xu hướng nồng độ pH theo thời gian thực. Theo dõi xu hướng giúp bạn có cái nhìn tổng quan và khoa học hơn so với việc chỉ nhìn vào một kết quả quét đơn lẻ.

## 1. Vì sao xu hướng quan trọng hơn kết quả đơn lẻ?
Độ pH nước tiểu của mèo có tính dao động sinh lý tự nhiên trong ngày:
- Buổi sáng sau một đêm nhịn ăn: Nước tiểu thường có tính axit hơn (pH 6.0 - 6.2).
- Sau bữa ăn chính 2-4 tiếng: Nước tiểu có xu hướng kiềm nhẹ (pH 6.8 - 7.2) do hiện tượng Alkaline Tide.

Vì vậy, một lần đo hơi cao hay hơi thấp không có nghĩa là bé mèo bị bệnh. Tuy nhiên, nếu **đường biểu đồ liên tục nằm ở vùng cảnh báo qua 3-5 lần đo**, đó là chỉ báo rõ ràng về nguy cơ bệnh lý đường tiết niệu cần được can thiệp.

## 2. Các vùng cảnh báo trên biểu đồ CatCheck
- **Vùng Xanh lá an toàn (pH 6.0 – 6.5):**
  Đường biểu đồ dao động ổn định trong vùng này chứng tỏ hệ tiết niệu của mèo đang hoạt động rất tốt.
- **Vùng Vàng chú ý (pH 6.6 – 7.0 hoặc pH 5.8 – 5.9):**
  Mức chớm lệch. Bạn nên kiểm tra lại lượng nước uống và thời điểm quét so với bữa ăn của bé.
- **Vùng Đỏ cảnh báo nguy cơ (pH > 7.0 hoặc pH < 5.8):**
  Nguy cơ cao hình thành sỏi Struvite (nếu pH > 7.0) hoặc sỏi Oxalate (nếu pH < 5.8). Ứng dụng CatCheck sẽ kích hoạt cảnh báo thông minh và gợi ý bạn theo dõi sát sao hơn.

## 3. Tần suất theo dõi khuyến nghị
- **Mèo khỏe mạnh bình thường:** Quét và lưu ảnh 1 đến 2 lần mỗi tuần để xây dựng đường cơ sở (baseline) sức khỏe cho bé.
- **Mèo có tiền sử sỏi hoặc đang đổi thức ăn:** Quét 2 ngày một lần để theo dõi sát phản ứng của cơ thể bé với loại thức ăn mới.
- **Tính năng Xuất báo cáo y tế (Export PDF):** Khi đưa mèo đi khám thú y, hãy dùng tính năng Xuất PDF trên CatCheck. Bác sĩ thú y sẽ có ngay dữ liệu theo dõi thực tế trong nhiều tuần, giúp đưa ra phác đồ điều trị chính xác nhất.$$,
     '["bieu-do", "xu-huong", "app", "catcheck", "theo-doi"]'::jsonb,
     'PUBLISHED', 'NONE', NULL, 180, now()),

    -- 14. Chăm sóc mèo sau khi điều trị sỏi
    (uuidv7(), 'cham-soc-meo-sau-khi-dieu-tri-soi', uuidv7(), 'vi', 'ARTICLE', 'GENERAL',
     'Chăm sóc và phòng ngừa tái phát sau khi mèo điều trị sỏi tiết niệu',
     'Kế hoạch chăm sóc dài hạn, theo dõi nước tiểu và chế độ ăn chống tái phát cho mèo sau khi khỏi bệnh.',
     $$# Chăm sóc và phòng ngừa tái phát sau khi mèo điều trị sỏi tiết niệu

Thống kê thú y cho thấy có tới **35% đến 50% mèo từng bị sỏi bàng quang hoặc tắc niệu đạo sẽ bị tái phát trong vòng 12 tháng** nếu không có kế hoạch phòng ngừa kiên trì và khoa học. Chăm sóc hậu điều trị là giai đoạn quyết định sức khỏe lâu dài của bé.

## 1. Tuân thủ nghiêm ngặt chế độ ăn trị liệu (Prescription Diet)
- Nếu mèo vừa điều trị sỏi Struvite hoặc Canxi Oxalate, bác sĩ thú y sẽ kê đơn các dòng hạt dinh dưỡng chuyên biệt (như Royal Canin Urinary S/O, Hill's c/d Multicare).
- **Nguyên tắc sống còn:** Tuyệt đối không cho ăn xen kẽ hạt thường, thức ăn vặt hay thịt nêm nếm gia vị của người. Chỉ một lượng nhỏ thức ăn ngoài cũng có thể phá vỡ cân bằng ion, làm sai lệch độ pH mục tiêu và khiến tinh thể sỏi tái phát trở lại.

## 2. Giữ nước tiểu luôn ở trạng thái pha loãng
- Mục tiêu quan trọng nhất trong việc chống tái phát sỏi là làm giảm tỷ trọng nước tiểu (Urine Specific Gravity - USG < 1.030). Nước tiểu loãng sẽ liên tục rửa trôi các cặn khoáng vi thể ra ngoài trước khi chúng kịp tích tụ lại thành sỏi.
- Sử dụng đài phun nước tuần hoàn và chuyển đổi phần lớn khẩu phần sang dạng thức ăn ướt (pate dinh dưỡng chuyên dụng cho tiết niệu).

## 3. Theo dõi màu cát CatCheck hằng tuần
- Cát vệ sinh chỉ thị màu CatCheck là công cụ đắc lực nhất giúp chủ nuôi theo dõi hiệu quả điều trị tại nhà mà không cần phải lấy mẫu nước tiểu phức tạp:
  - Nếu mèo từng bị sỏi Struvite: Giữ màu cát luôn ở mức vàng (pH 6.0 - 6.4), không để ngả sang xanh dương.
  - Nếu mèo từng bị sỏi Canxi Oxalate: Tránh để màu cát ngả sang cam đỏ đậm (pH < 6.0).
- Lưu lại lịch sử trên app để theo dõi diễn biến sức khỏe liên tục.

## 4. Tái khám và siêu âm định kỳ
- Đưa mèo đi tái khám, siêu âm bàng quang và xét nghiệm nước tiểu tổng quát sau 1 tháng, 3 tháng và định kỳ mỗi 6 tháng một lần.
- Việc phát hiện cặn sỏi khi còn ở dạng tinh thể cát mịn sẽ giúp điều trị nội khoa dễ dàng hơn nhiều so với khi sỏi đã phát triển to gây tắc nghẽn nguy cấp.$$,
     '["phuc-hoi", "tai-phat", "soi-nieu", "hau-phau", "dieu-tri"]'::jsonb,
     'PUBLISHED', 'MEDICAL', 'Phác đồ kiểm soát và phòng ngừa tái phát bệnh tiết niệu ở thú nhỏ (World Small Animal Veterinary Association)', 190, now())

ON CONFLICT (slug, locale) DO UPDATE
    SET title            = EXCLUDED.title,
        summary          = EXCLUDED.summary,
        body_md          = EXCLUDED.body_md,
        tags             = EXCLUDED.tags,
        claim_type       = EXCLUDED.claim_type,
        source_reference = EXCLUDED.source_reference,
        category         = EXCLUDED.category,
        kind             = EXCLUDED.kind,
        status           = EXCLUDED.status,
        sort_weight      = EXCLUDED.sort_weight,
        updated_at       = now();
