package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.crm.domain.Enums.LeadSource;
import ma.crm.domain.Enums.LeadStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** Prospect non encore qualifié (dateCréation = createdAt). */
@Entity
@Table(name = "leads")
@Getter
@Setter
public class Lead extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String companyName;

    @Column(length = 100)
    private String position;

    @Column(length = 80)
    private String sector;

    @Column(length = 80)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadSource source = LeadSource.MANUEL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status = LeadStatus.NOUVEAU;

    /** Probabilité de conversion (0-100) estimée par le service IA. */
    private Integer score;

    private Instant scoredAt;

    @Column(precision = 14, scale = 2)
    private BigDecimal estimatedBudget;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private boolean marketingConsent;

    @ManyToOne(fetch = FetchType.LAZY)
    private User assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    private Company convertedCompany;

    @ManyToOne(fetch = FetchType.LAZY)
    private Contact convertedContact;

    @ManyToOne(fetch = FetchType.LAZY)
    private Opportunity convertedOpportunity;

    private Instant convertedAt;

    @Column(nullable = false)
    private boolean anonymized;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
