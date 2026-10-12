package com.catcheck.shared.application.spi;

import java.util.Map;
import java.util.Optional;

public interface AppSettingReader {

    Optional<String> findPublicValue(String key);

    Map<String, String> findPublicNamespace(String namespacePrefix);
}
