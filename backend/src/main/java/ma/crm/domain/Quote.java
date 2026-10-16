package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.crm.domain.Enums.QuoteStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Devis (« commande » une fois accepté). */
@Entity
@Table(name = "quotes")
@Getter
@Setter
public class Quote extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @Column(nullable = false)
    private LocalDate date;

    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuoteStatus status = QuoteStatus.BROUILLON;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Opportunity opportunity;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Company company;

    /** Destinataire du devis. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<QuoteLine> lines = new ArrayList<>();

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalHT = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalVAT = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalTTC = BigDecimal.ZERO;

    @Column(length = 2000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    private User approvedBy;

    private Instant approvedAt;

    private Instant sentAt;

    private Instant respondedAt;

    /** Jeton de partage public (lien WhatsApp / e-mail) sans authentification. */
    @Column(unique = true, length = 64)
    private String publicToken;
}
