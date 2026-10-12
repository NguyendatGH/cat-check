package com.catcheck.cat.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.cat.application.spi.OpenDsarPort;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatStatus;
import com.catcheck.cat.domain.port.CatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminCatViewService {

    private final CatRepository catRepository;
    private final OpenDsarPort openDsarPort;
    private final AuditLogService auditLogService;

    public AdminCatViewService(CatRepository catRepository,
                               OpenDsarPort openDsarPort,
                               AuditLogService auditLogService) {
        this.catRepository = catRepository;
        this.openDsarPort = openDsarPort;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AdminCatListing listCatsOf(UUID userId, AdminActionContext context) {
        boolean fullData = hasDsarFullAccess(userId, context);
        List<Cat> cats = catRepository.findAllByOwnerId(userId, CatStatus.ACTIVE, true);

        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.CAT, userId)
                .action("ADMIN_USER_CATS_VIEW")
                .outcome(AuditOutcome.SUCCESS)
                .meta("reason", context.reason())
                .meta("catCount", cats.size())
                .meta("unmaskedByDsar", fullData)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new AdminCatListing(cats, fullData);
    }

    private boolean hasDsarFullAccess(UUID userId, AdminActionContext context) {
        return context.isDpo() && openDsarPort.hasOpenRequestFor(userId);
    }

    public record AdminCatListing(List<Cat> cats, boolean fullData) {

        public AdminCatListing {
            cats = cats == null ? List.of() : List.copyOf(cats);
        }
    }
}
