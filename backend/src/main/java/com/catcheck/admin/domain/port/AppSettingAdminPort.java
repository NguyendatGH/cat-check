package com.catcheck.admin.domain.port;

import com.catcheck.admin.domain.AppSettingRow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppSettingAdminPort {

    List<AppSettingRow> findAll();

    Optional<AppSettingRow> findByKey(String key);

    Optional<AppSettingRow> updateValue(String key, String rawValue, UUID updatedBy);
}
