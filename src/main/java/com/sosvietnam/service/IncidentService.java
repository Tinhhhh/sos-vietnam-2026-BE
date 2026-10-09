package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.model.payload.response.IncidentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IncidentService {
    CreateIncidentResponse createIncident(CreateIncidentRequest request);

    IncidentResponse dispatchIncident(String id, DispatchRequest request);

    List<IncidentResponse> getRecentIncidents();

    List<IncidentResponse.MediaResponse> addMedia(String id, String citizenAccessToken, List<MultipartFile> files);

    MediaAccess loadMedia(String id, String fileName);
}
