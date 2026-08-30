package com.techschool.attendance.controller;

import com.techschool.attendance.dto.request.NetworkSettingsRequestDto;
import com.techschool.attendance.dto.response.NetworkSettingsResponseDto;
import com.techschool.attendance.service.NetworkSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/network-settings")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class NetworkSettingsController {

    private final NetworkSettingsService networkSettingsService;

    @GetMapping
    public ResponseEntity<NetworkSettingsResponseDto.SettingsResponse> getSettings() {
        return ResponseEntity.ok(networkSettingsService.getSettings());
    }

    @PutMapping
    public ResponseEntity<NetworkSettingsResponseDto.SettingsResponse> updateSettings(
            @AuthenticationPrincipal String adminId,
            @RequestBody NetworkSettingsRequestDto.UpdateSettingsRequest request) {
        return ResponseEntity.ok(networkSettingsService.updateSettings(request, adminId));
    }

    @PostMapping("/ssids")
    public ResponseEntity<NetworkSettingsResponseDto.SettingsResponse> addSsid(
            @AuthenticationPrincipal String adminId,
            @Valid @RequestBody NetworkSettingsRequestDto.AddSsidRequest request) {
        return ResponseEntity.ok(networkSettingsService.addSsid(request.getSsid(), adminId));
    }

    @DeleteMapping("/ssids/{ssid}")
    public ResponseEntity<NetworkSettingsResponseDto.SettingsResponse> removeSsid(
            @AuthenticationPrincipal String adminId,
            @PathVariable String ssid) {
        return ResponseEntity.ok(networkSettingsService.removeSsid(ssid, adminId));
    }
}
