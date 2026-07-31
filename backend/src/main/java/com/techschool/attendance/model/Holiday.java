package com.techschool.attendance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@Document(collection = "holidays")
@CompoundIndexes({
    @CompoundIndex(name = "holiday_active_range", def = "{'active': 1, 'startDate': 1, 'endDate': 1}"),
    @CompoundIndex(name = "holiday_range", def = "{'startDate': 1, 'endDate': 1}")
})
public class Holiday {

    @Id
    private String id;

    @Indexed
    private String name;

    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

    /** true = applies to all cohorts, false = applies to the cohort in {@link #cohortId} */
    private boolean appliesToAll = true;

    @Indexed
    private String cohortId;

    private boolean active = true;

    private String createdById;
    private String createdByName;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public boolean isInRange(LocalDate date) {
        return startDate != null && endDate != null
                && !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
