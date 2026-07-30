package com.techschool.attendance.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.techschool.attendance.dto.QrDto;
import com.techschool.attendance.exception.AppException;
import com.techschool.attendance.model.AuditLog;
import com.techschool.attendance.model.Cohort;
import com.techschool.attendance.model.QrSession;
import com.techschool.attendance.model.SystemSetting;
import com.techschool.attendance.repository.CohortRepository;
import com.techschool.attendance.repository.QrSessionRepository;
import com.techschool.attendance.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QrService {

    private final QrSessionRepository qrSessionRepository;
    private final CohortRepository cohortRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final AuditService auditService;

    @Value("${app.attendance.qr-window-start}")
    private String windowStartDefault;

    @Value("${app.attendance.qr-window-end}")
    private String windowEndDefault;

    @Value("${app.attendance.timezone}")
    private String timezone;

    public QrDto.QrResponse generateSession(String facilitatorId, String facilitatorName,
                                             String cohortId, Integer durationMinutes) throws WriterException, IOException {
        return generateSession(facilitatorId, facilitatorName, cohortId, durationMinutes, null);
    }

    public QrDto.QrResponse generateSession(String facilitatorId, String facilitatorName,
                                             String cohortId, Integer durationMinutes, String origin) throws WriterException, IOException {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));

        if (!cohort.isActive()) {
            throw AppException.badRequest("Cohort is not active");
        }

        LocalDate today = LocalDate.now(ZoneId.of(timezone));

        // Check for existing active session & expire it to allow generating a new session
        Optional<QrSession> existing = qrSessionRepository.findActiveSessionByCohortId(cohortId);
        if (existing.isPresent()) {
            QrSession oldSession = existing.get();
            oldSession.setState(QrSession.SessionState.EXPIRED);
            qrSessionRepository.save(oldSession);
        }

        // Build session window & custom duration
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime activeFrom = ZonedDateTime.now(zone);
        ZonedDateTime expiresAt;

        if (durationMinutes != null && durationMinutes > 0) {
            expiresAt = activeFrom.plusMinutes(durationMinutes);
        } else {
            String windowEnd = getSetting("qr_window_end", windowEndDefault);
            String[] endParts = windowEnd.split(":");
            LocalTime endTime = LocalTime.of(Integer.parseInt(endParts[0]), Integer.parseInt(endParts[1]));
            if (endTime.getHour() == 23 && endTime.getMinute() == 59) {
                endTime = LocalTime.of(23, 59, 59);
            }
            expiresAt = ZonedDateTime.of(today, endTime, zone);
        }

        // Generate master token
        String masterToken = UUID.randomUUID().toString().replace("-", "") +
                Long.toHexString(System.currentTimeMillis());

        QrSession session = new QrSession();
        session.setCohortId(cohortId);
        session.setFacilitatorId(facilitatorId);
        session.setToken(masterToken);
        session.setDate(today);
        session.setActiveFrom(activeFrom.toInstant());
        session.setExpiresAt(expiresAt.toInstant());
        session.setState(QrSession.SessionState.ACTIVE);
        QrSession saved = qrSessionRepository.save(session);

        // Generate 20-second dynamic TOTP rolling payload
        String rollingPayload = generateRollingTokenPayload(masterToken);
        String qrContent = (origin != null && !origin.isEmpty())
                ? origin + "/login?qrs=" + rollingPayload
                : "QRS:" + rollingPayload;
        String qrBase64 = generateQrImage(qrContent, 300);

        auditService.log(facilitatorId, facilitatorName, "FACILITATOR",
                AuditLog.ActionType.QR_GENERATED, saved.getId(), cohort.getName(),
                "QR session opened for " + cohort.getName() + " on " + today +
                        (durationMinutes != null ? " (" + durationMinutes + " mins duration)" : ""), null);

        long remainingSeconds = Duration.between(Instant.now(), expiresAt.toInstant()).getSeconds();

        return new QrDto.QrResponse(
                saved.getId(), cohortId, cohort.getName(),
                qrBase64, rollingPayload,
                activeFrom.toInstant(), expiresAt.toInstant(),
                QrSession.SessionState.ACTIVE, Math.max(0, remainingSeconds)
        );
    }

    public QrDto.QrResponse getActiveSession(String cohortId) throws WriterException, IOException {
        return getActiveSession(cohortId, null);
    }

    public QrDto.QrResponse getActiveSession(String cohortId, String origin) throws WriterException, IOException {
        QrSession session = qrSessionRepository.findActiveSessionByCohortId(cohortId)
                .orElseThrow(() -> AppException.notFound("No active QR session for this cohort"));

        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> AppException.notFound("Cohort not found"));

        String rollingPayload = generateRollingTokenPayload(session.getToken());
        String qrContent = (origin != null && !origin.isEmpty())
                ? origin + "/login?qrs=" + rollingPayload
                : "QRS:" + rollingPayload;
        String qrBase64 = generateQrImage(qrContent, 300);
        long remaining = Duration.between(Instant.now(), session.getExpiresAt()).getSeconds();

        return new QrDto.QrResponse(
                session.getId(), cohortId, cohort.getName(),
                qrBase64, rollingPayload,
                session.getActiveFrom(), session.getExpiresAt(),
                session.getState(), Math.max(0, remaining)
        );
    }

    public QrDto.QrResponse getOrGeneratePublicSession(String cohortId) throws WriterException, IOException {
        return getOrGeneratePublicSession(cohortId, null);
    }

    public QrDto.QrResponse getOrGeneratePublicSession(String cohortId, String origin) throws WriterException, IOException {
        Optional<QrSession> existing = qrSessionRepository.findActiveSessionByCohortId(cohortId);
        if (existing.isPresent()) {
            return getActiveSession(cohortId, origin);
        }
        return generateSession("SYSTEM", "Automated Projection System", cohortId, null, origin);
    }

    public void expireSession(String sessionId, String actorId, String actorName) {
        QrSession session = qrSessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Session not found"));
        session.setState(QrSession.SessionState.EXPIRED);
        qrSessionRepository.save(session);
        auditService.log(actorId, actorName, "FACILITATOR",
                AuditLog.ActionType.QR_EXPIRED, sessionId, null, "Session manually stopped", null);
    }

    public QrSession validateToken(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) {
            throw AppException.badRequest("Invalid QR code payload");
        }

        String cleanedToken = rawToken.trim();
        // Handle URL format: http://origin/login?qrs=TOKEN
        int qrsIdx = cleanedToken.indexOf("/login?qrs=");
        if (qrsIdx >= 0) {
            cleanedToken = cleanedToken.substring(qrsIdx + "/login?qrs=".length());
        }
        if (cleanedToken.startsWith("QRS:")) {
            cleanedToken = cleanedToken.substring(4);
        }

        String masterToken = cleanedToken;
        if (cleanedToken.contains(":")) {
            String[] parts = cleanedToken.split(":");
            if (parts.length >= 3) {
                masterToken = parts[0];
                long timeStep;
                try {
                    timeStep = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    throw AppException.badRequest("Malformed dynamic QR token");
                }

                long currentStep = System.currentTimeMillis() / 10000; // 10-second window
                if (Math.abs(currentStep - timeStep) > 1) { // Expired 10s window
                    throw AppException.badRequest("QR code has expired. Please scan the live code on screen.");
                }

                String expectedHash = computeHash(masterToken + ":" + timeStep);
                if (!expectedHash.equals(parts[2])) {
                    throw AppException.badRequest("Invalid dynamic QR code signature");
                }
            }
        }

        QrSession session = qrSessionRepository.findByToken(masterToken)
                .orElseThrow(() -> AppException.badRequest("Invalid or expired QR code"));

        if (!session.isCurrentlyActive()) {
            throw AppException.badRequest("QR session has expired or is not active");
        }
        return session;
    }

    public String generateRollingTokenPayload(String masterToken) {
        long step = System.currentTimeMillis() / 10000; // 10-second window
        String hash = computeHash(masterToken + ":" + step);
        return masterToken + ":" + step + ":" + hash;
    }

    private String computeHash(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 12);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    // ── Auto-expire sessions ─────────────────────────────
    @Scheduled(fixedDelay = 60000) // every minute
    public void autoExpireSessions() {
        List<QrSession> expired = qrSessionRepository
                .findByStateAndExpiresAtBefore(QrSession.SessionState.ACTIVE, Instant.now());
        for (QrSession s : expired) {
            s.setState(QrSession.SessionState.EXPIRED);
            qrSessionRepository.save(s);
            log.info("Auto-expired QR session {} for cohort {}", s.getId(), s.getCohortId());
        }
    }

    // ── QR image generation ──────────────────────────────
    private String generateQrImage(String content, int size) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String getSetting(String key, String defaultVal) {
        return systemSettingRepository.findByKey(key)
                .map(SystemSetting::getValue)
                .orElse(defaultVal);
    }
}
