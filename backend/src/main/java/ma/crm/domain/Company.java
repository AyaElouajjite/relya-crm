package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/** Entreprise cliente ou prospect : pivot du modèle de données. */
@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 80)
    private String sector;

    /** Identifiant Commun de l'Entreprise (Maroc, 15 chiffres). */
    @Column(length = 15, unique = true)
    private String ice;

    @Column(length = 80)
    private String city;

    @Column(length = 255)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 150)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status = CompanyStatus.PROSPECT;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "company_tags", joinColumns = @JoinColumn(name = "company_id"))
    @Column(name = "tag", length = 40)
    private Set<String> tags = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    @Column(nullable = false)
    private boolean archived;
}
