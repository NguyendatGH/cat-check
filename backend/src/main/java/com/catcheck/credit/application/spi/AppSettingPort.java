package com.catcheck.credit.application.spi;

import java.util.OptionalInt;

public interface AppSettingPort {

    OptionalInt findInt(String key);
}
