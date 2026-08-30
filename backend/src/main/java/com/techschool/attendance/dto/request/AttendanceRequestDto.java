package com.techschool.attendance.dto.request;

import com.techschool.attendance.data.model.Attendance;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AttendanceRequestDto {

    @Data
    public static class ManualMarkRequest {
        @NotBlank
        private String studentId;
        @NotNull
        private Attendance.AttendanceStatus status;
        @NotBlank @Size(min = 3, max = 500)
        private String reason;
    }
}
