package com.catcheck.privacy.api.dto;

/**
 * Trạng thái hiện hành của một mục đích đối với user — response C2/C3.
 *
 * @param purposeCode mã mục đích
 * @param status      GRANTED/DENIED/WITHDRAWN, hoặc NONE khi chưa từng được hỏi
 *                    ("im lặng không phải là đồng ý", p15 §15.3.1 C4)
 */
public record ConsentStateView(String purposeCode, String status) {
}
