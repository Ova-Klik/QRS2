package com.techschool.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class NetworkSettingsResponseDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettingsResponse {
        private String id;
        private List<String> schoolWifiSsids;
        private String schoolIpRange;
        private boolean enforceNetwork;
        private double schoolLatitude;
        private double schoolLongitude;
        private double allowedRadiusMeters;
        private boolean enforceGeolocation;
        private Instant updatedAt;
        private String updatedBy;
    }
}
