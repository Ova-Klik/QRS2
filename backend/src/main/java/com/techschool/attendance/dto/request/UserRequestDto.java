package com.techschool.attendance.dto.request;

import com.techschool.attendance.data.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

public class UserRequestDto {

    @Data
    public static class CreateUserRequest {
        @NotBlank
        private String name;
        @NotBlank @Email
        private String email;
        @NotBlank @Size(min = 6)
        private String password;
        @NotNull
        private User.Role role;
        private String cohortId;
        private String registrationNumber;
        private List<String> assignedCohortIds;
    }

    @Data
    public static class UpdateUserRequest {
        private String name;
        private String email;
        private String phone;
        private String cohortId;
        private String registrationNumber;
        private List<String> assignedCohortIds;
        private Boolean active;
    }
}
