package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.crm.domain.Enums.LossReason;
import ma.crm.domain.Enums.OpportunityStage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "opportunities")
@Getter
@Setter
public class Opportunity extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(nullable = false)
    private int probability = OpportunityStage.PROSPECTION.defaultProbability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OpportunityStage stage = OpportunityStage.PROSPECTION;

    /** Date de clôture prévue. */
    private LocalDate expectedCloseDate;

    /** Date effective de passage en Gagnée / Perdue. */
    private Instant closedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private LossReason lossReason;

    @Column(length = 500)
    private String lossComment;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    private Contact contact;

    /** Commercial responsable. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    @Column(length = 2000)
    private String description;

    /** Mise à jour à chaque activité : sert aux relances automatiques. */
    private Instant lastActivityAt;
}
