package com.techschool.attendance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

@Data
@NoArgsConstructor
@Document(collection = "devices")
public class Device {

    @Id
    private String id;

    @Indexed
    private String studentId;

    @Indexed
    private String fingerprint;   // Browser fingerprint hash
    private String userAgent;
    @Indexed
    private String imei;          // Optional hardware ID

    @Indexed
    private boolean locked = true; // true = registered and in use

    @CreatedDate
    private Instant registeredAt;

    @LastModifiedDate
    private Instant updatedAt;

    private String registeredBy;   // admin userId who registered/unlocked
}
