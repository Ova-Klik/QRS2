package com.techschool.attendance.data.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "network_settings")
public class NetworkSettings {

    @Id
    @Builder.Default
    private String id = "default";

    @Builder.Default
    private List<String> schoolWifiSsids = new ArrayList<>();

    private String schoolIpRange;

    @Builder.Default
    private boolean enforceNetwork = false;

    private double schoolLatitude;
    private double schoolLongitude;

    @Builder.Default
    private double allowedRadiusMeters = 150.0;

    @Builder.Default
    private boolean enforceGeolocation = false;

    private Instant updatedAt;
    private String updatedBy;
}
