package com.top50.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "artists", uniqueConstraints = {
    @UniqueConstraint(columnNames = "normalized_name")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Artist {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 255, unique = true)
    private String normalizedName;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
