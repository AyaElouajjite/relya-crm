package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.AuditLog;
import ma.crm.dto.CommonDtos.AuditLogDto;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.repository.AuditLogRepository;
import ma.crm.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Journalisation des actions sensibles exigée par le cahier des charges (traçabilité). */
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String DELETE = "DELETE";
    public static final String ARCHIVE = "ARCHIVE";
    public static final String RESTORE = "RESTORE";
    public static final String EXPORT = "EXPORT";
    public static final String IMPORT = "IMPORT";

    private final AuditLogRepository repository;

    @Transactional
    public void log(String action, String entityType, Long entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(CurrentUser.email());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details == null || details.length() <= 500 ? details : details.substring(0, 500));
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> list(int page, int size) {
        return PageResponse.of(repository.findAllByOrderByAtDesc(PageRequest.of(page, size)), AuditLogDto::of);
    }
}
