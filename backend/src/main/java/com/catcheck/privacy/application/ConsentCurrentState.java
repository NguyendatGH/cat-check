package com.catcheck.privacy.application;

import com.catcheck.privacy.domain.ConsentStatus;

/**
 * Trạng thái hiện hành của một mục đích đối với một user — trả về ở C2/C3.
 *
 * @param purposeCode mã mục đích
 * @param status      {@link ConsentStatus#NONE} khi user chưa từng được hỏi về
 *                    mục này ("im lặng không phải là đồng ý")
 */
public record ConsentCurrentState(String purposeCode, ConsentStatus status) {
}
