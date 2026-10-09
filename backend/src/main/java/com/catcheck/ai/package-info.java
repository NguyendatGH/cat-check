/**
 * Chatbot retrieval-augmented generation trên kho nội dung đã công bố.
 *
 * <p>Năm cạnh thêm cho A1 ({@code docs/cat-rag-credit-pipeline.md} mục A4-(1)):
 * {@code CatContextPort} (ai/application) + {@code CatContextAdapter} (ai/infrastructure) dựng
 * ngữ cảnh một bé mèo cụ thể — KHÔNG PII chủ nuôi, chỉ dựng tại thời điểm hỏi, không nạp vào kho
 * RAG chung — bằng cách gọi named interface "api" của năm module sở hữu dữ liệu thật:</p>
 * <ul>
 *   <li>{@code cat::api} — hồ sơ + khảo sát + dấu hiệu lâm sàng ({@code CatContextQuery}, điểm
 *       kiểm sở hữu {@code catId}/{@code userId} duy nhất của luồng này);</li>
 *   <li>{@code scan::api} — lần quét gần nhất + tóm tắt 30 ngày ({@code ScanHistoryQuery},
 *       đã có sẵn, không đổi);</li>
 *   <li>{@code insight::api} — {@code health_flag} đang mở ({@code OpenHealthFlagQuery}, mới);</li>
 *   <li>{@code reminder::api} — lịch nhắc đang bật ({@code ActiveReminderQuery}, mới);</li>
 *   <li>{@code credit::api} — cờ quyền đọc an toàn, không số dư ledger chi tiết
 *       ({@code EntitlementQuery}, {@code CreditConsumption}, đã có sẵn, không đổi).</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {
        "shared", "cat::api", "scan::api", "insight::api", "reminder::api", "credit::api" })
package com.catcheck.ai;
