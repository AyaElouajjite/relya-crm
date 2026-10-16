package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.crm.domain.Enums.ActivityStatus;
import ma.crm.domain.Enums.ActivityType;

import java.time.Instant;

/** Appel, e-mail, rendez-vous, tâche ou événement système (historique automatique). */
@Entity
@Table(name = "activities", indexes = {@Index(columnList = "dueAt"), @Index(columnList = "company_id")})
@Getter
@Setter
public class Activity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActivityType type;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 4000)
    private String description;

    /** Date prévue (rendez-vous, échéance de tâche) ou date de l'événement. */
    @Column(nullable = false)
    private Instant dueAt;

    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActivityStatus status = ActivityStatus.A_FAIRE;

    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    private Opportunity opportunity;

    @ManyToOne(fetch = FetchType.LAZY)
    private Lead lead;

    /** Utilisateur responsable. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    private Instant reminderAt;

    @Column(nullable = false)
    private boolean reminderSent;

    /** Créée par le système (historique, relance automatique). */
    @Column(nullable = false)
    private boolean automatic;
}
