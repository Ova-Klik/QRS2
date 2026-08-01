package com.techschool.attendance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Document(collection = "attendance")
@CompoundIndexes({
    @CompoundIndex(name = "student_date_unique", def = "{'studentId': 1, 'date': 1}", unique = true),
    @CompoundIndex(name = "cohort_date", def = "{'cohortId': 1, 'date': 1}"),
    @CompoundIndex(name = "date_status", def = "{'date': 1, 'status': 1}"),
    @CompoundIndex(name = "student_date_range", def = "{'studentId': 1, 'date': 1, 'status': 1}")
})
public class Attendance {

    @Id
    private String id;

    private String studentId;
    private String cohortId;
    private String sessionId;      // QrSession id (null for manual)

    private LocalDate date;
    private Instant markedAt;

    @Indexed
    private AttendanceStatus status;

    private boolean manual = false;
    private String manualReason;
    private String markedById;     // facilitator or admin id for manual

    private String deviceId;       // device used for QR scan
    private String ipAddress;      // client IP at time of scan

    @CreatedDate
    private Instant createdAt;

    public enum AttendanceStatus {
        PRESENT, LATE, ABSENT, EXCUSED, HOLIDAY
    }
}
