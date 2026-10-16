package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    /** Codes techniques des rôles prévus par le cahier des charges. */
    public static final String ADMIN = "ADMIN";
    public static final String DIRECTEUR = "DIRECTEUR";
    public static final String COMMERCIAL = "COMMERCIAL";
    public static final String SUPPORT = "SUPPORT";
    public static final String CLIENT = "CLIENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 80)
    private String label;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 40)
    private Set<Permission> permissions = new HashSet<>();

    public Role(String code, String label, Set<Permission> permissions) {
        this.code = code;
        this.label = label;
        this.permissions = new HashSet<>(permissions);
    }
}
