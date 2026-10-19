package com.catcheck.scan.application.spi;

import java.util.UUID;

public interface OpenDsarPort {

    boolean hasOpenRequestFor(UUID userId);
}
