package com.techschool.attendance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Document(collection = "qr_sessions")
public class QrSession {

    @Id
    private String id;

    private String cohortId;
    private String facilitatorId;

    @Indexed(unique = true)
    private String token;           // Encrypted one-time token

    private String encryptedPayload; // Base64 JWT-signed payload
    private LocalDate date;

    private Instant activeFrom;
    private Instant expiresAt;

    private SessionState state = SessionState.CREATED;

    private int scanCount = 0;

    @CreatedDate
    private Instant createdAt;

    public enum SessionState {
        CREATED, ACTIVE, EXPIRED, ARCHIVED
    }

    public boolean isCurrentlyActive() {
        Instant now = Instant.now();
        return state == SessionState.ACTIVE
                && now.isAfter(activeFrom)
                && now.isBefore(expiresAt);
    }
}
