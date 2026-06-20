package com.alradno.banking.customer;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private UUID id;
    private String subject;
    private String fullName;
    @Enumerated(EnumType.STRING)
    private CustomerStatus status;
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    protected Customer() {
    }

    public Customer(UUID id, String subject, String fullName, CustomerStatus status, RiskLevel riskLevel) {
        this.id = id;
        this.subject = subject;
        this.fullName = fullName;
        this.status = status;
        this.riskLevel = riskLevel;
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getFullName() {
        return fullName;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }
}
