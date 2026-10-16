package ma.crm.dto;

import ma.crm.domain.AuditLog;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public final class CommonDtos {
    private CommonDtos() {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }

    public record ImportResult(int created, int updated, List<String> errors) {
    }

    /** Référence légère vers un enregistrement lié (affichage d'un lien). */
    public record Ref(Long id, String label) {
        public static Ref of(Long id, String label) {
            return id == null ? null : new Ref(id, label);
        }
    }

    public record AuditLogDto(Long id, String actor, String action, String entityType, Long entityId,
                              String details, Instant at) {
        public static AuditLogDto of(AuditLog a) {
            return new AuditLogDto(a.getId(), a.getActor(), a.getAction(), a.getEntityType(), a.getEntityId(),
                    a.getDetails(), a.getAt());
        }
    }
}
