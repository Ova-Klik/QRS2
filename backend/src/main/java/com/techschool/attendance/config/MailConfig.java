package com.techschool.attendance.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfig {
    // Uses Spring Boot Mail Auto-Configuration using properties defined in application.properties:
    // - spring.mail.host
    // - spring.mail.port
    // - spring.mail.username
    // - spring.mail.password
    // - spring.mail.properties.mail.smtp.*
}
