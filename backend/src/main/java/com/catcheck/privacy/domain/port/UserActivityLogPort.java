package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.UserActivityLog;

import java.util.UUID;

public interface UserActivityLogPort {

    void append(UserActivityLog event);

    int deleteByUser(UUID userId);
}
