package com.mathbot.api.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "grades")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Grade {

    @Id
    private Short id;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "description")
    private String description;
}
