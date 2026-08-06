package com.techschool.attendance.controller;

import com.google.zxing.WriterException;
import com.techschool.attendance.dto.AttendanceDto;
import com.techschool.attendance.dto.CohortDto;
import com.techschool.attendance.dto.QrDto;
import com.techschool.attendance.service.AttendanceService;
import com.techschool.attendance.service.CohortService;
import com.techschool.attendance.service.QrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final CohortService cohortService;
    private final QrService qrService;
    private final AttendanceService attendanceService;
    private final com.techschool.attendance.service.UserService userService;
    private final com.techschool.attendance.service.ExportService exportService;

    @GetMapping("/cohorts")
    public ResponseEntity<List<CohortDto.CohortResponse>> getActiveCohorts() {
        return ResponseEntity.ok(cohortService.getActiveCohorts());
    }

    @GetMapping("/settings")
    public ResponseEntity<java.util.Map<String, String>> getPublicSettings() {
        return ResponseEntity.ok(userService.getNetworkSettings());
    }

    @GetMapping("/qr-session/{cohortId}")
    public ResponseEntity<QrDto.QrResponse> getPublicQrSession(
            @PathVariable String cohortId,
            @RequestParam(required = false) String origin) throws WriterException, IOException {
        return ResponseEntity.ok(qrService.getOrGeneratePublicSession(cohortId, origin));
    }

    @GetMapping("/today-summary/{cohortId}")
    public ResponseEntity<AttendanceDto.DailySummary> getTodaySummary(@PathVariable String cohortId) {
        return ResponseEntity.ok(attendanceService.getCohortSummaryToday(cohortId));
    }

    @GetMapping("/projection/export/{cohortId}")
    public ResponseEntity<byte[]> exportPublicProjectionReport(
            @PathVariable String cohortId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(defaultValue = "xlsx") String format,
            jakarta.servlet.http.HttpServletRequest request) {
        return attendanceService.exportPublicProjectionReport(cohortId, date, format, request.getRemoteAddr(), exportService);
    }
}
