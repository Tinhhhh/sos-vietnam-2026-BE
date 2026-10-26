package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.payload.response.RoadSnapResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Điểm xe dừng: đường có tên, không phải hẻm / kiệt / ngõ / ngách, trong bán kính; không có thì điểm gần nhất. */
class RoadSnapServiceTest {

    private static com.fasterxml.jackson.databind.JsonNode waypoints(String json) throws Exception {
        return new ObjectMapper().readTree(json);
    }

    @Test
    void skipsAlleysAndUnnamedRoads_takesTheNearestMainRoad() throws Exception {
        RoadSnapResponse r = RoadSnapServiceImpl.pick(waypoints("""
                [{"name":"Ngõ 35 Phố Tây Sơn","distance":13.2,"location":[105.8269,21.0129]},
                 {"name":"","distance":20,"location":[105.8270,21.0130]},
                 {"name":"Hẻm 341/34 Xô viết Nghệ Tĩnh","distance":25,"location":[105.8271,21.0131]},
                 {"name":"Phố Tây Sơn","distance":33.4,"location":[105.8272,21.0132]},
                 {"name":"Phố Tây Sơn","distance":36,"location":[105.8273,21.0133]}]"""), 300);
        assertThat(r.getRoadName()).isEqualTo("Phố Tây Sơn");
        assertThat(r.getSource()).isEqualTo("MAIN_ROAD");
        assertThat(r.getDistanceM()).isEqualTo(33);
        assertThat(r.getLatitude()).isEqualTo(21.0132);
        assertThat(r.getLongitude()).isEqualTo(105.8272);
    }

    @Test
    void onlyAlleysInRadius_fallsBackToTheNearestDrivablePoint() throws Exception {
        RoadSnapResponse r = RoadSnapServiceImpl.pick(waypoints("""
                [{"name":"","distance":11,"location":[106.7105,10.8035]},
                 {"name":"Kiệt 12 Lê Lợi","distance":40,"location":[106.7106,10.8036]},
                 {"name":"Bạch Đằng","distance":350,"location":[106.7107,10.8037]}]"""), 300);
        assertThat(r.getSource()).isEqualTo("NEAREST_ROAD");
        assertThat(r.getRoadName()).isNull();
        assertThat(r.getDistanceM()).isEqualTo(11);
    }

    @Test
    void alleyPattern_matchesVietnameseNames_notMainRoadsStartingTheSameWay() {
        assertThat(RoadSnapServiceImpl.ALLEY.matcher("Hẻm 51 Nguyễn Trãi").find()).isTrue();
        assertThat(RoadSnapServiceImpl.ALLEY.matcher("NGÕ 35 Phố Tây Sơn").find()).isTrue();
        assertThat(RoadSnapServiceImpl.ALLEY.matcher("Ngách 4/12").find()).isTrue();
        assertThat(RoadSnapServiceImpl.ALLEY.matcher("Ngô Quyền").find()).isFalse();
        assertThat(RoadSnapServiceImpl.ALLEY.matcher("Hẻm").find()).isTrue();
        assertThat(RoadSnapServiceImpl.ALLEY.matcher("Nguyễn Văn Linh").find()).isFalse();
    }

    @Test
    void noWaypoints_returnsNull() throws Exception {
        assertThat(RoadSnapServiceImpl.pick(waypoints("[]"), 300)).isNull();
    }
}
