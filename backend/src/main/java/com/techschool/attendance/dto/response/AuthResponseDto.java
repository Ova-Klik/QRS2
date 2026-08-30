package com.techschool.attendance.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthResponseDto {

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class LoginResponse {
        private String token;
        private String userId;
        private String name;
        private String email;
        private String role;
        private String cohortId;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class ChallengeResponse {
        private String challenge;
        private String rpName;
        private String rpId;
    }
}
