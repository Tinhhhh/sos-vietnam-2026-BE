package com.sosvietnam.model.payload.request;

import com.sosvietnam.model.payload.enums.EmergencyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateIncidentRequest {
    @Size(max = 150)
    private String citizenName;
    @Size(max = 30)
    private String phone;
    @NotNull
    private EmergencyType emergencyType;
    @Size(max = 10)
    private List<@Size(max = 200) String> incidentTags;
    @Size(max = 2000)
    private String description;
    @Size(max = 500)
    private String address;
    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private Double latitude;
    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private Double longitude;
    @PositiveOrZero
    private Integer accuracy;
}
