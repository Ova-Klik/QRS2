package com.techschool.attendance.service;

import com.techschool.attendance.data.model.NetworkSettings;
import com.techschool.attendance.data.repository.NetworkSettingsRepository;
import com.techschool.attendance.dto.request.NetworkSettingsRequestDto;
import com.techschool.attendance.dto.response.NetworkSettingsResponseDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkSettingsService {

    public static final String SINGLETON_ID = "default";

    private final NetworkSettingsRepository repository;

    @Value("${app.network.school-wifi-ssid:TechSchool-WiFi}")
    private String defaultWifiSsid;

    @Value("${app.network.school-ip-range:192.168.1.0/24}")
    private String defaultIpRange;

    @Value("${app.network.enforce:false}")
    private boolean defaultEnforceNetwork;

    @Value("${app.geofence.school-latitude:6.5244}")
    private double defaultSchoolLatitude;

    @Value("${app.geofence.school-longitude:3.3792}")
    private double defaultSchoolLongitude;

    @Value("${app.geofence.allowed-radius-meters:150.0}")
    private double defaultAllowedRadiusMeters;

    @Value("${app.geofence.enforce:false}")
    private boolean defaultEnforceGeolocation;

    /**
     * Gets the active NetworkSettings from MongoDB.
     * Seeds initial defaults from config *only* if no document exists in MongoDB.
     */
    public NetworkSettings getSettingsEntity() {
        return repository.findById(SINGLETON_ID).orElseGet(() -> {
            log.info("No NetworkSettings document found in MongoDB. Seeding initial defaults from application properties.");
            List<String> initialSsids = new ArrayList<>();
            if (defaultWifiSsid != null && !defaultWifiSsid.isBlank()) {
                initialSsids.add(defaultWifiSsid.trim());
            }

            NetworkSettings initialDoc = NetworkSettings.builder()
                    .id(SINGLETON_ID)
                    .schoolWifiSsids(initialSsids)
                    .schoolIpRange(defaultIpRange != null ? defaultIpRange.trim() : "192.168.1.0/24")
                    .enforceNetwork(defaultEnforceNetwork)
                    .schoolLatitude(defaultSchoolLatitude)
                    .schoolLongitude(defaultSchoolLongitude)
                    .allowedRadiusMeters(defaultAllowedRadiusMeters)
                    .enforceGeolocation(defaultEnforceGeolocation)
                    .updatedAt(Instant.now())
                    .updatedBy("SYSTEM_INIT")
                    .build();

            return repository.save(initialDoc);
        });
    }

    public NetworkSettingsResponseDto.SettingsResponse getSettings() {
        return mapToResponse(getSettingsEntity());
    }

    public NetworkSettingsResponseDto.SettingsResponse updateSettings(NetworkSettingsRequestDto.UpdateSettingsRequest request, String adminId) {
        NetworkSettings settings = getSettingsEntity();

        if (request.getSchoolWifiSsids() != null) {
            List<String> cleanedSsids = new ArrayList<>();
            for (String ssid : request.getSchoolWifiSsids()) {
                if (ssid != null && !ssid.isBlank()) {
                    cleanedSsids.add(ssid.trim());
                }
            }
            settings.setSchoolWifiSsids(cleanedSsids);
        }

        if (request.getSchoolIpRange() != null) {
            settings.setSchoolIpRange(request.getSchoolIpRange().trim());
        }

        if (request.getEnforceNetwork() != null) {
            settings.setEnforceNetwork(request.getEnforceNetwork());
        }

        if (request.getSchoolLatitude() != null) {
            settings.setSchoolLatitude(request.getSchoolLatitude());
        }

        if (request.getSchoolLongitude() != null) {
            settings.setSchoolLongitude(request.getSchoolLongitude());
        }

        if (request.getAllowedRadiusMeters() != null) {
            if (request.getAllowedRadiusMeters() <= 0) {
                throw AppException.badRequest("Allowed radius meters must be greater than 0");
            }
            settings.setAllowedRadiusMeters(request.getAllowedRadiusMeters());
        }

        if (request.getEnforceGeolocation() != null) {
            settings.setEnforceGeolocation(request.getEnforceGeolocation());
        }

        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(adminId);

        NetworkSettings saved = repository.save(settings);
        log.info("NetworkSettings updated by admin {}: enforceNetwork={}, enforceGeo={}, ssids={}",
                adminId, saved.isEnforceNetwork(), saved.isEnforceGeolocation(), saved.getSchoolWifiSsids());
        return mapToResponse(saved);
    }

    public NetworkSettingsResponseDto.SettingsResponse addSsid(String ssid, String adminId) {
        if (ssid == null || ssid.isBlank()) {
            throw AppException.badRequest("SSID cannot be blank");
        }
        String cleaned = ssid.trim();
        NetworkSettings settings = getSettingsEntity();
        if (settings.getSchoolWifiSsids() == null) {
            settings.setSchoolWifiSsids(new ArrayList<>());
        }

        boolean alreadyExists = settings.getSchoolWifiSsids().stream()
                .anyMatch(s -> s.equalsIgnoreCase(cleaned));

        if (!alreadyExists) {
            settings.getSchoolWifiSsids().add(cleaned);
            settings.setUpdatedAt(Instant.now());
            settings.setUpdatedBy(adminId);
            settings = repository.save(settings);
            log.info("Admin {} added SSID '{}' to NetworkSettings", adminId, cleaned);
        }

        return mapToResponse(settings);
    }

    public NetworkSettingsResponseDto.SettingsResponse removeSsid(String ssid, String adminId) {
        if (ssid == null || ssid.isBlank()) {
            throw AppException.badRequest("SSID cannot be blank");
        }
        String cleaned = ssid.trim();
        NetworkSettings settings = getSettingsEntity();
        if (settings.getSchoolWifiSsids() != null) {
            boolean removed = settings.getSchoolWifiSsids().removeIf(s -> s.equalsIgnoreCase(cleaned));
            if (removed) {
                settings.setUpdatedAt(Instant.now());
                settings.setUpdatedBy(adminId);
                settings = repository.save(settings);
                log.info("Admin {} removed SSID '{}' from NetworkSettings", adminId, cleaned);
            }
        }
        return mapToResponse(settings);
    }

    private NetworkSettingsResponseDto.SettingsResponse mapToResponse(NetworkSettings settings) {
        return NetworkSettingsResponseDto.SettingsResponse.builder()
                .id(settings.getId())
                .schoolWifiSsids(settings.getSchoolWifiSsids() != null ? new ArrayList<>(settings.getSchoolWifiSsids()) : new ArrayList<>())
                .schoolIpRange(settings.getSchoolIpRange())
                .enforceNetwork(settings.isEnforceNetwork())
                .schoolLatitude(settings.getSchoolLatitude())
                .schoolLongitude(settings.getSchoolLongitude())
                .allowedRadiusMeters(settings.getAllowedRadiusMeters())
                .enforceGeolocation(settings.isEnforceGeolocation())
                .updatedAt(settings.getUpdatedAt())
                .updatedBy(settings.getUpdatedBy())
                .build();
    }
}
