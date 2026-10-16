package com.example.ems.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String fullName;
    @Column(nullable = false, unique = true, length = 190)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false)
    private String role = "USER";
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

}
