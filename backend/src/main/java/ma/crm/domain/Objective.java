package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Objectif de chiffre d'affaires mensuel fixé par le directeur commercial. */
@Entity
@Table(name = "objectives", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "target_year", "target_month"}))
@Getter
@Setter
public class Objective extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @Column(name = "target_year", nullable = false)
    private int year;

    @Column(name = "target_month", nullable = false)
    private int month;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal targetAmount;
}
