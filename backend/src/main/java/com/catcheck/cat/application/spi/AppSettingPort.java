package com.catcheck.cat.application.spi;

import java.util.OptionalInt;
import java.util.UUID;

public interface AppSettingPort {

    OptionalInt findInt(String key, UUID requesterId);
}
