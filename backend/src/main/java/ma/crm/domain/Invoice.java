package ma.crm.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import ma.crm.domain.Enums.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Facture émise à partir d'un devis accepté. La comptabilité reste hors périmètre :
 * cette entité sert au suivi commercial, au portail client et au chiffre d'affaires réalisé.
 */
@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice extends BaseEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    private Quote quote;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Company company;

    @Column(nullable = false)
    private LocalDate issueDate;

    private LocalDate dueDate;

    private LocalDate paidDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceStatus status = InvoiceStatus.EMISE;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalHT;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalVAT;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal totalTTC;
}
