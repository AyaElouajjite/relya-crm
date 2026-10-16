package ma.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.crm.domain.Enums.TicketChannel;
import ma.crm.domain.Enums.TicketPriority;
import ma.crm.domain.Enums.TicketStatus;
import ma.crm.domain.Ticket;
import ma.crm.domain.TicketMessage;
import ma.crm.dto.CommonDtos.Ref;
import ma.crm.dto.UserDtos.UserSummary;

import java.time.Instant;
import java.util.List;

/** DTO du module 7 : service client (SAV). */
public final class TicketDtos {
    private TicketDtos() {
    }

    public record MessageDto(Long id, String author, boolean fromClient, String content, boolean internal,
                             Instant createdAt) {
        public static MessageDto of(TicketMessage m) {
            return new MessageDto(m.getId(), m.getAuthor().getFullName(),
                    m.getAuthor().getContact() != null, m.getContent(), m.isInternal(), m.getCreatedAt());
        }
    }

    public record TicketDto(Long id, String number, String subject, String description, TicketPriority priority,
                            TicketStatus status, TicketChannel channel, String category, Ref contact, Ref company,
                            UserSummary assignee, Instant openedAt, Instant dueAt, Instant firstResponseAt,
                            Instant resolvedAt, boolean overdue, List<MessageDto> messages) {
        /** @param includeInternal faux pour le portail client : les notes internes sont masquées */
        public static TicketDto of(Ticket t, boolean includeInternal, boolean withMessages) {
            boolean overdue = t.getStatus().isOpen() && t.getDueAt().isBefore(Instant.now());
            List<MessageDto> messages = !withMessages ? List.of() : t.getMessages().stream()
                    .filter(m -> includeInternal || !m.isInternal()).map(MessageDto::of).toList();
            return new TicketDto(t.getId(), t.getNumber(), t.getSubject(), t.getDescription(), t.getPriority(),
                    t.getStatus(), t.getChannel(), t.getCategory(),
                    Ref.of(t.getContact().getId(), t.getContact().getFullName()),
                    Ref.of(t.getCompany().getId(), t.getCompany().getName()),
                    includeInternal ? UserSummary.of(t.getAssignee()) : null,
                    t.getOpenedAt(), t.getDueAt(), t.getFirstResponseAt(), t.getResolvedAt(), overdue, messages);
        }
    }

    public record TicketRequest(@NotBlank @Size(max = 200) String subject,
                                @Size(max = 4000) String description,
                                TicketPriority priority,
                                TicketChannel channel,
                                @Size(max = 60) String category,
                                @NotNull Long contactId,
                                Long assigneeId) {
    }

    /** Ticket ouvert par le client depuis le portail. */
    public record PortalTicketRequest(@NotBlank @Size(max = 200) String subject,
                                      @NotBlank @Size(max = 4000) String description,
                                      TicketPriority priority,
                                      @Size(max = 60) String category) {
    }

    public record TicketUpdateRequest(TicketStatus status, TicketPriority priority, Long assigneeId,
                                      @Size(max = 60) String category) {
    }

    public record MessageRequest(@NotBlank @Size(max = 4000) String content, boolean internal) {
    }
}
