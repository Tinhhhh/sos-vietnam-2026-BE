package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.UnitDutyAssignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** GET /api/v1/unit/duty: trực ban hiện tại + lịch sử (mới nhất trước). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DutyResponse {

    @JsonProperty("current")
    private Assignment current;

    @JsonProperty("history")
    private List<Assignment> history;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assignment {
        @JsonProperty("id")
        private Long id;

        @JsonProperty("member_id")
        private UUID memberId;

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("rank_label")
        private String rankLabel;

        @JsonProperty("assigned_by_name")
        private String assignedByName;

        @JsonProperty("started_at")
        private LocalDateTime startedAt;

        @JsonProperty("ended_at")
        private LocalDateTime endedAt;

        public static Assignment from(UnitDutyAssignment d) {
            return Assignment.builder()
                    .id(d.getId())
                    .memberId(d.getMember().getId())
                    .fullName(d.getMember().getFullName())
                    .rankLabel(d.getMember().getRank() == null ? null : d.getMember().getRank().getLabel())
                    .assignedByName(d.getAssignedBy() == null ? null : d.getAssignedBy().displayName())
                    .startedAt(d.getStartedAt())
                    .endedAt(d.getEndedAt())
                    .build();
        }
    }
}
