package com.techschool.attendance.service;

import com.techschool.attendance.data.model.NetworkSettings;
import com.techschool.attendance.dto.request.QrRequestDto;
import com.techschool.attendance.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCheckInService {

    private final NetworkSettingsService networkSettingsService;

    public String verifyCheckIn(QrRequestDto.ScanRequest request, String studentId, String remoteIp) {
        NetworkSettings settings = networkSettingsService.getSettingsEntity();

        boolean enforceWifi = settings.isEnforceNetwork();
        boolean enforceGeo = settings.isEnforceGeolocation();

        if (enforceWifi && enforceGeo) {
            boolean wifiSuccess = isWifiVerified(request, remoteIp, settings);
            if (wifiSuccess) {
                log.info("Student {} verified check-in via WIFI.", studentId);
                return "WIFI";
            }
            log.info("Student {} failed Wi-Fi check; falling back to Geolocation check.", studentId);
            boolean geoSuccess = isGeolocationVerified(request, studentId, settings);
            if (geoSuccess) {
                log.info("Student {} verified check-in via GEOLOCATION (Wi-Fi fallback).", studentId);
                return "GEOLOCATION";
            }
            throw AppException.forbidden("Check-in failed: Unable to verify location via school Wi-Fi network or GPS geofence.");
        }

        if (enforceWifi && !enforceGeo) {
            boolean wifiSuccess = isWifiVerified(request, remoteIp, settings);
            if (!wifiSuccess) {
                String ssids = (settings.getSchoolWifiSsids() != null && !settings.getSchoolWifiSsids().isEmpty())
                        ? String.join(", ", settings.getSchoolWifiSsids()) : "None configured";
                log.warn("Student {} failed Wi-Fi check (Wi-Fi only mode). SSID={}, clientIP={}",
                        studentId, request.getNetworkSSID(), request.getClientIP());
                throw AppException.forbidden("Attendance can only be marked while connected to an authorized school network (" + ssids + ").");
            }
            log.info("Student {} verified check-in via WIFI.", studentId);
            return "WIFI";
        }

        if (!enforceWifi && enforceGeo) {
            boolean geoSuccess = isGeolocationVerified(request, studentId, settings);
            if (!geoSuccess) {
                throw AppException.forbidden("Attendance verification failed for geolocation.");
            }
            log.info("Student {} verified check-in via GEOLOCATION.", studentId);
            return "GEOLOCATION";
        }

        log.info("Student {} check-in unverified (network and geolocation enforcement disabled).", studentId);
        return "UNVERIFIED";
    }

    boolean isWifiVerified(QrRequestDto.ScanRequest request, String remoteIp, NetworkSettings settings) {
        List<String> allowedSsids = settings.getSchoolWifiSsids();
        String ipRange = settings.getSchoolIpRange();

        boolean ssidMatch = false;
        if (allowedSsids != null && !allowedSsids.isEmpty() && request.getNetworkSSID() != null && !request.getNetworkSSID().isBlank()) {
            String clientSsid = request.getNetworkSSID().trim();
            for (String allowed : allowedSsids) {
                if (allowed != null && allowed.equalsIgnoreCase(clientSsid)) {
                    ssidMatch = true;
                    break;
                }
            }
        }

        boolean ipMatch = false;
        String clientIp = (request.getClientIP() != null && !request.getClientIP().isBlank())
                ? request.getClientIP().trim()
                : (remoteIp != null ? remoteIp.trim() : null);

        if (clientIp != null && ipRange != null && !ipRange.isBlank()) {
            ipMatch = isIpInSchoolRange(clientIp, ipRange);
        }

        return ssidMatch && ipMatch;
    }

    boolean isGeolocationVerified(QrRequestDto.ScanRequest request, String studentId, NetworkSettings settings) {
        Double lat = request.getLatitude();
        Double lng = request.getLongitude();
        Double accuracy = request.getAccuracy();

        if (lat == null || lng == null) {
            log.warn("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"MISSING_COORDINATES\"}", studentId);
            throw AppException.badRequest("Location coordinates are required to mark attendance when geofencing is enabled.");
        }

        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0 || (lat == 0.0 && lng == 0.0)
                || Double.isNaN(lat) || Double.isNaN(lng) || Double.isInfinite(lat) || Double.isInfinite(lng)) {
            log.warn("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"INVALID_COORDINATES\"}", studentId);
            throw AppException.badRequest("Invalid location coordinates received. Please ensure your device has a valid GPS fix and try again.");
        }

        double schoolLat = settings.getSchoolLatitude();
        double schoolLng = settings.getSchoolLongitude();
        double allowedRadius = settings.getAllowedRadiusMeters();

        if (Double.isNaN(schoolLat) || Double.isInfinite(schoolLat) || schoolLat < -90.0 || schoolLat > 90.0 ||
            Double.isNaN(schoolLng) || Double.isInfinite(schoolLng) || schoolLng < -180.0 || schoolLng > 180.0) {
            log.warn("Geofence school location misconfigured: lat={}, lng={}", schoolLat, schoolLng);
            throw AppException.badRequest("Geofence school location is misconfigured. Please contact your administrator.");
        }

        if (Double.isNaN(allowedRadius) || Double.isInfinite(allowedRadius) || allowedRadius <= 0) {
            log.warn("Invalid geofence radius: {}. Must be > 0.", allowedRadius);
            throw AppException.badRequest("Geofence radius is misconfigured (must be > 0). Please contact your administrator.");
        }

        if (accuracy != null && !Double.isFinite(accuracy)) {
            throw AppException.badRequest("Invalid location accuracy value received. Please try again.");
        }

        if (accuracy != null && accuracy > Math.max(3000.0, allowedRadius * 10.0)) {
            throw AppException.badRequest("Your location accuracy (" + Math.round(accuracy) + "m) is too low. Please move to an open area with better GPS signal and try again.");
        }

        double distanceMeters = calculateHaversineDistanceMeters(lat, lng, schoolLat, schoolLng);

        if (!Double.isFinite(distanceMeters)) {
            throw AppException.badRequest("Unable to calculate distance from school. Please ensure your device has a valid GPS fix and try again.");
        }

        if (distanceMeters > allowedRadius) {
            log.warn("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"OUTSIDE_GEOFENCE\", \"distance\":{}, \"allowedRadius\":{}}",
                    studentId, Math.round(distanceMeters), allowedRadius);
            throw AppException.forbidden(
                    "You are outside the allowed attendance location (" +
                    Math.round(distanceMeters) + "m away, max allowed: " + (int) allowedRadius + "m)."
            );
        }

        log.info("Structured Location Audit: {\"studentId\":\"{}\", \"locationStatus\":\"INSIDE_GEOFENCE\", \"distance\":{}, \"allowedRadius\":{}}",
                studentId, Math.round(distanceMeters), allowedRadius);
        return true;
    }

    private static final int EARTH_RADIUS_METERS = 6371000;

    private double calculateHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        if (!Double.isFinite(lat1) || !Double.isFinite(lon1) || !Double.isFinite(lat2) || !Double.isFinite(lon2)) {
            return Double.NaN;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        a = Math.max(0.0, Math.min(1.0, a));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS_METERS * c;
        return Double.isFinite(distance) ? distance : Double.NaN;
    }

    private boolean isIpInSchoolRange(String clientIp, String ipRange) {
        if (clientIp == null || clientIp.isBlank() || ipRange == null || ipRange.isBlank()) return false;
        try {
            String cleanIp = clientIp.trim();
            String range = ipRange.trim();

            String subnetStr;
            int prefixLength;

            if (range.contains("/")) {
                String[] parts = range.split("/", 2);
                subnetStr = parts[0].trim();
                prefixLength = Integer.parseInt(parts[1].trim());
            } else {
                subnetStr = range;
                prefixLength = 32;
            }

            java.net.InetAddress ipAddr = java.net.InetAddress.getByName(cleanIp);
            java.net.InetAddress subnetAddr = java.net.InetAddress.getByName(subnetStr);

            byte[] ipBytes = ipAddr.getAddress();
            byte[] subnetBytes = subnetAddr.getAddress();

            if (ipBytes.length != subnetBytes.length) return false;
            if (prefixLength < 0 || prefixLength > ipBytes.length * 8) return false;

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != subnetBytes[i]) return false;
            }

            if (remainingBits > 0 && fullBytes < ipBytes.length) {
                int mask = 0xFF << (8 - remainingBits) & 0xFF;
                if ((ipBytes[fullBytes] & mask) != (subnetBytes[fullBytes] & mask)) return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to evaluate IP range match for ip={}, range={}: {}", clientIp, ipRange, e.getMessage());
            return false;
        }
    }
}
