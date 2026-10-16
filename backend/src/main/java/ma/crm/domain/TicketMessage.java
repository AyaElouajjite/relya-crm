package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ticket_messages")
@Getter
@Setter
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Ticket ticket;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User author;

    @Column(nullable = false, length = 4000)
    private String content;

    /** Note interne : invisible pour le client sur le portail. */
    @Column(nullable = false)
    private boolean internal;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
