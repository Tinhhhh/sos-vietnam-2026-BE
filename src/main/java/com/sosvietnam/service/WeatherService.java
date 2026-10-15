package com.sosvietnam.service;

import com.sosvietnam.model.payload.response.WeatherResponse;

/** Thời tiết hiện tại cho header màn hình trực ban (proxy Open-Meteo như server.js bản gốc). */
public interface WeatherService {
    WeatherResponse getCurrentWeather(double lat, double lng);
}
