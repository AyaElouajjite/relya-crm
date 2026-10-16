package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "quote_lines")
@Getter
@Setter
public class QuoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Quote quote;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    /** Remise en pourcentage. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    public BigDecimal getTotalHT() {
        BigDecimal gross = quantity.multiply(unitPrice);
        BigDecimal factor = BigDecimal.ONE.subtract(discount.movePointLeft(2));
        return gross.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalVAT() {
        return getTotalHT().multiply(vatRate.movePointLeft(2)).setScale(2, RoundingMode.HALF_UP);
    }
}
