package ma.crm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.crm.domain.Activity;
import ma.crm.domain.Enums.ActivityStatus;
import ma.crm.domain.Enums.ActivityType;
import ma.crm.dto.CommonDtos.Ref;
import ma.crm.dto.UserDtos.UserSummary;

import java.time.Instant;

/** DTO du module 6 : activités et agenda. */
public final class ActivityDtos {
    private ActivityDtos() {
    }

    public record ActivityDto(Long id, ActivityType type, String subject, String description, Instant dueAt,
                              Integer durationMinutes, ActivityStatus status, Instant completedAt, Ref contact,
                              Ref company, Ref opportunity, Ref lead, UserSummary owner, Instant reminderAt,
                              boolean automatic, boolean overdue, Instant createdAt) {
        public static ActivityDto of(Activity a) {
            boolean overdue = a.getStatus() == ActivityStatus.A_FAIRE && a.getType() != ActivityType.SYSTEME
                    && a.getDueAt().isBefore(Instant.now());
            return new ActivityDto(a.getId(), a.getType(), a.getSubject(), a.getDescription(), a.getDueAt(),
                    a.getDurationMinutes(), a.getStatus(), a.getCompletedAt(),
                    a.getContact() == null ? null : Ref.of(a.getContact().getId(), a.getContact().getFullName()),
                    a.getCompany() == null ? null : Ref.of(a.getCompany().getId(), a.getCompany().getName()),
                    a.getOpportunity() == null ? null : Ref.of(a.getOpportunity().getId(), a.getOpportunity().getTitle()),
                    a.getLead() == null ? null : Ref.of(a.getLead().getId(), a.getLead().getFullName()),
                    UserSummary.of(a.getOwner()), a.getReminderAt(), a.isAutomatic(), overdue, a.getCreatedAt());
        }
    }

    public record ActivityRequest(@NotNull ActivityType type,
                                  @NotBlank @Size(max = 200) String subject,
                                  @Size(max = 4000) String description,
                                  @NotNull Instant dueAt,
                                  @Min(0) Integer durationMinutes,
                                  ActivityStatus status,
                                  Long contactId,
                                  Long companyId,
                                  Long opportunityId,
                                  Long leadId,
                                  Long ownerId,
                                  /* minutes avant l'échéance ; null = pas de rappel */
                                  @Min(0) Integer reminderMinutesBefore) {
    }
}
