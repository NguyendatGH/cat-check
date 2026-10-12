package com.catcheck.cat.application.spi;

import java.util.UUID;

public interface OpenDsarPort {

    boolean hasOpenRequestFor(UUID userId);
}
