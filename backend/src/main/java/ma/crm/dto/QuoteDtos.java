package ma.crm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ma.crm.domain.Enums.InvoiceStatus;
import ma.crm.domain.Enums.ProductType;
import ma.crm.domain.Enums.QuoteStatus;
import ma.crm.domain.Invoice;
import ma.crm.domain.Product;
import ma.crm.domain.Quote;
import ma.crm.domain.QuoteLine;
import ma.crm.dto.CommonDtos.Ref;
import ma.crm.dto.UserDtos.UserSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** DTO du module 5 : catalogue, devis et factures. */
public final class QuoteDtos {
    private QuoteDtos() {
    }

    public record ProductDto(Long id, String name, String reference, String description, ProductType type,
                             BigDecimal priceHT, BigDecimal vatRate, String unit, boolean active) {
        public static ProductDto of(Product p) {
            return new ProductDto(p.getId(), p.getName(), p.getReference(), p.getDescription(), p.getType(),
                    p.getPriceHT(), p.getVatRate(), p.getUnit(), p.isActive());
        }
    }

    public record ProductRequest(@NotBlank @Size(max = 150) String name,
                                 @Size(max = 40) String reference,
                                 @Size(max = 1000) String description,
                                 ProductType type,
                                 @NotNull @PositiveOrZero BigDecimal priceHT,
                                 @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
                                 @Size(max = 20) String unit,
                                 Boolean active) {
    }

    public record QuoteLineDto(Long id, Long productId, String description, BigDecimal quantity, BigDecimal unitPrice,
                               BigDecimal discount, BigDecimal vatRate, BigDecimal totalHT) {
        public static QuoteLineDto of(QuoteLine l) {
            return new QuoteLineDto(l.getId(), l.getProduct() == null ? null : l.getProduct().getId(), l.getDescription(),
                    l.getQuantity(), l.getUnitPrice(), l.getDiscount(), l.getVatRate(), l.getTotalHT());
        }
    }

    public record QuoteLineRequest(Long productId,
                                   @NotBlank @Size(max = 300) String description,
                                   @NotNull @Positive BigDecimal quantity,
                                   @NotNull @PositiveOrZero BigDecimal unitPrice,
                                   @DecimalMin("0") @DecimalMax("100") BigDecimal discount,
                                   @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate) {
    }

    public record QuoteDto(Long id, String number, LocalDate date, LocalDate validUntil, QuoteStatus status,
                           Ref opportunity, Ref company, Ref contact, String contactEmail, String contactPhone,
                           UserSummary owner, List<QuoteLineDto> lines, BigDecimal totalHT, BigDecimal totalVAT,
                           BigDecimal totalTTC, String notes, UserSummary approvedBy, Instant approvedAt,
                           Instant sentAt, Instant respondedAt, boolean needsApproval, String publicToken,
                           Ref invoice, Instant createdAt) {
        public static QuoteDto of(Quote q, boolean needsApproval, Invoice invoice) {
            return new QuoteDto(q.getId(), q.getNumber(), q.getDate(), q.getValidUntil(), q.getStatus(),
                    Ref.of(q.getOpportunity().getId(), q.getOpportunity().getTitle()),
                    Ref.of(q.getCompany().getId(), q.getCompany().getName()),
                    q.getContact() == null ? null : Ref.of(q.getContact().getId(), q.getContact().getFullName()),
                    q.getContact() == null ? null : q.getContact().getEmail(),
                    q.getContact() == null ? null : q.getContact().getPhone(),
                    UserSummary.of(q.getOwner()), q.getLines().stream().map(QuoteLineDto::of).toList(),
                    q.getTotalHT(), q.getTotalVAT(), q.getTotalTTC(), q.getNotes(), UserSummary.of(q.getApprovedBy()),
                    q.getApprovedAt(), q.getSentAt(), q.getRespondedAt(), needsApproval, q.getPublicToken(),
                    invoice == null ? null : Ref.of(invoice.getId(), invoice.getNumber()), q.getCreatedAt());
        }
    }

    /** Version allégée pour les listes. */
    public record QuoteSummary(Long id, String number, LocalDate date, QuoteStatus status, Ref company,
                               Ref opportunity, UserSummary owner, BigDecimal totalTTC) {
        public static QuoteSummary of(Quote q) {
            return new QuoteSummary(q.getId(), q.getNumber(), q.getDate(), q.getStatus(),
                    Ref.of(q.getCompany().getId(), q.getCompany().getName()),
                    Ref.of(q.getOpportunity().getId(), q.getOpportunity().getTitle()),
                    UserSummary.of(q.getOwner()), q.getTotalTTC());
        }
    }

    public record QuoteRequest(@NotNull Long opportunityId,
                               Long contactId,
                               LocalDate date,
                               LocalDate validUntil,
                               @Size(max = 2000) String notes,
                               @NotEmpty @Valid List<QuoteLineRequest> lines) {
    }

    public record SendQuoteRequest(@Email String to, @Size(max = 2000) String message) {
    }

    public record DecisionRequest(@Size(max = 500) String comment) {
    }

    public record InvoiceDto(Long id, String number, Ref quote, Ref company, LocalDate issueDate, LocalDate dueDate,
                             LocalDate paidDate, InvoiceStatus status, BigDecimal totalHT, BigDecimal totalVAT,
                             BigDecimal totalTTC, boolean overdue) {
        public static InvoiceDto of(Invoice i) {
            boolean overdue = i.getStatus() == InvoiceStatus.EMISE && i.getDueDate() != null
                    && i.getDueDate().isBefore(LocalDate.now());
            return new InvoiceDto(i.getId(), i.getNumber(), Ref.of(i.getQuote().getId(), i.getQuote().getNumber()),
                    Ref.of(i.getCompany().getId(), i.getCompany().getName()), i.getIssueDate(), i.getDueDate(),
                    i.getPaidDate(), i.getStatus(), i.getTotalHT(), i.getTotalVAT(), i.getTotalTTC(), overdue);
        }
    }
}
