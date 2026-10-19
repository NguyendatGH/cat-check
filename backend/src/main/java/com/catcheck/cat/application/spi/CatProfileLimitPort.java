package com.catcheck.cat.application.spi;

import java.util.Optional;
import java.util.UUID;

public interface CatProfileLimitPort {

    Optional<CatProfileLimit> limitFor(UUID ownerId);
}
