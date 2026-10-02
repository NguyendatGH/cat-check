package com.catcheck.privacy.application;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.RetentionPolicy;
import com.catcheck.privacy.domain.port.RetentionPolicyPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý cấu hình thời hạn lưu trữ (p4 B6, p15 REQ-RET-03): thời hạn KHÔNG hard-code
 * để DPO/luật sư chỉnh mà không cần deploy. Chỉ role {@code DPO} được sửa (REQ-RET-06).
 *
 * <p>Bất biến I15 (p4 §4.5.1): hai giá trị KHÔNG được NỚI bằng bảng này vì là cam kết
 * với owner/user — {@code SCAN_IMAGE ≤ 14 ngày} (quyết định #9) và grace xoá tài khoản
 * ≤ 7 ngày (TD-05). Service chặn giá trị lớn hơn kèm lý do.</p>
 */
@Service
public class RetentionService {

    /** Quyết định owner #9: ảnh scan tối đa 14 ngày — không được nới bằng cấu hình (I15). */
    public static final int SCAN_IMAGE_MAX_DAYS = 14;

    /** Vai trò được sửa retention_policy (p11 §11.5.1). */
    private static final String ROLE_DPO = "DPO";

    private final RetentionPolicyPort policyPort;
    private final UserAccountPort userAccountPort;

    public RetentionService(RetentionPolicyPort policyPort, UserAccountPort userAccountPort) {
        this.policyPort = policyPort;
        this.userAccountPort = userAccountPort;
    }

    /** Cấu hình retention hiện có — nguồn dữ liệu cho dashboard {@code /admin/privacy/retention} (p15 REQ-RET-05, M6). */
    public List<RetentionPolicy> listPolicies() {
        return policyPort.findAll();
    }

    /**
     * Thêm/cập nhật chính sách retention. Chỉ DPO (p11 §15.5.2 REQ-RET-06); giá trị nới
     * quá cam kết owner thì từ chối kèm lý do (I15).
     */
    public void savePolicy(RetentionPolicy policy, UUID actor) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(actor);
        if (!snapshot.hasRole(ROLE_DPO)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, ROLE_DPO);
        }
        // Bất biến I15: 14 ngày là cam kết của owner đã in vào copy hiển thị cho user —
        // không được nới bằng bảng cấu hình.
        if ("SCAN_IMAGE".equals(policy.code()) && policy.retentionDays() != null
                && policy.retentionDays() > SCAN_IMAGE_MAX_DAYS) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "SCAN_IMAGE_MAX_DAYS");
        }
        policyPort.save(policy);
    }
}
