package com.sosvietnam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.config.BeanConfiguration;
import com.sosvietnam.config.SecurityConfig;
import com.sosvietnam.controller.AuthController;
import com.sosvietnam.controller.GeoController;
import com.sosvietnam.controller.IncidentController;
import com.sosvietnam.controller.StationController;
import com.sosvietnam.controller.WeatherController;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.RefreshToken;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.StationLevel;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.model.payload.response.DataStatusResponse;
import com.sosvietnam.model.payload.response.IncidentResponse;
import com.sosvietnam.model.payload.response.LocationResponse;
import com.sosvietnam.model.payload.response.StationResponse;
import com.sosvietnam.model.payload.response.WeatherResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.RefreshTokenRepository;
import com.sosvietnam.repository.RoleRepository;
import com.sosvietnam.security.*;
import com.sosvietnam.service.BandoSyncService;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.IncidentService;
import com.sosvietnam.service.WeatherService;
import com.sosvietnam.service.implement.AuthenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test HTTP thật qua MockMvc với SecurityConfig + JWT filter thật.
 * Chỉ giả lập (mock) tầng repository/DB vì môi trường test không có PostGIS.
 */
@WebMvcTest(controllers = {AuthController.class, GeoController.class, StationController.class, IncidentController.class,
        WeatherController.class})
