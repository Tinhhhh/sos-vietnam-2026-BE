package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Thời tiết hiện tại tại đơn vị trực ban (header màn hình chỉ huy). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/weather")
@Tag(name = "Weather", description = "Current weather at the officer's unit (Open-Meteo)")
public class WeatherController {

    private final WeatherService weatherService;

    @Operation(summary = "Get current weather",
            description = "Cached 10 minutes per ~1 km. 503 when Open-Meteo is unreachable. Role required: OFFICER, ADMIN")
    @GetMapping("/current")
    public ResponseEntity<Object> getCurrentWeather(@RequestParam("lat") double lat, @RequestParam("lng") double lng) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved current weather",
                weatherService.getCurrentWeather(lat, lng));
    }
}
