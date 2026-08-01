package com.techschool.attendance.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "system_settings")
public class SystemSetting {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;
    private String value;
    private String description;
}
