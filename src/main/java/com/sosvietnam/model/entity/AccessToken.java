package com.sosvietnam.model.entity;

import lombok.*;

import java.util.Date;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessToken {

    private UUID jwtId;

    private UUID userId;

    private String fullName;

    private String email;

    private Long roleId;

    private Date issuedAt;

    private Date expiresAt;
}