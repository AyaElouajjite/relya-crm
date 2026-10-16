package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.ActivityStatus;
import ma.crm.domain.Enums.ActivityType;
import ma.crm.dto.ActivityDtos.ActivityDto;
import ma.crm.dto.ActivityDtos.ActivityRequest;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.*;
import ma.crm.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static ma.crm.service.CompanyService.blankToNull;

/** Module 6 : planification des appels, rendez-vous et tâches ; calendrier partagé ; rappels. */
@Service
@RequiredArgsConstructor
@Transactional
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    private static final DateTimeFormatter ICS = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final ActivityRepository activities;
    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final OpportunityRepository opportunities;
    private final LeadRepository leads;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    public record Filter(Instant from, Instant to, Long ownerId, ActivityType type, ActivityStatus status,
                         Long companyId, Long contactId, Long opportunityId, Long leadId, boolean includeSystem,
                         boolean overdueOnly) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityDto> search(Filter f, int page, int size, boolean ascending) {
        Sort sort = ascending ? Sort.by("dueAt").ascending() : Sort.by("dueAt").descending();
        return PageResponse.of(activities.findAll(spec(f), PageRequest.of(page, size, sort)), ActivityDto::of);
    }

    /** Calendrier : toutes les activités de la période, sans pagination. */
    @Transactional(readOnly = true)
    public List<ActivityDto> calendar(Filter f) {
        return activities.findAll(spec(f), Sort.by("dueAt")).stream().map(ActivityDto::of).toList();
    }

    private Specification<Activity> spec(Filter f) {
        Specification<Activity> spec = (root, query, cb) -> cb.conjunction();
        if (!f.includeSystem()) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("type"), ActivityType.SYSTEME));
        }
        if (f.from() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dueAt"), f.from()));
        }
        if (f.to() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("dueAt"), f.to()));
        }
        if (f.ownerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
        }
        if (f.type() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), f.type()));
        }
        if (f.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.overdueOnly()) {
            spec = spec.and((root, query, cb) -> cb.and(cb.equal(root.get("status"), ActivityStatus.A_FAIRE),
                    cb.lessThan(root.get("dueAt"), Instant.now())));
        }
        if (f.companyId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("id"), f.companyId()));
        }
        if (f.contactId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("contact").get("id"), f.contactId()));
        }
        if (f.opportunityId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("opportunity").get("id"), f.opportunityId()));
        }
        if (f.leadId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("lead").get("id"), f.leadId()));
        }
        return spec;
    }

    @Transactional(readOnly = true)
    public ActivityDto get(Long id) {
        return ActivityDto.of(find(id));
    }

    public ActivityDto create(ActivityRequest req) {
        if (req.type() == ActivityType.SYSTEME) {
            throw new BusinessException("Le type SYSTEME est réservé à l'historique automatique");
        }
        Activity a = new Activity();
        apply(a, req);
        if (a.getOwner() == null) {
            a.setOwner(users.getReferenceById(CurrentUser.id()));
        }
        activities.save(a);
        touch(a);
        if (a.getOwner() != null && !Objects.equals(a.getOwner().getId(), CurrentUser.id())) {
            notifications.notify(a.getOwner(), "ACTIVITY", "Nouvelle activité : " + a.getSubject(),
                    "Prévue le " + PdfService.DATE.withZone(java.time.ZoneId.systemDefault()).format(a.getDueAt()),
                    "/activities", false);
        }
        return ActivityDto.of(a);
    }

    public ActivityDto update(Long id, ActivityRequest req) {
        Activity a = find(id);
        if (a.isAutomatic() && a.getType() == ActivityType.SYSTEME) {
            throw new BusinessException("L'historique automatique n'est pas modifiable");
        }
        CurrentUser.checkOwnership(a.getOwner());
        Instant previousDue = a.getDueAt();
        apply(a, req);
        if (!previousDue.equals(a.getDueAt())) {
            a.setReminderSent(false);
        }
        touch(a);
        return ActivityDto.of(a);
    }

    public ActivityDto setStatus(Long id, ActivityStatus status) {
        Activity a = find(id);
        CurrentUser.checkOwnership(a.getOwner());
        a.setStatus(status);
        a.setCompletedAt(status == ActivityStatus.TERMINEE ? Instant.now() : null);
        touch(a);
        return ActivityDto.of(a);
    }

    public void delete(Long id) {
        Activity a = find(id);
        if (a.getType() == ActivityType.SYSTEME) {
            throw new BusinessException("L'historique automatique ne peut pas être supprimé");
        }
        CurrentUser.checkOwnership(a.getOwner());
        activities.delete(a);
        audit.log(AuditService.DELETE, "Activity", id, a.getSubject());
    }

    /** Toute interaction relance le compteur d'inactivité de l'opportunité liée. */
    private void touch(Activity a) {
        if (a.getOpportunity() != null) {
            a.getOpportunity().setLastActivityAt(Instant.now());
        }
    }

    private void apply(Activity a, ActivityRequest req) {
        a.setType(req.type());
        a.setSubject(req.subject().trim());
        a.setDescription(blankToNull(req.description()));
        a.setDueAt(req.dueAt());
        a.setDurationMinutes(req.durationMinutes());
        if (req.status() != null) {
            a.setStatus(req.status());
            a.setCompletedAt(req.status() == ActivityStatus.TERMINEE ? Instant.now() : null);
        }
        Opportunity opp = req.opportunityId() == null ? null : opportunities.findById(req.opportunityId())
                .orElseThrow(() -> NotFoundException.of("Opportunité", req.opportunityId()));
        Contact contact = req.contactId() == null ? null : contacts.findById(req.contactId())
                .orElseThrow(() -> NotFoundException.of("Contact", req.contactId()));
        Company company = req.companyId() != null ? companies.findById(req.companyId())
                .orElseThrow(() -> NotFoundException.of("Entreprise", req.companyId()))
                : contact != null && contact.getCompany() != null ? contact.getCompany()
                : opp != null ? opp.getCompany() : null;
        a.setOpportunity(opp);
        a.setContact(contact);
        a.setCompany(company);
        a.setLead(req.leadId() == null ? null : leads.findById(req.leadId())
                .orElseThrow(() -> NotFoundException.of("Lead", req.leadId())));
        if (req.ownerId() != null) {
            a.setOwner(users.findById(req.ownerId()).orElseThrow(() -> NotFoundException.of("Utilisateur", req.ownerId())));
        }
        a.setReminderAt(req.reminderMinutesBefore() == null ? null
                : req.dueAt().minus(req.reminderMinutesBefore(), ChronoUnit.MINUTES));
    }

    /** Rappels : vérifiés chaque minute, envoyés par notification temps réel et par e-mail. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void sendDueReminders() {
        List<Activity> due = activities.findDueReminders(Instant.now());
        for (Activity a : due) {
            a.setReminderSent(true);
            if (a.getOwner() != null) {
                notifications.notify(a.getOwner(), "REMINDER", "Rappel : " + a.getSubject(),
                        "Échéance : " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                                .withZone(java.time.ZoneId.systemDefault()).format(a.getDueAt()),
                        "/activities", true);
            }
        }
        if (!due.isEmpty()) {
            log.info("{} rappel(s) envoyé(s)", due.size());
        }
    }

    /**
     * Flux iCalendar des activités d'un utilisateur, pour synchroniser son agenda
     * (Google Agenda, Outlook, Calendrier Apple).
     */
    @Transactional(readOnly = true)
    public String ics(Long userId) {
        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        var f = new Filter(from, null, userId, null, null, null, null, null, null, false, false);
        StringBuilder sb = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//CRM//Agenda//FR\r\nCALSCALE:GREGORIAN\r\n");
        for (Activity a : activities.findAll(spec(f), Sort.by("dueAt"))) {
            if (a.getStatus() == ActivityStatus.ANNULEE) {
                continue;
            }
            Instant end = a.getDueAt().plus(a.getDurationMinutes() == null ? 30 : a.getDurationMinutes(), ChronoUnit.MINUTES);
            sb.append("BEGIN:VEVENT\r\n")
                    .append("UID:activity-").append(a.getId()).append("@crm\r\n")
                    .append("DTSTAMP:").append(ICS.format(a.getUpdatedAt() == null ? Instant.now() : a.getUpdatedAt())).append("\r\n")
                    .append("DTSTART:").append(ICS.format(a.getDueAt())).append("\r\n")
                    .append("DTEND:").append(ICS.format(end)).append("\r\n")
                    .append("SUMMARY:").append(escape("[" + a.getType() + "] " + a.getSubject())).append("\r\n");
            if (a.getDescription() != null) {
                sb.append("DESCRIPTION:").append(escape(a.getDescription())).append("\r\n");
            }
            if (a.getStatus() == ActivityStatus.TERMINEE) {
                sb.append("STATUS:CONFIRMED\r\n");
            }
            sb.append("END:VEVENT\r\n");
        }
        return sb.append("END:VCALENDAR\r\n").toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r", "").replace("\n", "\\n");
    }

    Activity find(Long id) {
        return activities.findById(id).orElseThrow(() -> NotFoundException.of("Activité", id));
    }
}
