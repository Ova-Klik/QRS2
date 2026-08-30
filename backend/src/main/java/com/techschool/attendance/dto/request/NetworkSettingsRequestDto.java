package com.techschool.attendance.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class NetworkSettingsRequestDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSettingsRequest {
        private List<String> schoolWifiSsids;
        private String schoolIpRange;
        private Boolean enforceNetwork;
        private Double schoolLatitude;
        private Double schoolLongitude;
        private Double allowedRadiusMeters;
        private Boolean enforceGeolocation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddSsidRequest {
        @NotBlank(message = "SSID must not be blank")
        private String ssid;
    }
}
