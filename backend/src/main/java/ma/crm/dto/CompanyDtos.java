package ma.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.crm.domain.Company;
import ma.crm.domain.CompanyStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class CompanyDtos {
    private CompanyDtos() {
    }

    public record CompanyDto(Long id, String name, String sector, String ice, String city, String address,
                             String phone, String email, String website, CompanyStatus status, Set<String> tags,
                             UserDtos.UserSummary owner, boolean archived, long contactCount,
                             Instant createdAt, Instant updatedAt) {
        public static CompanyDto of(Company c, long contactCount) {
            return new CompanyDto(c.getId(), c.getName(), c.getSector(), c.getIce(), c.getCity(), c.getAddress(),
                    c.getPhone(), c.getEmail(), c.getWebsite(), c.getStatus(), new TreeSet<>(c.getTags()),
                    UserDtos.UserSummary.of(c.getOwner()), c.isArchived(), contactCount,
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record CompanyRequest(@NotBlank @Size(max = 150) String name,
                                 @Size(max = 80) String sector,
                                 @Pattern(regexp = "^$|^\\d{15}$", message = "L'ICE doit contenir 15 chiffres") String ice,
                                 @Size(max = 80) String city,
                                 @Size(max = 255) String address,
                                 @Size(max = 30) String phone,
                                 @Email @Size(max = 150) String email,
                                 @Size(max = 150) String website,
                                 CompanyStatus status,
                                 Set<@Size(max = 40) String> tags,
                                 Long ownerId) {
    }

    /** Fiche client à 360° : coordonnées, contacts, opportunités, devis, factures, tickets et historique. */
    public record Company360(CompanyDto company, List<ContactDtos.ContactDto> contacts,
                             List<SalesDtos.OpportunityDto> opportunities, List<QuoteDtos.QuoteSummary> quotes,
                             List<QuoteDtos.InvoiceDto> invoices, List<TicketDtos.TicketDto> tickets,
                             List<ActivityDtos.ActivityDto> history, java.math.BigDecimal revenue,
                             java.math.BigDecimal openPipeline) {
    }

    public record FilterOptions(List<String> sectors, List<String> cities) {
    }
}
