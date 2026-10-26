package com.sosvietnam.util;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/** Tuyến đường dạng "encoded polyline" của Google (độ chính xác 1e-5, như incident_routes.polyline). */
@UtilityClass
public class Polylines {

    /** @return các điểm [lat, lng]; chuỗi rỗng / hỏng thì trả danh sách đã giải được tới chỗ hỏng */
    public List<double[]> decode(String encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return points;
        int index = 0;
        int lat = 0;
        int lng = 0;
        try {
            while (index < encoded.length()) {
                int[] r = next(encoded, index);
                lat += r[0];
                r = next(encoded, r[1]);
                lng += r[0];
                index = r[1];
                points.add(new double[]{lat / 1e5, lng / 1e5});
            }
        } catch (StringIndexOutOfBoundsException e) {
            // chuỗi bị cắt: giữ phần đã đọc được
        }
        return points;
    }

    /** Ngược lại của decode: các điểm [lat, lng] -> chuỗi polyline 1e-5. */
    public String encode(List<double[]> points) {
        StringBuilder out = new StringBuilder();
        long prevLat = 0;
        long prevLng = 0;
        for (double[] p : points) {
            long lat = Math.round(p[0] * 1e5);
            long lng = Math.round(p[1] * 1e5);
            put(out, lat - prevLat);
            put(out, lng - prevLng);
            prevLat = lat;
            prevLng = lng;
        }
        return out.toString();
    }

    private void put(StringBuilder out, long value) {
        long v = value < 0 ? ~(value << 1) : value << 1;
        while (v >= 0x20) {
            out.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        out.append((char) (v + 63));
    }

    /** Một số có dấu: trả {giá trị, vị trí tiếp theo}. */
    private int[] next(String s, int index) {
        int result = 0;
        int shift = 0;
        int b;
        do {
            b = s.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        int value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{value, index};
    }
}
