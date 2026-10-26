package com.sosvietnam.service;

import com.sosvietnam.model.payload.response.RoadSnapResponse;

/** Đưa vị trí SOS ra đường lớn gần nhất để xe cứu hộ dừng (dân ở trong hẻm, ngõ nhỏ). */
public interface RoadSnapService {

    RoadSnapResponse snap(double lat, double lng);
}
