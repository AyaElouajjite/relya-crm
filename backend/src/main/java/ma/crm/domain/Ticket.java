package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.crm.domain.Enums.TicketChannel;
import ma.crm.domain.Enums.TicketPriority;
import ma.crm.domain.Enums.TicketStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Ticket SAV : dateOuverture = openedAt, dateRésolution = resolvedAt. */
@Entity
@Table(name = "tickets")
@Getter
@Setter
public class Ticket extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketPriority priority = TicketPriority.NORMALE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status = TicketStatus.OUVERT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketChannel channel = TicketChannel.AGENT;

    @Column(length = 60)
    private String category;

    /** Contact qui a ouvert le ticket. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Contact contact;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Company company;

    /** Agent support en charge. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User assignee;

    @Column(nullable = false)
    private Instant openedAt;

    /** Échéance calculée à partir du SLA de la priorité. */
    @Column(nullable = false)
    private Instant dueAt;

    private Instant firstResponseAt;

    private Instant resolvedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt")
    private List<TicketMessage> messages = new ArrayList<>();
}
