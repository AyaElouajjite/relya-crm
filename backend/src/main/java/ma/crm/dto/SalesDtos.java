package ma.crm.dto;

import jakarta.validation.constraints.*;
import ma.crm.domain.Enums.*;
import ma.crm.domain.Lead;
import ma.crm.domain.Opportunity;
import ma.crm.dto.CommonDtos.Ref;
import ma.crm.dto.UserDtos.UserSummary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** DTO des modules Leads (3) et Pipeline des opportunités (4). */
public final class SalesDtos {
    private SalesDtos() {
    }

    // ---- Leads ----

    public record LeadDto(Long id, String firstName, String lastName, String fullName, String email, String phone,
                          String companyName, String position, String sector, String city, LeadSource source,
                          LeadStatus status, Integer score, Instant scoredAt, BigDecimal estimatedBudget, String notes,
                          boolean marketingConsent, UserSummary assignedTo, Ref convertedCompany,
                          Ref convertedOpportunity, Instant convertedAt, Instant createdAt) {
        public static LeadDto of(Lead l) {
            return new LeadDto(l.getId(), l.getFirstName(), l.getLastName(), l.getFullName(), l.getEmail(), l.getPhone(),
                    l.getCompanyName(), l.getPosition(), l.getSector(), l.getCity(), l.getSource(), l.getStatus(),
                    l.getScore(), l.getScoredAt(), l.getEstimatedBudget(), l.getNotes(), l.isMarketingConsent(),
                    UserSummary.of(l.getAssignedTo()),
                    l.getConvertedCompany() == null ? null : Ref.of(l.getConvertedCompany().getId(), l.getConvertedCompany().getName()),
                    l.getConvertedOpportunity() == null ? null : Ref.of(l.getConvertedOpportunity().getId(), l.getConvertedOpportunity().getTitle()),
                    l.getConvertedAt(), l.getCreatedAt());
        }
    }

    public record LeadRequest(@NotBlank @Size(max = 80) String firstName,
                              @NotBlank @Size(max = 80) String lastName,
                              @Email @Size(max = 150) String email,
                              @Size(max = 30) String phone,
                              @Size(max = 150) String companyName,
                              @Size(max = 100) String position,
                              @Size(max = 80) String sector,
                              @Size(max = 80) String city,
                              LeadSource source,
                              LeadStatus status,
                              @PositiveOrZero BigDecimal estimatedBudget,
                              @Size(max = 2000) String notes,
                              boolean marketingConsent,
                              Long assignedToId) {
    }

    /**
     * Conversion : rattache le lead à une entreprise existante ou en crée une, crée le contact,
     * et éventuellement une opportunité.
     */
    public record ConvertLeadRequest(Long existingCompanyId,
                                     boolean createOpportunity,
                                     @Size(max = 150) String opportunityTitle,
                                     @PositiveOrZero BigDecimal amount,
                                     LocalDate expectedCloseDate) {
    }

    public record ConvertLeadResult(Long companyId, Long contactId, Long opportunityId) {
    }

    /** Formulaire web public. {@code website} est un piège à robots : il doit rester vide. */
    public record PublicLeadRequest(@NotBlank @Size(max = 80) String firstName,
                                    @NotBlank @Size(max = 80) String lastName,
                                    @NotBlank @Email @Size(max = 150) String email,
                                    @Size(max = 30) String phone,
                                    @Size(max = 150) String companyName,
                                    @Size(max = 2000) String message,
                                    @AssertTrue(message = "Vous devez accepter le traitement de vos données") boolean dataProcessingConsent,
                                    boolean marketingConsent,
                                    String website) {
    }

    // ---- Opportunités ----

    public record OpportunityDto(Long id, String title, BigDecimal amount, int probability, BigDecimal weightedAmount,
                                 OpportunityStage stage, LocalDate expectedCloseDate, Instant closedAt,
                                 LossReason lossReason, String lossComment, Ref company, Ref contact,
                                 UserSummary owner, String description, Instant lastActivityAt, Instant createdAt) {
        public static OpportunityDto of(Opportunity o) {
            return new OpportunityDto(o.getId(), o.getTitle(), o.getAmount(), o.getProbability(), weighted(o),
                    o.getStage(), o.getExpectedCloseDate(), o.getClosedAt(), o.getLossReason(), o.getLossComment(),
                    Ref.of(o.getCompany().getId(), o.getCompany().getName()),
                    o.getContact() == null ? null : Ref.of(o.getContact().getId(), o.getContact().getFullName()),
                    UserSummary.of(o.getOwner()), o.getDescription(), o.getLastActivityAt(), o.getCreatedAt());
        }

        static BigDecimal weighted(Opportunity o) {
            return o.getAmount().multiply(BigDecimal.valueOf(o.getProbability()))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    }

    public record OpportunityRequest(@NotBlank @Size(max = 150) String title,
                                     @NotNull @PositiveOrZero BigDecimal amount,
                                     @Min(0) @Max(100) Integer probability,
                                     OpportunityStage stage,
                                     LocalDate expectedCloseDate,
                                     @NotNull Long companyId,
                                     Long contactId,
                                     Long ownerId,
                                     @Size(max = 2000) String description,
                                     LossReason lossReason,
                                     @Size(max = 500) String lossComment) {
    }

    /** Déplacement dans le Kanban. Le motif est obligatoire pour passer en « Perdue ». */
    public record StageChangeRequest(@NotNull OpportunityStage stage,
                                     LossReason lossReason,
                                     @Size(max = 500) String lossComment) {
    }

    public record PipelineColumn(OpportunityStage stage, long count, BigDecimal total, BigDecimal weighted,
                                 List<OpportunityDto> items) {
    }
}
