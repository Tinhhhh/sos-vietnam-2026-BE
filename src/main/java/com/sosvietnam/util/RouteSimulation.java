package com.sosvietnam.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Chia một tuyến (danh sách [lat, lng]) thành các điểm cách đều nhau theo quãng đường, để giả lập xe chạy
 * (nút "Mô phỏng di chuyển" của ca demo). Mỗi điểm kèm hướng đi (độ, 0 = bắc).
 */
@UtilityClass
public class RouteSimulation {

    private static final double EARTH_RADIUS_M = 6_371_000;

    /** Một bước: toạ độ + hướng đi. */
    public record Step(double lat, double lng, double heading) {
    }

    /**
     * @param path        tuyến, ít nhất 2 điểm
     * @param stepM       quãng đường mỗi bước (vd 50 km/h x 5 giây = 69 m)
     * @param stopBeforeM dừng cách điểm cuối bao nhiêu mét (xe tới gần hiện trường, người vẫn phải bấm "Tới nơi")
     * @return các bước, bước đầu ở điểm xuất phát, bước cuối cách đích stopBeforeM (tuyến ngắn hơn thì dừng ở đích)
     */
    public List<Step> steps(List<double[]> path, double stepM, double stopBeforeM) {
        List<Step> steps = new ArrayList<>();
        if (path == null || path.size() < 2 || stepM <= 0) return steps;
        double[] cumulative = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            cumulative[i] = cumulative[i - 1] + distanceM(path.get(i - 1), path.get(i));
        }
        double total = cumulative[path.size() - 1];
        double end = Math.max(0, total - stopBeforeM);
        int segment = 1;
        for (double s = 0; ; s += stepM) {
            double at = Math.min(s, end);
            while (segment < path.size() - 1 && cumulative[segment] < at) segment++;
            double[] a = path.get(segment - 1);
            double[] b = path.get(segment);
            double length = cumulative[segment] - cumulative[segment - 1];
            double f = length <= 0 ? 0 : (at - cumulative[segment - 1]) / length;
            steps.add(new Step(a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, bearing(a, b)));
            if (at >= end) break;
        }
        return steps;
    }

    public double distanceM(double[] a, double[] b) {
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLng = Math.toRadians(b[1] - a[1]);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    /** Khoảng cách ngắn nhất (m) từ điểm tới tuyến; chiếu phẳng quanh điểm, đủ chính xác trong vài km. */
    public double distanceToPathM(List<double[]> path, double[] p) {
        double k = Math.cos(Math.toRadians(p[0]));
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < path.size(); i++) {
            double ax = (path.get(i)[1] - p[1]) * k;
            double ay = path.get(i)[0] - p[0];
            double dx = (path.get(i + 1)[1] - path.get(i)[1]) * k;
            double dy = path.get(i + 1)[0] - path.get(i)[0];
            double len = dx * dx + dy * dy;
            double t = len == 0 ? 0 : Math.max(0, Math.min(1, (-ax * dx - ay * dy) / len));
            best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy));
        }
        return best * Math.toRadians(1) * EARTH_RADIUS_M;
    }

    /** Điểm cách p một quãng meters theo hướng headingDeg (0 = bắc, 90 = đông). */
    public double[] offset(double[] p, double headingDeg, double meters) {
        double d = meters / EARTH_RADIUS_M;
        double h = Math.toRadians(headingDeg);
        double lat1 = Math.toRadians(p[0]);
        double lng1 = Math.toRadians(p[1]);
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(h));
        double lng2 = lng1 + Math.atan2(Math.sin(h) * Math.sin(d) * Math.cos(lat1),
                Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));
        return new double[]{Math.toDegrees(lat2), Math.toDegrees(lng2)};
    }

    public double bearing(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLng = Math.toRadians(b[1] - a[1]);
        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
