package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.TicketChannel;
import ma.crm.domain.Enums.TicketPriority;
import ma.crm.domain.Enums.TicketStatus;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.TicketDtos.*;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.ContactRepository;
import ma.crm.repository.TicketRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static ma.crm.service.CompanyService.blankToNull;

/** Module 7 : tickets SAV, avec SLA par priorité et notification du client à chaque changement. */
@Service
@RequiredArgsConstructor
@Transactional
public class TicketService {

    private final TicketRepository tickets;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final SequenceService sequences;
    private final NotificationService notifications;
    private final MailService mail;
    private final HistoryService history;
    private final AuditService audit;

    public record Filter(String q, TicketStatus status, TicketPriority priority, Long assigneeId, Long companyId,
                         boolean openOnly, boolean overdueOnly) {
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketDto> search(Filter f, int page, int size) {
        return PageResponse.of(tickets.findAll(spec(f), PageRequest.of(page, size, Sort.by("openedAt").descending())),
                t -> TicketDto.of(t, true, false));
    }

    private Specification<Ticket> spec(Filter f) {
        Specification<Ticket> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(f.q())) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("number")), like),
                    cb.like(cb.lower(root.get("subject")), like),
                    cb.like(cb.lower(root.get("company").get("name")), like)));
        }
        if (f.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.openOnly() || f.overdueOnly()) {
            spec = spec.and((root, query, cb) -> root.get("status").in(TicketStatus.RESOLU, TicketStatus.FERME).not());
        }
        if (f.overdueOnly()) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("dueAt"), Instant.now()));
        }
        if (f.priority() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), f.priority()));
        }
        if (f.assigneeId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignee").get("id"), f.assigneeId()));
        }
        if (f.companyId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("id"), f.companyId()));
        }
        return spec;
    }

    @Transactional(readOnly = true)
    public TicketDto get(Long id) {
        return TicketDto.of(find(id), true, true);
    }

    /** Création par un agent (téléphone, e-mail...). */
    public TicketDto create(TicketRequest req) {
        Contact contact = contacts.findById(req.contactId()).orElseThrow(() -> NotFoundException.of("Contact", req.contactId()));
        User assignee = req.assigneeId() == null ? null
                : users.findById(req.assigneeId()).orElseThrow(() -> NotFoundException.of("Utilisateur", req.assigneeId()));
        Ticket t = open(contact, req.subject(), req.description(), req.priority(),
                req.channel() == null ? TicketChannel.AGENT : req.channel(), req.category(), assignee);
        return TicketDto.of(t, true, true);
    }

    /** Création par le client depuis le portail. */
    public TicketDto createFromPortal(PortalTicketRequest req) {
        Contact contact = portalContact();
        TicketPriority priority = req.priority() == TicketPriority.URGENTE ? TicketPriority.HAUTE : req.priority();
        Ticket t = open(contact, req.subject(), req.description(), priority, TicketChannel.PORTAIL, req.category(), null);
        return TicketDto.of(t, false, true);
    }

    private Ticket open(Contact contact, String subject, String description, TicketPriority priority,
                        TicketChannel channel, String category, User assignee) {
        if (contact.getCompany() == null) {
            throw new BusinessException("Le contact doit être rattaché à une entreprise pour ouvrir un ticket");
        }
        Ticket t = new Ticket();
        t.setNumber(sequences.next("TCK"));
        t.setSubject(subject.trim());
        t.setDescription(blankToNull(description));
        t.setPriority(priority == null ? TicketPriority.NORMALE : priority);
        t.setChannel(channel);
        t.setCategory(blankToNull(category));
        t.setContact(contact);
        t.setCompany(contact.getCompany());
        t.setOpenedAt(Instant.now());
        t.setDueAt(t.getOpenedAt().plus(t.getPriority().slaHours, ChronoUnit.HOURS));
        t.setAssignee(assignee != null ? assignee : leastBusyAgent());
        tickets.save(t);
        history.record("Ticket " + t.getNumber() + " ouvert : " + t.getSubject(), null,
                new HistoryService.Links(t.getCompany(), contact, null, null));
        if (t.getAssignee() != null && !Objects.equals(t.getAssignee().getId(), CurrentUser.id())) {
            notifications.notify(t.getAssignee(), "TICKET", "Nouveau ticket " + t.getNumber() + " (" + t.getPriority() + ")",
                    t.getCompany().getName() + " · " + t.getSubject(), "/tickets/" + t.getId(), false);
        }
        notifyClient(t, "Votre demande " + t.getNumber() + " a bien été enregistrée",
                "Nous avons bien reçu votre demande « " + t.getSubject() + " ». Délai de traitement visé : "
                        + t.getPriority().slaHours + " h.");
        audit.log(AuditService.CREATE, "Ticket", t.getId(), t.getNumber());
        return t;
    }

    public TicketDto update(Long id, TicketUpdateRequest req) {
        Ticket t = find(id);
        List<String> changes = new ArrayList<>();
        if (req.status() != null && req.status() != t.getStatus()) {
            changes.add("statut : " + statusLabel(req.status()));
            t.setStatus(req.status());
            if (!req.status().isOpen()) {
                if (t.getResolvedAt() == null) {
                    t.setResolvedAt(Instant.now());
                }
            } else {
                t.setResolvedAt(null);
            }
        }
        if (req.priority() != null && req.priority() != t.getPriority()) {
            changes.add("priorité : " + req.priority());
            t.setPriority(req.priority());
            t.setDueAt(t.getOpenedAt().plus(req.priority().slaHours, ChronoUnit.HOURS));
        }
        if (req.assigneeId() != null && (t.getAssignee() == null || !t.getAssignee().getId().equals(req.assigneeId()))) {
            User assignee = users.findById(req.assigneeId()).orElseThrow(() -> NotFoundException.of("Utilisateur", req.assigneeId()));
            t.setAssignee(assignee);
            changes.add("pris en charge par " + assignee.getFirstName());
            if (!Objects.equals(assignee.getId(), CurrentUser.id())) {
                notifications.notify(assignee, "TICKET", "Ticket " + t.getNumber() + " vous est attribué",
                        t.getSubject(), "/tickets/" + t.getId(), false);
            }
        }
        if (req.category() != null) {
            t.setCategory(blankToNull(req.category()));
        }
        if (!changes.isEmpty()) {
            String summary = String.join(", ", changes);
            history.record("Ticket " + t.getNumber() + " — " + summary, null,
                    new HistoryService.Links(t.getCompany(), t.getContact(), null, null));
            notifyClient(t, "Mise à jour de votre demande " + t.getNumber(),
                    "Votre demande « " + t.getSubject() + " » a été mise à jour (" + summary + ").");
            audit.log(AuditService.UPDATE, "Ticket", id, summary);
        }
        return TicketDto.of(t, true, true);
    }

    /** Réponse d'un agent (publique ou note interne). */
    public TicketDto reply(Long id, MessageRequest req) {
        Ticket t = find(id);
        TicketMessage m = message(t, req.content(), req.internal());
        if (!req.internal()) {
            if (t.getFirstResponseAt() == null) {
                t.setFirstResponseAt(Instant.now());
            }
            if (t.getStatus() == TicketStatus.OUVERT) {
                t.setStatus(TicketStatus.EN_COURS);
            }
            notifyClient(t, "Nouvelle réponse à votre demande " + t.getNumber(),
                    m.getAuthor().getFullName() + " a écrit :\n\n" + m.getContent());
        }
        return TicketDto.of(t, true, true);
    }

    // ---- Portail client ----

    @Transactional(readOnly = true)
    public PageResponse<TicketDto> portalList(int page, int size) {
        Long companyId = portalCompanyId();
        return PageResponse.of(tickets.findAll((root, query, cb) -> cb.equal(root.get("company").get("id"), companyId),
                PageRequest.of(page, size, Sort.by("openedAt").descending())), t -> TicketDto.of(t, false, false));
    }

    @Transactional(readOnly = true)
    public TicketDto portalGet(Long id) {
        return TicketDto.of(portalTicket(id), false, true);
    }

    public TicketDto portalReply(Long id, MessageRequest req) {
        Ticket t = portalTicket(id);
        if (t.getStatus() == TicketStatus.FERME) {
            throw new BusinessException("Ce ticket est fermé : ouvrez une nouvelle demande");
        }
        message(t, req.content(), false);
        if (t.getStatus() == TicketStatus.EN_ATTENTE_CLIENT || t.getStatus() == TicketStatus.RESOLU) {
            t.setStatus(TicketStatus.EN_COURS);
            t.setResolvedAt(null);
        }
        if (t.getAssignee() != null) {
            notifications.notify(t.getAssignee(), "TICKET", "Réponse du client sur " + t.getNumber(),
                    req.content().length() > 120 ? req.content().substring(0, 120) + "…" : req.content(),
                    "/tickets/" + t.getId(), false);
        }
        return TicketDto.of(t, false, true);
    }

    private Ticket portalTicket(Long id) {
        Ticket t = find(id);
        if (!t.getCompany().getId().equals(portalCompanyId())) {
            throw new NotFoundException("Ticket introuvable");
        }
        return t;
    }

    private Long portalCompanyId() {
        Long companyId = CurrentUser.require().companyId();
        if (companyId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Votre compte n'est rattaché à aucune entreprise");
        }
        return companyId;
    }

    private Contact portalContact() {
        Long contactId = CurrentUser.require().contactId();
        if (contactId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Votre compte n'est rattaché à aucun contact");
        }
        return contacts.findById(contactId).orElseThrow();
    }

    // ---- Interne ----

    private TicketMessage message(Ticket t, String content, boolean internal) {
        TicketMessage m = new TicketMessage();
        m.setTicket(t);
        m.setAuthor(users.getReferenceById(CurrentUser.id()));
        m.setContent(content.trim());
        m.setInternal(internal);
        t.getMessages().add(m);
        tickets.saveAndFlush(t);
        return m;
    }

    /** Notification du client à chaque changement : e-mail, et notification in-app s'il a un compte portail. */
    private void notifyClient(Ticket t, String title, String message) {
        Contact c = t.getContact();
        Optional<User> portalUser = users.findByContact_Id(c.getId());
        portalUser.ifPresent(u -> notifications.notify(u, "TICKET", title, message, "/portal/tickets/" + t.getId(), false));
        if (StringUtils.hasText(c.getEmail())) {
            mail.send(c.getEmail(), title, "Bonjour " + c.getFirstName() + ",\n\n" + message
                    + "\n\nVous pouvez suivre votre demande depuis votre espace client.\n\nLe service client");
        }
    }

    /** Attribution automatique à l'agent support ayant le moins de tickets ouverts. */
    private User leastBusyAgent() {
        List<User> agents = users.findByActiveTrueAndRole_CodeIn(List.of(Role.SUPPORT));
        if (agents.isEmpty()) {
            return null;
        }
        Map<Long, Long> load = new HashMap<>();
        tickets.findAll(spec(new Filter(null, null, null, null, null, true, false))).forEach(t -> {
            if (t.getAssignee() != null) {
                load.merge(t.getAssignee().getId(), 1L, Long::sum);
            }
        });
        return agents.stream().min(Comparator.comparingLong(u -> load.getOrDefault(u.getId(), 0L))).orElse(null);
    }

    static String statusLabel(TicketStatus s) {
        return switch (s) {
            case OUVERT -> "ouvert";
            case EN_COURS -> "en cours";
            case EN_ATTENTE_CLIENT -> "en attente de votre retour";
            case RESOLU -> "résolu";
            case FERME -> "fermé";
        };
    }

    Ticket find(Long id) {
        return tickets.findById(id).orElseThrow(() -> NotFoundException.of("Ticket", id));
    }
}
