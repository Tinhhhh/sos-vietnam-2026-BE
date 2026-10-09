package com.sosvietnam.model.payload.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Object for Exception Response")
public class ExceptionResponse {
    @Schema(description = "Http Status Code", example = "400")
    private Integer httpStatus;

    @Schema(description = "Time that the error occurred", example = "10/08/2026 16:30:00")
    private String timestamp;

    @Schema(description = "Error title", example = "Invalid Credentials")
    private String message;

    @Schema(description = "An Error detail", example = "Bad credentials")
    private String error;

    @Schema(description = "List Of Validation Errors")
    private Map<String, String> data;
}