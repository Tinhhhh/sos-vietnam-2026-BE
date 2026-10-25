package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.AppSettingKey;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một cài đặt chung (bảng app_settings, V13). Chưa có dòng = dùng giá trị mặc định trong application.properties. */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSetting {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "setting_key", length = 50)
    private AppSettingKey key;

    @Column(nullable = false, length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // cán bộ đổi gần nhất (unit_members.member_id), null = chưa ai đổi
    @Column(name = "updated_by")
    private UUID updatedBy;
}
