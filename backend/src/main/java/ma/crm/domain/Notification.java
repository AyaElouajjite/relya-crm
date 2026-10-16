package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = @Index(columnList = "recipient_id, readAt"))
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User recipient;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String message;

    /** Lien interne de l'application (ex. /tickets/12). */
    @Column(length = 200)
    private String link;

    private Instant readAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
