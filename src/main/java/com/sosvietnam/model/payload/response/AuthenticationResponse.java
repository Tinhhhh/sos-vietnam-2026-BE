package com.sosvietnam.model.payload.response;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private UUID accountId;
    private String email;
    private String fullName;
    private String role;
    private String agencyType;
    private String province;
}