package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.LeadSource;
import ma.crm.domain.Enums.LeadStatus;
import ma.crm.domain.Enums.OpportunityStage;
import ma.crm.dto.CommonDtos.ImportResult;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.SalesDtos.*;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.*;
import ma.crm.security.CurrentUser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static ma.crm.service.CompanyService.blankToNull;

/** Module 3 : capture, qualification, attribution et conversion des leads. */
@Service
@RequiredArgsConstructor
@Transactional
public class LeadService {

    static final String[] CSV_HEADERS = {"prenom", "nom", "email", "telephone", "entreprise", "poste", "secteur",
            "ville", "source", "statut", "budget", "score", "commercial"};
    private static final Set<String> SORTABLE = Set.of("createdAt", "lastName", "companyName", "score", "status");
    /** Limite anti-spam du formulaire public : nombre de soumissions par adresse IP et par heure. */
    private static final int PUBLIC_LIMIT_PER_HOUR = 10;

    private final LeadRepository leads;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final OpportunityRepository opportunities;
    private final UserRepository users;
    private final LeadScoringService scoring;
    private final NotificationService notifications;
    private final HistoryService history;
    private final AuditService audit;
    private final Map<String, Deque<Instant>> publicSubmissions = new ConcurrentHashMap<>();

    public record Filter(String q, LeadStatus status, LeadSource source, Long assignedToId, Integer minScore,
                         boolean includeConverted) {
    }

