package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityIncidentPort {

    SecurityIncident save(SecurityIncident incident);

    String nextPublicRef();

    void update(SecurityIncident incident);

    List<SecurityIncident> findOverdueAuthorityNotification(Instant now);

    Optional<SecurityIncident> findById(UUID id);

    List<SecurityIncident> findForAdmin(IncidentSeverity severity, Boolean unresolved, int offset, int limit);

    long countForAdmin(IncidentSeverity severity, Boolean unresolved);
}
