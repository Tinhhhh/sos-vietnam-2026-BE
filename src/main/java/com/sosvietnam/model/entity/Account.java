package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sosvietnam.model.payload.enums.AgencyType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_account_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id")
    private UUID id;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    @Email
    private String email;

    @JsonIgnore
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "phone", length = 10)
    @Pattern(regexp = "^0[0-9]{9}$", message = "Phone number must be 10 digits starting with 0.")
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Builder.Default
    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder.Default
    @Column(name = "is_dispatcher", nullable = false, columnDefinition = "boolean default false")
    private boolean isDispatcher = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "agency_type", length = 30)
    private AgencyType agencyType;

    @Column(name = "province", length = 150)
    private String province;

    @Column(name = "ward", length = 150)
    private String ward;

    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    public void prePersist() {
        if (this.createdDate == null)
            this.createdDate = LocalDateTime.now();
    }

    @JsonIgnore
    public String fullName() {
        if (firstName == null && lastName == null)
            return email;
        return ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();
    }

    public String getFullName() {
        return fullName();
    }
}