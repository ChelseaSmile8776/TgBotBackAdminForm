package com.example.bot.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public class FormPayload {
    @NotBlank public String type; // "A", "B", "C"
    @NotBlank public String name;
    @NotBlank public String phone;
    public String question;       // для "B"
    public Long orderIdSql;       // для "C"
    public String sku;            // для "C"
    public String productUrl;     // для "C"
    public BigDecimal amount;     // для "C"
}