package com.example.bot.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(indexes = {
        @Index(columnList = "createdAt"),
        @Index(columnList = "formType")
})
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private FormType formType;

    // общие поля
    private String name;
    private String phone;

    // для формы B
    private String questionText;

    // для формы C
    private String productSku;
    private String productUrl;
    private BigDecimal amount;
    private Long orderIdSql;

    // кому назначено (chatId)
    private Long assignedToChatId;

    private OffsetDateTime createdAt = OffsetDateTime.now();

    // ===== GETTERS / SETTERS =====

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FormType getFormType() {
        return formType;
    }

    public void setFormType(FormType formType) {
        this.formType = formType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductUrl() {
        return productUrl;
    }

    public void setProductUrl(String productUrl) {
        this.productUrl = productUrl;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Long getOrderIdSql() {
        return orderIdSql;
    }

    public void setOrderIdSql(Long orderIdSql) {
        this.orderIdSql = orderIdSql;
    }

    public Long getAssignedToChatId() {
        return assignedToChatId;
    }

    public void setAssignedToChatId(Long assignedToChatId) {
        this.assignedToChatId = assignedToChatId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Submission{" +
                "id=" + id +
                ", formType=" + formType +
                ", name='" + name + '\'' +
                ", phone='" + phone + '\'' +
                ", questionText='" + questionText + '\'' +
                ", productSku='" + productSku + '\'' +
                ", productUrl='" + productUrl + '\'' +
                ", amount=" + amount +
                ", orderIdSql=" + orderIdSql +
                ", assignedToChatId=" + assignedToChatId +
                ", createdAt=" + createdAt +
                '}';
    }
}