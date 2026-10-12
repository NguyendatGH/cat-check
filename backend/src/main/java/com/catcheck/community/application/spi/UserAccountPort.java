package com.catcheck.community.application.spi;

import java.util.UUID;

public interface UserAccountPort {

    AccountStatus statusOf(UUID userId);
}
