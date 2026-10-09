package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentMediaItem;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.model.payload.response.IncidentResponse;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.IncidentService;
import com.sosvietnam.service.MediaAccess;
import com.sosvietnam.service.MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private static final String INCIDENT_TOPIC = "/topic/incidents";
    private static final int MAX_MEDIA_PER_INCIDENT = 10;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 15L * 1024 * 1024;
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/quicktime", "mov"
    );

    private final IncidentRepository incidentRepository;
    private final GeoService geoService;
    private final MediaStorageService mediaStorageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final SecureRandom secureRandom = new SecureRandom();

    // Deliberately not @Transactional: a failed boundary lookup must not mark the
    // transaction rollback-only and take the SOS down with it. save() has its own.
    @Override
    public CreateIncidentResponse createIncident(CreateIncidentRequest request) {
        String id = "SOS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Point point = geometryFactory.createPoint(new Coordinate(request.getLongitude(), request.getLatitude()));
        point.setSRID(4326);

        String ward = null;
        String province = null;
        try {
            Map<String, Object> resolved = geoService.resolveLocation(request.getLatitude(), request.getLongitude());
            ward = (String) resolved.getOrDefault("ward", null);
            province = (String) resolved.getOrDefault("province", null);
        } catch (Exception e) {
            // An SOS must never be lost because the boundary lookup failed.
            log.warn("Could not resolve jurisdiction for {}: {}", id, e.getMessage());
        }

        String citizenAccessToken = newAccessToken();

        Incident incident = Incident.builder()
                .id(id)
                .citizenName(request.getCitizenName())
                .phone(request.getPhone())
                .emergencyType(request.getEmergencyType())
                .status(IncidentStatus.PENDING)
                .description(request.getDescription())
                .incidentTags(cleanTags(request.getIncidentTags()))
                .address(request.getAddress())
                .ward(ward)
                .province(province)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracy(request.getAccuracy())
                .location(point)
                .citizenTokenHash(sha256(citizenAccessToken))
                .createdAt(LocalDateTime.now())
                .build();

        Incident saved = incidentRepository.save(incident);
        log.info("🚨 [SOS ALERT] New Incident Registered: ID={}, Type={}, Location={}, {}", id, request.getEmergencyType(), ward, province);

        IncidentResponse response = IncidentResponse.from(saved);
        broadcast(response);
        return new CreateIncidentResponse(response, citizenAccessToken);
    }

    @Transactional
    @Override
    public IncidentResponse dispatchIncident(String id, DispatchRequest request) {
        Incident incident = findIncident(id);

        incident.setStatus(IncidentStatus.DISPATCHED);
        incident.setAssignedStationId(request.getStationId());
        incident.setAssignedDispatcher(request.getDispatcherName());
        incident.setUpdatedAt(LocalDateTime.now());

        Incident updated = incidentRepository.save(incident);
        log.info("🚒 [DISPATCHED] Incident {} assigned to station {} by {}", id, request.getStationId(), request.getDispatcherName());

        IncidentResponse response = IncidentResponse.from(updated);
        broadcast(response);
        return response;
    }

    @Override
    public List<IncidentResponse> getRecentIncidents() {
        return incidentRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(IncidentResponse::from)
                .toList();
    }

    @Transactional
    @Override
    public List<IncidentResponse.MediaResponse> addMedia(String id, String citizenAccessToken, List<MultipartFile> files) {
        Incident incident = findIncident(id);
        verifyCitizenToken(incident, citizenAccessToken);

        if (files == null || files.isEmpty()) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Chưa chọn ảnh/video nào");
        }
        List<IncidentMediaItem> media = new ArrayList<>(incident.getMedia() == null ? List.of() : incident.getMedia());
        if (media.size() + files.size() > MAX_MEDIA_PER_INCIDENT) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Tối đa " + MAX_MEDIA_PER_INCIDENT + " ảnh/video cho mỗi sự cố");
        }

        List<IncidentMediaItem> added = new ArrayList<>();
        for (MultipartFile file : files) {
            String contentType = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase();
            String extension = EXTENSION_BY_TYPE.get(contentType);
            if (extension == null) {
                throw new SosException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng không hỗ trợ: " + file.getOriginalFilename());
            }
            boolean isVideo = contentType.startsWith("video/");
            if (file.getSize() > (isVideo ? MAX_VIDEO_BYTES : MAX_IMAGE_BYTES)) {
                throw new SosException(HttpStatus.PAYLOAD_TOO_LARGE,
                        (isVideo ? "Video tối đa 15MB: " : "Ảnh tối đa 10MB: ") + file.getOriginalFilename());
            }

            String fileName = mediaStorageService.store(mediaFolder(id), file, extension);
            added.add(IncidentMediaItem.builder()
                    .type(isVideo ? "VIDEO" : "IMAGE")
                    .fileName(fileName)
                    .originalName(file.getOriginalFilename())
                    .contentType(contentType)
                    .size(file.getSize())
                    .uploadedAt(LocalDateTime.now())
                    .build());
        }

        media.addAll(added);
        incident.setMedia(media);
        Incident saved = incidentRepository.save(incident);
        log.info("📎 [MEDIA] {} file(s) attached to incident {}", added.size(), id);

        broadcast(IncidentResponse.from(saved));
        return added.stream().map(item -> IncidentResponse.toMediaResponse(id, item)).toList();
    }

    @Override
    public MediaAccess loadMedia(String id, String fileName) {
        Incident incident = findIncident(id);
        boolean attached = incident.getMedia() != null
                && incident.getMedia().stream().anyMatch(m -> m.getFileName().equals(fileName));
        if (!attached) {
            throw new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp");
        }
        return mediaStorageService.load(mediaFolder(id), fileName);
    }

    private Incident findIncident(String id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy vụ việc ID: " + id));
    }

    private void verifyCitizenToken(Incident incident, String token) {
        if (token == null || token.isBlank()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Thiếu mã truy cập của người báo tin");
        }
        String expected = incident.getCitizenTokenHash();
        boolean valid = expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), sha256(token).getBytes(StandardCharsets.US_ASCII));
        if (!valid) {
            throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ cho sự cố này");
        }
    }

    private void broadcast(IncidentResponse incident) {
        try {
            messagingTemplate.convertAndSend(INCIDENT_TOPIC, incident);
        } catch (Exception e) {
            log.warn("Could not broadcast incident via websocket: {}", e.getMessage());
        }
    }

    private static String mediaFolder(String incidentId) {
        return "incidents/" + incidentId;
    }

    private static List<String> cleanTags(List<String> tags) {
        if (tags == null) return new ArrayList<>();
        return new ArrayList<>(tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .toList());
    }

    private String newAccessToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
