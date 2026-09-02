package org.leap.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

import org.leap.domain.enums.RiskProfile;

public class Client {

    private final Long clientId;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final LocalDate dateOfBirth;
    private RiskProfile riskProfile;
    private final Instant createdAt;
    private Instant updatedAt;

    public Client(
            Long clientId,
            String firstName,
            String lastName,
            String email,
            String phone,
            LocalDate dateOfBirth,
            RiskProfile riskProfile,
            Instant createdAt,
            Instant updatedAt) {

        this.clientId = Objects.requireNonNull(clientId);
        this.firstName = Objects.requireNonNull(firstName);
        this.lastName = Objects.requireNonNull(lastName);
        this.email = Objects.requireNonNull(email);
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.riskProfile = Objects.requireNonNull(riskProfile);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;

        validate();
    }

    private void validate() {
        if (firstName.isBlank()) {
            throw new IllegalArgumentException("First name cannot be blank");
        }

        if (lastName.isBlank()) {
            throw new IllegalArgumentException("Last name cannot be blank");
        }

        if (email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }

        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date of birth cannot be in the future");
        }
    }

    public Long getClientId() {
        return clientId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public RiskProfile getRiskProfile() {
        return riskProfile;
    }

    public void changeRiskProfile(RiskProfile riskProfile) {
        this.riskProfile = Objects.requireNonNull(riskProfile);
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}