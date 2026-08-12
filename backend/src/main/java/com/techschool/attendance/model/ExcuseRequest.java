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
@Document(collection = "excuse_requests")
@CompoundIndexes({
    @CompoundIndex(name = "student_status", def = "{'studentId': 1, 'status': 1}"),
    @CompoundIndex(name = "cohort_status", def = "{'cohortId': 1, 'status': 1}")
})
public class ExcuseRequest {

    @Id
    private String id;

    @Indexed
    private String studentId;
    private String studentName;

    @Indexed
    private String cohortId;

    private String reason;
    private int numberOfDays = 1;
    private LocalDate startDate;
    private LocalDate endDate;
    private String coverUpPlan;

    private Status status = Status.PENDING; // PENDING, APPROVED, REJECTED

    private String reviewedById;
    private String reviewedByName;
    private String reviewerNotes;
    private Instant reviewedAt;

    @CreatedDate
    private Instant createdAt;

    public enum Status {
        PENDING, ACCEPTED, REJECTED, APPROVED
    }
}
