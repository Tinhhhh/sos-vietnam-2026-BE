package com.sosvietnam.repository;

import com.sosvietnam.model.entity.AppSetting;
import com.sosvietnam.model.payload.enums.AppSettingKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, AppSettingKey> {
}
