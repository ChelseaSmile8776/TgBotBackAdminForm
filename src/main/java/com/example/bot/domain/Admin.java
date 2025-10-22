package com.example.bot.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "admins")
public class Admin {

    public Admin() {}
    public Admin(Long chatId) {
        this.chatId = chatId;
        this.role = "ADMIN";
    }
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long chatId;

    @Column(length = 64)
    private String username;

    @Column(nullable = false)
    private String role = "ADMIN"; // или "SUPERADMIN"

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}