    @Transactional(readOnly = true)
    public PageResponse<LeadDto> search(Filter f, int page, int size, String sort, String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromOptionalString(dir)
                .orElse(Sort.Direction.DESC), sortField));
        return PageResponse.of(leads.findAll(spec(f), pageable), LeadDto::of);
    }

    private Specification<Lead> spec(Filter f) {
        Specification<Lead> spec = (root, query, cb) -> cb.isFalse(root.get("anonymized"));
        if (!CurrentUser.has(Permission.ALL_RECORDS)) {
            // Un commercial voit ses leads et ceux qui ne sont encore attribués à personne.
            Long me = CurrentUser.id();
            spec = spec.and((root, query, cb) -> cb.or(cb.isNull(root.get("assignedTo")),
                    cb.equal(root.get("assignedTo").get("id"), me)));
        }
        if (StringUtils.hasText(f.q())) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), like),
                    cb.like(cb.lower(root.get("lastName")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("companyName")), like)));
        }
        if (f.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
        } else if (!f.includeConverted()) {
            spec = spec.and((root, query, cb) -> cb.notEqual(root.get("status"), LeadStatus.CONVERTI));
        }
        if (f.source() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("source"), f.source()));
        }
        if (f.assignedToId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), f.assignedToId()));
        }
        if (f.minScore() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("score"), f.minScore()));
        }
        return spec;
    }

    @Transactional(readOnly = true)
    public LeadDto get(Long id) {
        Lead lead = find(id);
        checkAccess(lead);
        return LeadDto.of(lead);
    }

    public LeadDto create(LeadRequest req) {
        Lead lead = new Lead();
        apply(lead, req);
        if (lead.getAssignedTo() == null && !CurrentUser.has(Permission.LEAD_ASSIGN)) {
            lead.setAssignedTo(users.getReferenceById(CurrentUser.id()));
        }
        leads.save(lead);
        rescore(lead);
        notifyAssignee(lead);
        audit.log(AuditService.CREATE, "Lead", lead.getId(), lead.getFullName());
        return LeadDto.of(lead);
    }

    public LeadDto update(Long id, LeadRequest req) {
        Lead lead = find(id);
        checkAccess(lead);
        if (lead.getStatus() == LeadStatus.CONVERTI) {
            throw new BusinessException("Ce lead est déjà converti : modifiez plutôt l'entreprise ou le contact");
        }
        User previous = lead.getAssignedTo();
        apply(lead, req);
        if (req.status() == LeadStatus.CONVERTI) {
            throw new BusinessException("Utilisez l'action « Convertir » pour convertir un lead");
        }
        rescore(lead);
        if (lead.getAssignedTo() != null && !Objects.equals(previous, lead.getAssignedTo())) {
            notifyAssignee(lead);
        }
        audit.log(AuditService.UPDATE, "Lead", id, lead.getFullName());
        return LeadDto.of(lead);
    }

    public LeadDto assign(Long id, Long userId) {
        Lead lead = find(id);
        User user = users.findById(userId).orElseThrow(() -> NotFoundException.of("Utilisateur", userId));
        lead.setAssignedTo(user);
        history.record("Lead attribué à " + user.getFullName(), null, HistoryService.Links.lead(lead));
        notifyAssignee(lead);
        audit.log(AuditService.UPDATE, "Lead", id, "attribué à " + user.getEmail());
        return LeadDto.of(lead);
    }

    public void delete(Long id) {
        Lead lead = find(id);
        checkAccess(lead);
        leads.delete(lead);
        audit.log(AuditService.DELETE, "Lead", id, lead.getFullName());
    }

    /** Conversion d'un lead en client (entreprise + contact) et, si demandé, en opportunité. */
    public ConvertLeadResult convert(Long id, ConvertLeadRequest req) {
        Lead lead = find(id);
        checkAccess(lead);
        if (lead.getStatus() == LeadStatus.CONVERTI) {
            throw BusinessException.conflict("Ce lead est déjà converti");
        }
        User owner = lead.getAssignedTo() != null ? lead.getAssignedTo() : users.getReferenceById(CurrentUser.id());

        Company company;
        if (req.existingCompanyId() != null) {
            company = companies.findById(req.existingCompanyId())
                    .orElseThrow(() -> NotFoundException.of("Entreprise", req.existingCompanyId()));
        } else {
            String name = StringUtils.hasText(lead.getCompanyName()) ? lead.getCompanyName() : lead.getFullName();
            company = new Company();
            company.setName(name);
            company.setSector(lead.getSector());
            company.setCity(lead.getCity());
            company.setOwner(owner);
            companies.save(company);
        }
        if (company.getStatus() == CompanyStatus.INACTIF) {
            company.setStatus(CompanyStatus.PROSPECT);
        }

        Contact contact = new Contact();
        contact.setFirstName(lead.getFirstName());
        contact.setLastName(lead.getLastName());
        contact.setEmail(lead.getEmail());
        contact.setPhone(lead.getPhone());
        contact.setPosition(lead.getPosition());
        contact.setCompany(company);
        contact.setOwner(owner);
        contact.setMarketingConsent(lead.isMarketingConsent());
        contact.setConsentDate(lead.isMarketingConsent() ? lead.getCreatedAt() : null);
        contacts.save(contact);

        Opportunity opp = null;
        if (req.createOpportunity()) {
            opp = new Opportunity();
            opp.setTitle(StringUtils.hasText(req.opportunityTitle()) ? req.opportunityTitle().trim()
                    : "Opportunité " + company.getName());
            BigDecimal amount = req.amount() != null ? req.amount()
                    : lead.getEstimatedBudget() != null ? lead.getEstimatedBudget() : BigDecimal.ZERO;
            opp.setAmount(amount);
            opp.setStage(OpportunityStage.QUALIFICATION);
            opp.setProbability(OpportunityStage.QUALIFICATION.defaultProbability);
            opp.setExpectedCloseDate(req.expectedCloseDate() != null ? req.expectedCloseDate() : LocalDate.now().plusMonths(1));
            opp.setCompany(company);
            opp.setContact(contact);
            opp.setOwner(owner);
            opp.setLastActivityAt(Instant.now());
            opportunities.save(opp);
        }

        lead.setStatus(LeadStatus.CONVERTI);
        lead.setConvertedAt(Instant.now());
        lead.setConvertedCompany(company);
        lead.setConvertedContact(contact);
        lead.setConvertedOpportunity(opp);

        history.record("Lead converti : " + lead.getFullName(),
                opp == null ? null : "Opportunité créée : " + opp.getTitle(),
                new HistoryService.Links(company, contact, opp, lead));
        audit.log(AuditService.UPDATE, "Lead", id, "converti en entreprise #" + company.getId());
        return new ConvertLeadResult(company.getId(), contact.getId(), opp == null ? null : opp.getId());
    }

    public LeadDto rescore(Long id) {
        Lead lead = find(id);
        rescore(lead);
        return LeadDto.of(lead);
    }

    /** Recalcule le score de tous les leads ouverts (après un ré-entraînement du modèle). */
    public int rescoreAll() {
        List<Lead> open = leads.findByStatusIn(List.of(LeadStatus.NOUVEAU, LeadStatus.CONTACTE, LeadStatus.QUALIFIE));
        open.forEach(this::rescore);
        return open.size();
    }

    @Transactional(readOnly = true)
    public Map<?, ?> retrainModel() {
        List<Lead> history = leads.findByStatusIn(List.of(LeadStatus.CONVERTI, LeadStatus.NON_QUALIFIE));
        if (history.size() < 20) {
            throw new BusinessException("Il faut au moins 20 leads clôturés (convertis ou non qualifiés) pour "
                    + "ré-entraîner le modèle ; il y en a " + history.size());
        }
        return scoring.retrain(history);
    }

    private void rescore(Lead lead) {
        LeadScoringService.ScoreResult result = scoring.score(lead);
        lead.setScore(result.score());
        lead.setScoredAt(Instant.now());
    }

    // ---- Formulaire web public ----

    public void capturePublic(PublicLeadRequest req, String ip) {
        if (StringUtils.hasText(req.website())) {
            return; // robot : on ignore silencieusement
        }
        Deque<Instant> recent = publicSubmissions.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (recent) {
            Instant oneHourAgo = Instant.now().minusSeconds(3600);
            while (!recent.isEmpty() && recent.peekFirst().isBefore(oneHourAgo)) {
                recent.pollFirst();
            }
            if (recent.size() >= PUBLIC_LIMIT_PER_HOUR) {
                throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Trop de demandes, réessayez plus tard");
            }
            recent.addLast(Instant.now());
        }
        Lead lead = new Lead();
        lead.setFirstName(req.firstName().trim());
        lead.setLastName(req.lastName().trim());
        lead.setEmail(req.email().trim().toLowerCase());
        lead.setPhone(blankToNull(req.phone()));
        lead.setCompanyName(blankToNull(req.companyName()));
        lead.setNotes(blankToNull(req.message()));
        lead.setMarketingConsent(req.marketingConsent());
        lead.setSource(LeadSource.FORMULAIRE_WEB);
        lead.setAssignedTo(leastBusyCommercial());
        leads.save(lead);
        rescore(lead);
        notifyAssignee(lead);
    }

    /** Attribution automatique au commercial ayant le moins de leads ouverts. */
    private User leastBusyCommercial() {
        List<User> sellers = users.findByActiveTrueAndRole_CodeIn(List.of(Role.COMMERCIAL));
        if (sellers.isEmpty()) {
            return null;
        }
        Map<Long, Long> load = new HashMap<>();
        leads.findByStatusIn(List.of(LeadStatus.NOUVEAU, LeadStatus.CONTACTE, LeadStatus.QUALIFIE)).forEach(l -> {
            if (l.getAssignedTo() != null) {
                load.merge(l.getAssignedTo().getId(), 1L, Long::sum);
            }
        });
        return sellers.stream().min(Comparator.comparingLong(u -> load.getOrDefault(u.getId(), 0L))).orElse(null);
    }

    private void notifyAssignee(Lead lead) {
        if (lead.getAssignedTo() != null && !Objects.equals(lead.getAssignedTo().getId(), CurrentUser.id())) {
            notifications.notify(lead.getAssignedTo(), "LEAD", "Nouveau lead attribué : " + lead.getFullName(),
                    (lead.getCompanyName() == null ? "" : lead.getCompanyName() + " · ")
                            + "score " + (lead.getScore() == null ? "—" : lead.getScore() + " %"),
                    "/leads/" + lead.getId(), false);
        }
    }

    // ---- Import / export ----

    @Transactional
    public byte[] exportCsv(Filter filter) {
        List<Lead> list = leads.findAll(spec(filter), Sort.by("createdAt").descending());
        audit.log(AuditService.EXPORT, "Lead", null, list.size() + " leads exportés");
        return CsvSupport.write(CSV_HEADERS, list, l -> new Object[]{l.getFirstName(), l.getLastName(), l.getEmail(),
                l.getPhone(), l.getCompanyName(), l.getPosition(), l.getSector(), l.getCity(), l.getSource().name(),
                l.getStatus().name(), l.getEstimatedBudget(), l.getScore(),
                l.getAssignedTo() == null ? null : l.getAssignedTo().getEmail()});
    }

    public ImportResult importCsv(InputStream input) {
        List<CSVRecord> records = CsvSupport.read(input);
        int created = 0;
        List<String> errors = new ArrayList<>();
        for (CSVRecord r : records) {
            long line = r.getRecordNumber() + 1;
            String first = CsvSupport.get(r, "prenom");
            String last = CsvSupport.get(r, "nom");
            if (first == null || last == null) {
                errors.add("Ligne " + line + " : prénom et nom obligatoires");
                continue;
            }
            Lead lead = new Lead();
            lead.setFirstName(first);
            lead.setLastName(last);
            lead.setEmail(CsvSupport.get(r, "email"));
            lead.setPhone(CsvSupport.get(r, "telephone"));
            lead.setCompanyName(CsvSupport.get(r, "entreprise"));
            lead.setPosition(CsvSupport.get(r, "poste"));
            lead.setSector(CsvSupport.get(r, "secteur"));
            lead.setCity(CsvSupport.get(r, "ville"));
            lead.setSource(LeadSource.IMPORT);
            String budget = CsvSupport.get(r, "budget");
            try {
                lead.setEstimatedBudget(budget == null ? null : new BigDecimal(budget.replace(" ", "").replace(',', '.')));
            } catch (NumberFormatException e) {
                errors.add("Ligne " + line + " : budget invalide « " + budget + " »");
                continue;
            }
            String seller = CsvSupport.get(r, "commercial");
            lead.setAssignedTo(seller == null ? null : users.findByEmailIgnoreCase(seller).orElse(null));
            leads.save(lead);
            rescore(lead);
            created++;
        }
        audit.log(AuditService.IMPORT, "Lead", null, created + " leads importés");
        return new ImportResult(created, 0, errors);
    }

    private void apply(Lead lead, LeadRequest req) {
        lead.setFirstName(req.firstName().trim());
        lead.setLastName(req.lastName().trim());
        lead.setEmail(blankToNull(req.email()));
        lead.setPhone(blankToNull(req.phone()));
        lead.setCompanyName(blankToNull(req.companyName()));
        lead.setPosition(blankToNull(req.position()));
        lead.setSector(blankToNull(req.sector()));
        lead.setCity(blankToNull(req.city()));
        if (req.source() != null) {
            lead.setSource(req.source());
        }
        if (req.status() != null && req.status() != LeadStatus.CONVERTI) {
            lead.setStatus(req.status());
        }
        lead.setEstimatedBudget(req.estimatedBudget());
        lead.setNotes(blankToNull(req.notes()));
        lead.setMarketingConsent(req.marketingConsent());
        if (req.assignedToId() != null) {
            if (!CurrentUser.has(Permission.LEAD_ASSIGN) && !req.assignedToId().equals(CurrentUser.id())) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Seul le directeur commercial peut attribuer un lead à un autre commercial");
            }
            lead.setAssignedTo(users.findById(req.assignedToId())
                    .orElseThrow(() -> NotFoundException.of("Utilisateur", req.assignedToId())));
        }
    }

    private void checkAccess(Lead lead) {
        if (lead.getAssignedTo() != null) {
            CurrentUser.checkOwnership(lead.getAssignedTo());
        }
    }

    Lead find(Long id) {
        return leads.findById(id).filter(l -> !l.isAnonymized()).orElseThrow(() -> NotFoundException.of("Lead", id));
    }
}
