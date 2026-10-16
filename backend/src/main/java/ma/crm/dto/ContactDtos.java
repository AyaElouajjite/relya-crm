package ma.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.crm.domain.Contact;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public final class ContactDtos {
    private ContactDtos() {
    }

    /** @param portalAccess vrai si le contact dispose d'un compte sur le portail client */
    public record ContactDto(Long id, String firstName, String lastName, String fullName, String email,
                             String phone, String position, Long companyId, String companyName, Set<String> tags,
                             UserDtos.UserSummary owner, boolean archived, boolean marketingConsent,
                             Instant consentDate, boolean anonymized, boolean portalAccess, Instant createdAt) {
        public static ContactDto of(Contact c) {
            return of(c, false);
        }

        public static ContactDto of(Contact c, boolean portalAccess) {
            return new ContactDto(c.getId(), c.getFirstName(), c.getLastName(), c.getFullName(), c.getEmail(),
                    c.getPhone(), c.getPosition(),
                    c.getCompany() == null ? null : c.getCompany().getId(),
                    c.getCompany() == null ? null : c.getCompany().getName(),
                    new TreeSet<>(c.getTags()), UserDtos.UserSummary.of(c.getOwner()), c.isArchived(),
                    c.isMarketingConsent(), c.getConsentDate(), c.isAnonymized(), portalAccess, c.getCreatedAt());
        }
    }

    public record ContactRequest(@NotBlank @Size(max = 80) String firstName,
                                 @NotBlank @Size(max = 80) String lastName,
                                 @Email @Size(max = 150) String email,
                                 @Size(max = 30) String phone,
                                 @Size(max = 100) String position,
                                 Long companyId,
                                 Set<@Size(max = 40) String> tags,
                                 Long ownerId,
                                 boolean marketingConsent) {
    }
}
