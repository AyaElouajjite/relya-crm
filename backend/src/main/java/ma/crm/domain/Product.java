package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.crm.domain.Enums.ProductType;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
public class Product extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(unique = true, length = 40)
    private String reference;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductType type = ProductType.PRODUIT;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal priceHT;

    /** Taux de TVA en pourcentage (20 par défaut au Maroc). */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate = new BigDecimal("20");

    @Column(length = 20)
    private String unit;

    @Column(nullable = false)
    private boolean active = true;
}
