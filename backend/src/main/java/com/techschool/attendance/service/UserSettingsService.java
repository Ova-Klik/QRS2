package com.techschool.attendance.service;

import com.techschool.attendance.data.model.AuditLog;
import com.techschool.attendance.data.model.SystemSetting;
import com.techschool.attendance.data.repository.SystemSettingRepository;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private static final String[] NETWORK_KEYS = {
        "school_name", "school_address", "school_email", "school_website",
        "school_wifi_ssid", "school_ip_range", "network_enforce",
        "qr_window_start", "qr_window_end", "late_threshold",
        "school_latitude", "school_longitude", "school_geofence_radius_meters", "geofence_enforce", "geofence_fallback_enabled",
        "qr_refresh_interval", "qr_refresh_enabled"
    };
    private static final String[] NETWORK_DEFAULTS = {
        "Tech School", "Lagos, Nigeria", "admin@techschool.edu.ng", "https://techschool.edu.ng",
        "TechSchool-WiFi", "192.168.1.0/24", "false",
        "07:00", "12:00", "08:31",
        "6.5244", "3.3792", "150", "false", "true",
        "15", "true"
    };

    private final SystemSettingRepository systemSettingRepository;
    private final AuditService auditService;

    public Map<String, String> getNetworkSettings() {
        Map<String, String> settings = new LinkedHashMap<>();
        for (int i = 0; i < NETWORK_KEYS.length; i++) {
            SystemSetting setting = systemSettingRepository.findByKey(NETWORK_KEYS[i]).orElse(null);
            settings.put(NETWORK_KEYS[i], setting != null ? setting.getValue() : NETWORK_DEFAULTS[i]);
        }
        return settings;
    }

    public Map<String, String> updateNetworkSettings(String actorId, String actorName, Map<String, String> updates) {
        if (updates.containsKey("qr_refresh_interval")) {
            String value = updates.get("qr_refresh_interval");
            try {
                int interval = Integer.parseInt(value != null ? value.trim() : "");
                if (interval < 5 || interval > 600) {
                    throw AppException.badRequest("QR Refresh Interval must be between 5 and 600 seconds.");
                }
            } catch (NumberFormatException e) {
                throw AppException.badRequest("QR Refresh Interval must be a valid integer between 5 and 600 seconds.");
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            SystemSetting setting = systemSettingRepository.findByKey(entry.getKey()).orElse(new SystemSetting());
            setting.setKey(entry.getKey());
            setting.setValue(entry.getValue());
            systemSettingRepository.save(setting);
            result.put(entry.getKey(), entry.getValue());
        }
        auditService.log(actorId, actorName, "SUPER_ADMIN", AuditLog.ActionType.USER_UPDATED,
                actorId, actorName, "Network settings updated: " + String.join(", ", updates.keySet()), null);
        return result;
    }
}
