package com.catcheck.shop.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.api.dto.AdminProductRequest;
import com.catcheck.shop.domain.AdminProduct;
import com.catcheck.shop.domain.AdminProductDraft;
import com.catcheck.shop.domain.port.ShopRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AdminShopService {
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9][A-Z0-9-]{1,63}$");

    private final ShopRepository repository;
    private final AuditLogService auditLog;

    public AdminShopService(ShopRepository repository, AuditLogService auditLog) {
        this.repository = repository;
        this.auditLog = auditLog;
    }

    public List<AdminProduct> list() { return repository.adminProducts(); }

    public AdminProduct create(AdminProductRequest r, UUID adminId, String role) {
        String sku = r.sku() == null ? "" : r.sku().strip().toUpperCase(Locale.ROOT);
        if (!SKU.matcher(sku).matches()) throw new BusinessRuleException(ShopErrorCode.PRODUCT_INVALID);
        AdminProduct p = repository.adminCreate(draft(sku, r));
        audit("ADMIN_SHOP_PRODUCT_CREATED", p, adminId, role, "status", p.status());
        return p;
    }

    public AdminProduct update(UUID id, AdminProductRequest r, UUID adminId, String role) {
        AdminProduct p = repository.adminUpdate(id, draft(null, r)).orElseThrow(() -> new NotFoundException(ShopErrorCode.PRODUCT_NOT_FOUND));
        audit("ADMIN_SHOP_PRODUCT_UPDATED", p, adminId, role, "status", p.status());
        return p;
    }

    public AdminProduct setStatus(UUID id, String status, UUID adminId, String role) {
        AdminProduct p = repository.adminSetStatus(id, status).orElseThrow(() -> new NotFoundException(ShopErrorCode.PRODUCT_NOT_FOUND));
        audit("ADMIN_SHOP_PRODUCT_STATUS_CHANGED", p, adminId, role, "status", p.status());
        return p;
    }

    private AdminProductDraft draft(String sku, AdminProductRequest r) {
        String image = r.imageUrl() == null || r.imageUrl().isBlank() ? null : r.imageUrl().strip();
        if (image != null && !validUrl(image)) throw new BusinessRuleException(ShopErrorCode.PRODUCT_INVALID);
        if (r.compareAtPriceVnd() != null && r.compareAtPriceVnd() <= r.priceVnd()) throw new BusinessRuleException(ShopErrorCode.PRODUCT_INVALID);
        return new AdminProductDraft(sku, r.name().strip(), r.description().strip(), image, r.priceVnd(), r.compareAtPriceVnd(), r.stockQuantity(), r.status());
    }

    private static boolean validUrl(String s) {
        try {
            URI u = URI.create(s);
            return ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme())) && u.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void audit(String action, AdminProduct p, UUID adminId, String role, String k, Object v) {
        auditLog.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, role))
                .subject(AuditSubjectType.SYSTEM, null)
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .meta("productId", p.id().toString())
                .meta("sku", p.sku())
                .meta(k, v)
                .build());
    }
}
