package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "contacts")
@Getter
@Setter
public class Contact extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    private Company company;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "contact_tags", joinColumns = @JoinColumn(name = "contact_id"))
    @Column(name = "tag", length = 40)
    private Set<String> tags = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    @Column(nullable = false)
    private boolean archived;

    /** Loi 09-08 : consentement explicite aux communications commerciales. */
    @Column(nullable = false)
    private boolean marketingConsent;

    private Instant consentDate;

    /** Loi 09-08 : données personnelles effacées à la demande de la personne. */
    @Column(nullable = false)
    private boolean anonymized;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
