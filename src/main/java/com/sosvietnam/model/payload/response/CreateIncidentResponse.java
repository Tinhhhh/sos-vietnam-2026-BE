package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Kết quả gửi tin báo SOS. citizen_access_token chỉ trả về đúng 1 lần ở đây;
 * người dân gửi lại nó qua header X-SOS-Access-Token để thao tác với sự cố
 * (upload ảnh/video, sau này: theo dõi, chat, báo "tôi đã an toàn").
 */
@Data
@AllArgsConstructor
public class CreateIncidentResponse {
    @JsonProperty("incident")
    private IncidentResponse incident;
    @JsonProperty("citizen_access_token")
    private String citizenAccessToken;
}
