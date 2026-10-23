package com.catcheck.shop.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.api.dto.AdminPromoCodeRequest;
import com.catcheck.shop.domain.PromoCode;
import com.catcheck.shop.domain.PromoCodeDraft;
import com.catcheck.shop.domain.port.ShopRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AdminPromoService {
    private final ShopRepository repository;
    private final AuditLogService auditLog;

    public AdminPromoService(ShopRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    public List<PromoCode> list() {
        return repository.adminPromoCodes();
    }

    public PromoCode get(UUID id) {
        return repository.adminPromoCode(id).orElseThrow(() -> new NotFoundException(ShopErrorCode.PROMO_CODE_NOT_FOUND));
    }

    public PromoCode create(AdminPromoCodeRequest req, UUID adminId, String role) {
        String cleanCode = req.code().strip().toUpperCase(Locale.ROOT);
        PromoCodeDraft draft = new PromoCodeDraft(
                cleanCode,
                req.label().strip(),
                req.type().toUpperCase(Locale.ROOT),
                req.amount(),
                req.minOrderVnd(),
                req.maxDiscountVnd(),
                req.usageLimit(),
                req.startsAt(),
                req.expiresAt(),
                req.isActive() == null || req.isActive()
        );
        PromoCode p = repository.adminCreatePromoCode(draft);
        audit("ADMIN_SHOP_PROMO_CREATED", p, adminId, role);
        return p;
    }

    public PromoCode update(UUID id, AdminPromoCodeRequest req, UUID adminId, String role) {
        PromoCodeDraft draft = new PromoCodeDraft(
                null,
                req.label().strip(),
                req.type().toUpperCase(Locale.ROOT),
                req.amount(),
                req.minOrderVnd(),
                req.maxDiscountVnd(),
                req.usageLimit(),
                req.startsAt(),
                req.expiresAt(),
                req.isActive() == null || req.isActive()
        );
        PromoCode p = repository.adminUpdatePromoCode(id, draft).orElseThrow(() -> new NotFoundException(ShopErrorCode.PROMO_CODE_NOT_FOUND));
        audit("ADMIN_SHOP_PROMO_UPDATED", p, adminId, role);
        return p;
    }

    public PromoCode setStatus(UUID id, boolean isActive, UUID adminId, String role) {
        PromoCode p = repository.adminSetPromoCodeStatus(id, isActive).orElseThrow(() -> new NotFoundException(ShopErrorCode.PROMO_CODE_NOT_FOUND));
        audit("ADMIN_SHOP_PROMO_STATUS_CHANGED", p, adminId, role);
        return p;
    }

    public void delete(UUID id, UUID adminId, String role) {
        PromoCode p = get(id);
        if (!repository.adminDeletePromoCode(id)) {
            throw new NotFoundException(ShopErrorCode.PROMO_CODE_NOT_FOUND);
        }
        audit("ADMIN_SHOP_PROMO_DELETED", p, adminId, role);
    }

    private void audit(String action, PromoCode p, UUID adminId, String role) {
        auditLog.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, role))
                .subject(AuditSubjectType.SYSTEM, null)
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .meta("promoId", p.id().toString())
                .meta("code", p.code())
                .meta("type", p.type())
                .meta("amount", p.amount())
                .meta("isActive", p.isActive())
                .build());
    }
}
