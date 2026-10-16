package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/dashboard/summary: số liệu thống kê trong khoảng ngày [from, to] (ngày giờ Việt Nam)
 * theo phạm vi của người xem. Các số chưa tính được thì null (vd thời gian nhận khi chưa ca nào được nhận,
 * báo khống / hoàn tất khi chưa có module đóng ca), FE hiện "—" thay vì 0.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    @JsonProperty("scope")
    private Scope scope;

    @JsonProperty("range")
    private Range range;

    @JsonProperty("kpis")
    private Kpis kpis;

    @JsonProperty("by_tag")
    private List<TagCount> byTag;

    // 24 số, giờ Việt Nam
    @JsonProperty("by_hour")
    private List<Long> byHour;

    // Đủ mọi ngày trong khoảng, ngày không có ca = 0
    @JsonProperty("by_day")
    private List<DayCount> byDay;

    // 7 hàng (Thứ 2 .. Chủ nhật) x 24 giờ
    @JsonProperty("heatmap")
    private List<List<Long>> heatmap;

    // Cấp tỉnh: theo xã/phường; toàn quốc: theo tỉnh; cấp xã: rỗng
    @JsonProperty("by_area")
    private List<AreaStats> byArea;

    // Cấp tỉnh và toàn quốc: số ca của MỌI lực lượng trong phạm vi địa bàn (không lọc theo lực lượng),
    // để biểu đồ tròn "lực lượng nào được gọi nhiều nhất" có ý nghĩa
    @JsonProperty("by_agency")
    private List<AgencyCount> byAgency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scope {
        @JsonProperty("agency")
        private AgencyType agency;
        @JsonProperty("level")
        private UnitLevel level;
        @JsonProperty("province")
        private String province;
        @JsonProperty("ward")
        private String ward;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Range {
        @JsonProperty("from")
        private LocalDate from;
        @JsonProperty("to")
        private LocalDate to;
        @JsonProperty("previous_from")
        private LocalDate previousFrom;
        @JsonProperty("previous_to")
        private LocalDate previousTo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kpis {
        @JsonProperty("total")
        private long total;
        @JsonProperty("previous_total")
        private long previousTotal;
        // Đang chờ nhận ngay lúc này (không phụ thuộc khoảng ngày)
        @JsonProperty("pending")
        private long pending;
        @JsonProperty("accepted")
        private long accepted;
        @JsonProperty("escalated")
        private long escalated;
        @JsonProperty("avg_accept_seconds")
        private Double avgAcceptSeconds;
        @JsonProperty("median_accept_seconds")
        private Double medianAcceptSeconds;
        // 0..1: ca được nhận trong sla_minutes kể từ lúc báo / ca đã nhận
        @JsonProperty("within_sla_rate")
        private Double withinSlaRate;
        @JsonProperty("sla_minutes")
        private long slaMinutes;
        // Chờ module đóng ca
        @JsonProperty("fake_alarm_count")
        private Long fakeAlarmCount;
        @JsonProperty("resolved_count")
        private Long resolvedCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TagCount {
        @JsonProperty("tag")
        private String tag;
        @JsonProperty("count")
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DayCount {
        @JsonProperty("date")
        private LocalDate date;
        @JsonProperty("total")
        private long total;
        @JsonProperty("escalated")
        private long escalated;
        @JsonProperty("accepted")
        private long accepted;
        // 0..1, null khi ngày đó chưa ca nào được nhận
        @JsonProperty("within_sla_rate")
        private Double withinSlaRate;

        public DayCount(LocalDate date, long total, long escalated) {
            this(date, total, escalated, 0, null);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AreaStats {
        @JsonProperty("name")
        private String name;
        @JsonProperty("total")
        private long total;
        @JsonProperty("pending")
        private long pending;
        @JsonProperty("escalated")
        private long escalated;
        @JsonProperty("avg_accept_seconds")
        private Double avgAcceptSeconds;
        @JsonProperty("within_sla_rate")
        private Double withinSlaRate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgencyCount {
        @JsonProperty("agency")
        private AgencyType agency;
        @JsonProperty("total")
        private long total;
    }
}