@Import({SecurityConfig.class, BeanConfiguration.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        JwtAuthenticationEntryPoint.class, CustomLogoutHandler.class, CustomUserDetailService.class, AuthenServiceImpl.class})
@TestPropertySource(properties = "app.jwt.secret-key=q83vEi7pZf2mR1tYw9LkXc4N6bHs0aJdUe5gVo8iTzQyP3rKfW2xCnMbLh7sDjA1")
class ApiContractTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean AccountRepository accountRepository;
    @MockitoBean RoleRepository roleRepository;
    @MockitoBean RefreshTokenRepository refreshTokenRepository;
    @MockitoBean GeoService geoService;
    @MockitoBean BandoSyncService bandoSyncService;
    @MockitoBean IncidentService incidentService;
    @MockitoBean WeatherService weatherService;

    // "DB" giả cho refresh token: nếu code lưu trùng thì list sẽ có 2 phần tử
    final List<RefreshToken> refreshTable = Collections.synchronizedList(new ArrayList<>());
    final Map<String, Account> accounts = new ConcurrentHashMap<>();
    final Map<String, Role> roles = new HashMap<>();

    @BeforeEach
    void setUp() {
        long id = 1;
        for (String r : List.of("ADMIN", "OFFICER", "CITIZEN")) {
            roles.put(r, Role.builder().id(id++).roleName(r).build());
        }
        accounts.put("officer@sos.vn", Account.builder().id(UUID.randomUUID()).firstName("An").lastName("Nguyễn")
                .email("officer@sos.vn").password(passwordEncoder.encode("Officer@123")).role(roles.get("OFFICER"))
                .agencyType(AgencyType.POLICE).province("TP. Hà Nội").isActive(true).build());

        when(roleRepository.findByRoleName(anyString())).thenAnswer(i -> Optional.ofNullable(roles.get((String) i.getArgument(0))));
        when(accountRepository.findByEmail(anyString())).thenAnswer(i -> Optional.ofNullable(accounts.get((String) i.getArgument(0))));
        when(accountRepository.existsByEmail(anyString())).thenAnswer(i -> accounts.containsKey((String) i.getArgument(0)));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> {
            Account a = i.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            accounts.put(a.getEmail(), a);
            return a;
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(i -> {
            RefreshToken t = i.getArgument(0);
            if (!refreshTable.contains(t)) refreshTable.add(t);
            return t;
        });
        // giống Spring Data: >1 dòng cùng token sẽ ném lỗi (đây là bug cũ)
        when(refreshTokenRepository.findByToken(anyString())).thenAnswer(i -> {
            List<RefreshToken> found = refreshTable.stream().filter(t -> t.getToken().equals(i.getArgument(0))).toList();
            if (found.size() > 1) throw new org.springframework.dao.IncorrectResultSizeDataAccessException(1, found.size());
            return found.stream().findFirst();
        });
        when(refreshTokenRepository.findByJitId(any())).thenAnswer(i ->
                refreshTable.stream().filter(t -> i.getArgument(0).equals(t.getJitId())).findFirst().orElse(null));
    }

    private JsonNode json(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private JsonNode login(String email, String password) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn());
    }

    // ── Auth ──────────────────────────────────────────────────────────

    @Test
    void login_returnsResponseBuilderEnvelope_withSnakeCaseTokens() throws Exception {
        JsonNode body = login("officer@sos.vn", "Officer@123");
        assertThat(body.get("http_status").asInt()).isEqualTo(200);
        assertThat(body.has("time_stamp")).isTrue();
        JsonNode data = body.get("data");
        assertThat(data.get("access_token").asText()).isNotBlank();
        assertThat(data.get("refresh_token").asText()).isNotBlank();
        // Chỉ trả cặp token (style HIV-TMSS); thông tin tài khoản lấy qua /me
        assertThat(data.size()).isEqualTo(2);
        // refresh token chỉ được lưu 1 lần
        assertThat(refreshTable).hasSize(1);
    }

    @Test
    void login_wrongPassword_returns401WithVietnameseMessage() throws Exception {
        JsonNode body = json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"officer@sos.vn\",\"password\":\"sai\"}"))
                .andExpect(status().isUnauthorized()).andReturn());
        assertThat(body.get("message").asText()).isEqualTo("Email hoặc mật khẩu không chính xác");
    }

    @Test
    void me_returnsAccountInfo_withRoleAsString_andNoPassword() throws Exception {
        String access = login("officer@sos.vn", "Officer@123").get("data").get("access_token").asText();
        JsonNode data = json(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(data.get("role_name").asText()).isEqualTo("OFFICER");
        assertThat(data.get("email").asText()).isEqualTo("officer@sos.vn");
        assertThat(data.get("full_name").asText()).isEqualTo("Nguyễn An");
        assertThat(data.has("password")).isFalse();
        assertThat(data.get("role_name").isTextual()).isTrue();
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.http_status").value(401));
    }

    @Test
    void refresh_works_andRotatesTokens_andOldAccessIsRevoked() throws Exception {
        JsonNode first = login("officer@sos.vn", "Officer@123").get("data");
        String oldAccess = first.get("access_token").asText();

        JsonNode refreshed = json(mvc.perform(post("/api/v1/auth/refresh-token")
                        .header("Authorization", "Bearer " + first.get("refresh_token").asText()))
                .andExpect(status().isOk()).andReturn()).get("data");
        assertThat(refreshed.get("access_token").asText()).isNotEqualTo(oldAccess);

        // access token cũ bị thu hồi
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized());
        // access token mới dùng được
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refreshed.get("access_token").asText()))
                .andExpect(status().isOk());
        // dùng lại refresh token cũ -> 401
        mvc.perform(post("/api/v1/auth/refresh-token").header("Authorization", "Bearer " + first.get("refresh_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_withAccessToken_isRejected() throws Exception {
        String access = login("officer@sos.vn", "Officer@123").get("data").get("access_token").asText();
        mvc.perform(post("/api/v1/auth/refresh-token").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesAccessToken() throws Exception {
        String access = login("officer@sos.vn", "Officer@123").get("data").get("access_token").asText();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        assertThat(refreshTable.get(0).isRevoked()).isTrue();
    }

    @Test
    void logout_withGarbageToken_doesNot500() throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isOk());
    }

    @Test
    void register_rejectsRoleField_with400() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"hacker@x.vn\",\"password\":\"Password1\",\"roleName\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dữ liệu gửi lên không hợp lệ"));
        assertThat(accounts).doesNotContainKey("hacker@x.vn");
    }

    @Test
    void register_alwaysCreatesCitizen_andNeverCreatesRoles() throws Exception {
        JsonNode body = json(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dan@x.vn\",\"password\":\"Password1\",\"first_name\":\"Bình\",\"last_name\":\"Trần\"}"))
                .andExpect(status().isOk()).andReturn());
        // Đăng ký chỉ trả message, không tự đăng nhập
        assertThat(body.has("data")).isFalse();
        assertThat(accounts.get("dan@x.vn").fullName()).isEqualTo("Trần Bình");
        assertThat(accounts.get("dan@x.vn").getRole().getRoleName()).isEqualTo("CITIZEN");
        verify(roleRepository, never()).save(any());
    }

    @Test
    void register_weakPassword_returnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@x.vn\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.password").exists());
    }

    // ── Geo / Station ────────────────────────────────────────────────

    @Test
    void geoResolve_isPublic_andSnakeCase() throws Exception {
        when(geoService.resolveLocation(21.03, 105.85)).thenReturn(LocationResponse.builder()
                .latitude(21.03).longitude(105.85).ward("Phường Hoàn Kiếm").province("TP. Hà Nội").provinceCode("01").build());
        mvc.perform(get("/api/v1/geo/resolve").param("lat", "21.03").param("lng", "105.85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.province_code").value("01"))
                .andExpect(jsonPath("$.data.ward").value("Phường Hoàn Kiếm"));
    }

    @Test
    void geoResolve_missingParam_returns400() throws Exception {
        mvc.perform(get("/api/v1/geo/resolve").param("lat", "21.03")).andExpect(status().isBadRequest());
    }

    @Test
    void geoSyncNow_requiresAdmin() throws Exception {
        mvc.perform(post("/api/v1/geo/sync-now")).andExpect(status().isUnauthorized());
        String access = login("officer@sos.vn", "Officer@123").get("data").get("access_token").asText();
        mvc.perform(post("/api/v1/geo/sync-now").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.http_status").value(403));
        verify(bandoSyncService, never()).syncFromNationalDatabase();
    }

    @Test
    void stationsNearest_isPublic() throws Exception {
        when(geoService.findNearestStations(anyDouble(), anyDouble(), any(), anyDouble(), anyInt())).thenReturn(List.of());
        mvc.perform(get("/api/v1/stations/nearest").param("lat", "21").param("lng", "105").param("agency", "POLICE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void stationsList_isPublic_andPassesFilters() throws Exception {
        when(geoService.getStations(any(), any(), any())).thenReturn(List.of(StationResponse.builder()
                .id(1L).name("Phòng CSGT Công An TP. Cần Thơ").agencyType(AgencyType.CSGT).level(StationLevel.PROVINCE)
                .facilityType("Trụ sở chính").build()));
        mvc.perform(get("/api/v1/stations").param("province", "Cần Thơ").param("agency", "CSGT").param("level", "PROVINCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].agency_type").value("CSGT"))
                .andExpect(jsonPath("$.data[0].level").value("PROVINCE"))
                .andExpect(jsonPath("$.data[0].facility_type").value("Trụ sở chính"));
        verify(geoService).getStations("Cần Thơ", AgencyType.CSGT, StationLevel.PROVINCE);
    }

    @Test
    void stationsList_unknownLevel_returns400() throws Exception {
        mvc.perform(get("/api/v1/stations").param("level", "DISTRICT")).andExpect(status().isBadRequest());
    }

    @Test
    void dataStatus_requiresOfficer() throws Exception {
        when(geoService.getDataStatus()).thenReturn(List.of(DataStatusResponse.builder()
                .dataset("rescue_stations").version("2026.10.15").total(3717).build()));
        mvc.perform(get("/api/v1/geo/data-status")).andExpect(status().isUnauthorized());
        String access = login("officer@sos.vn", "Officer@123").get("data").get("access_token").asText();
        mvc.perform(get("/api/v1/geo/data-status").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].dataset").value("rescue_stations"))
                .andExpect(jsonPath("$.data[0].version").value("2026.10.15"))
                .andExpect(jsonPath("$.data[0].total").value(3717));
    }

    @Test
    void weather_requiresOfficer_andIsSnakeCase() throws Exception {
        when(weatherService.getCurrentWeather(10.03, 105.78)).thenReturn(WeatherResponse.builder()
                .temperature(30.5).description("Có mây rải rác").isDay(true).windSpeed(7.2).build());
        mvc.perform(get("/api/v1/weather/current").param("lat", "10.03").param("lng", "105.78"))
                .andExpect(status().isUnauthorized());
        String access = login("officer@sos.vn", "Officer@123").get("data").get("access_token").asText();
        MvcResult result = mvc.perform(get("/api/v1/weather/current").param("lat", "10.03").param("lng", "105.78")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temperature").value(30.5))
                .andExpect(jsonPath("$.data.is_day").value(true))
                .andExpect(jsonPath("$.data.wind_speed").value(7.2))
                .andReturn();
        assertThat(json(result).get("data").size()).isEqualTo(4);
    }

    @Test
    void oldPaths_areGone() throws Exception {
        mvc.perform(post("/api/incidents").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
    }

    // ── Incident ─────────────────────────────────────────────────────

    @Test
    void createIncident_isPublic_readsSnakeCase_andReturnsSnakeCase() throws Exception {
        IncidentResponse inc = IncidentResponse.builder().id("SOS-20261014-ABC123").citizenName("Người dân")
                .emergencyType(EmergencyType.POLICE).status(IncidentStatus.PENDING).incidentTags(List.of("Trộm"))
                .media(List.of()).latitude(21.0).longitude(105.0).createdAt(LocalDateTime.now()).build();
        when(incidentService.createIncident(any())).thenAnswer(i -> {
            var req = (com.sosvietnam.model.payload.request.CreateIncidentRequest) i.getArgument(0);
            assertThat(req.getEmergencyType()).isEqualTo(EmergencyType.POLICE);
            assertThat(req.getCitizenName()).isEqualTo("Người dân");
            assertThat(req.getIncidentTags()).containsExactly("Trộm");
            return new CreateIncidentResponse(inc, "tok123");
        });
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content("""
                        {"emergency_type":"POLICE","citizen_name":"Người dân","incident_tags":["Trộm"],
                         "latitude":21.0,"longitude":105.0,"accuracy":12}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.citizen_access_token").value("tok123"))
                .andExpect(jsonPath("$.data.incident.id").value("SOS-20261014-ABC123"))
                .andExpect(jsonPath("$.data.incident.emergency_type").value("POLICE"))
                .andExpect(jsonPath("$.data.incident.citizen_name").value("Người dân"));
    }

    @Test
    void createIncident_validationError_returnsFieldMap() throws Exception {
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON).content("{\"latitude\":200}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.emergencyType").exists());
    }

    @Test
    void recentIncidents_requiresOfficer() throws Exception {
        when(incidentService.getRecentIncidents()).thenReturn(List.of());
        mvc.perform(get("/api/v1/incidents/recent")).andExpect(status().isUnauthorized());
        String access = login("officer@sos.vn", "Officer@123").get("data").get("access_token").asText();
        mvc.perform(get("/api/v1/incidents/recent").header("Authorization", "Bearer " + access)).andExpect(status().isOk());
    }

    @Test
    void mediaUrl_usesV1Path() {
        var item = com.sosvietnam.model.entity.IncidentMediaItem.builder().type("IMAGE").fileName("a.jpg").build();
        assertThat(IncidentResponse.toMediaResponse("SOS-1", item).getUrl()).isEqualTo("/api/v1/incidents/SOS-1/media/a.jpg");
    }
}
