package com.techschool.attendance.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed
    private String name;

    @Indexed(unique = true)
    private String email;

    private String phone;

    private String passwordHash;

    private Role role;

    // Student fields
    @Indexed
    private String cohortId;
    private String deviceId;

    @Indexed
    private String registrationNumber; // optional student registration number

    // Facilitator fields
    private List<String> assignedCohortIds;

    // WebAuthn biometric
    private String webAuthnCredentialId;
    private String webAuthnPublicKey;

    private boolean active = true;
    private boolean passwordResetRequired = false;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public enum Role {
        STUDENT, FACILITATOR, SUPER_ADMIN
    }
}
