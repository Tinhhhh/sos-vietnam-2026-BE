package com.sosvietnam.service;

import com.sosvietnam.util.RouteSimulation;

import java.util.List;

/**
 * CHỈ ĐỂ DEMO: giả lập điện thoại của người đi hiện trường chạy dọc tuyến, mỗi 5 giây một điểm, để thử theo dõi
 * xe mà không phải cầm máy ra đường. Có `app.traccar.osmand-url` thì gửi điểm vào Traccar như app Traccar Client
 * (thử cả chuỗi Traccar -> forward -> BE), không có thì đưa thẳng vào DispatchTrackingService.
 * Chỉ giữ trong RAM; không đổi trạng thái lượt (vẫn phải bấm "Tới nơi").
 */
public interface DispatchSimulationService {

    /** Bắt đầu (hoặc chạy lại từ đầu) cho lượt điều động; dừng tự động khi hết tuyến hoặc lượt không còn EN_ROUTE. */
    void start(Long dispatchId, String badge, List<RouteSimulation.Step> steps);

    void stop(Long dispatchId);

    boolean running(Long dispatchId);

    /** Quãng đường xe đi trong một bước (tốc độ x chu kỳ), để chia tuyến. */
    double stepMeters();
}
