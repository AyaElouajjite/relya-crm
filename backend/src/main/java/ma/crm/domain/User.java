package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Role role;

    private Instant lastLoginAt;

    /**
     * Incrémentée à la déconnexion et au changement de mot de passe : tous les jetons JWT
     * émis avant deviennent invalides (révocation côté serveur).
     */
    @Column(nullable = false)
    private int tokenVersion;

    /** Pour un compte du portail client : le contact (et donc l'entreprise) qu'il représente. */
    @OneToOne(fetch = FetchType.LAZY)
    private Contact contact;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
