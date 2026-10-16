package ma.crm.service;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Company;
import ma.crm.domain.CompanyStatus;
import ma.crm.domain.User;
import ma.crm.dto.CommonDtos.ImportResult;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.CompanyDtos.*;
import ma.crm.dto.ContactDtos.ContactDto;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.CompanyRepository;
import ma.crm.repository.ContactRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.CurrentUser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyService {

    static final String[] CSV_HEADERS = {"raisonSociale", "secteur", "ice", "ville", "adresse", "telephone",
            "email", "siteWeb", "statut", "tags", "commercial"};
    private static final Set<String> SORTABLE = Set.of("name", "sector", "city", "status", "createdAt", "updatedAt");

    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final AuditService audit;
    private final ma.crm.repository.OpportunityRepository opportunities;
    private final ma.crm.repository.QuoteRepository quotes;
    private final ma.crm.repository.InvoiceRepository invoices;
    private final ma.crm.repository.TicketRepository tickets;
    private final ma.crm.repository.ActivityRepository activities;

    public record Filter(String q, String sector, String city, CompanyStatus status, String tag, Long ownerId,
                         boolean archived) {
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyDto> search(Filter filter, int page, int size, String sort, String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "name";
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromOptionalString(dir)
                .orElse(Sort.Direction.ASC), sortField));
        Page<Company> result = companies.findAll(spec(filter), pageable);
        Map<Long, Long> counts = contactCounts(result.getContent());
        return PageResponse.of(result, c -> CompanyDto.of(c, counts.getOrDefault(c.getId(), 0L)));
    }

    private Specification<Company> spec(Filter f) {
        Specification<Company> spec = (root, query, cb) -> cb.equal(root.get("archived"), f.archived());
        if (StringUtils.hasText(f.q())) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("ice")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("city")), like)));
        }
        if (StringUtils.hasText(f.sector())) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sector"), f.sector()));
        }
        if (StringUtils.hasText(f.city())) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("city"), f.city()));
        }
        if (f.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.ownerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
        }
        if (StringUtils.hasText(f.tag())) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("tags", JoinType.INNER), f.tag());
            });
        }
        return spec;
    }

    private Map<Long, Long> contactCounts(List<Company> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return contacts.countActiveByCompanyIds(list.stream().map(Company::getId).toList()).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    @Transactional(readOnly = true)
    public Company360 get360(Long id) {
        Company company = find(id);
        List<ContactDto> list = contacts.findByCompany_IdAndArchivedFalseOrderByLastNameAsc(id).stream()
                .map(ContactDto::of).toList();
        var opps = opportunities.findByCompany_IdOrderByCreatedAtDesc(id).stream()
                .map(ma.crm.dto.SalesDtos.OpportunityDto::of).toList();
        var quoteList = quotes.findByCompany_IdOrderByDateDesc(id).stream()
                .map(ma.crm.dto.QuoteDtos.QuoteSummary::of).toList();
        var invoiceList = invoices.findByCompany_IdOrderByIssueDateDesc(id).stream()
                .map(ma.crm.dto.QuoteDtos.InvoiceDto::of).toList();
        var ticketList = tickets.findByCompany_IdOrderByOpenedAtDesc(id).stream()
                .map(t -> ma.crm.dto.TicketDtos.TicketDto.of(t, true, false)).toList();
        var history = activities.findAll((root, q, cb) -> cb.equal(root.get("company").get("id"), id),
                        PageRequest.of(0, 50, Sort.by("dueAt").descending()))
                .map(ma.crm.dto.ActivityDtos.ActivityDto::of).getContent();
        java.math.BigDecimal revenue = invoiceList.stream()
                .filter(i -> i.status() != ma.crm.domain.Enums.InvoiceStatus.ANNULEE)
                .map(ma.crm.dto.QuoteDtos.InvoiceDto::totalHT).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal pipeline = opps.stream().filter(o -> !o.stage().isClosed())
                .map(ma.crm.dto.SalesDtos.OpportunityDto::amount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return new Company360(CompanyDto.of(company, list.size()), list, opps, quoteList, invoiceList, ticketList,
                history, revenue, pipeline);
    }

    @Transactional(readOnly = true)
    public FilterOptions filterOptions() {
        return new FilterOptions(companies.findDistinctSectors(), companies.findDistinctCities());
    }

    public CompanyDto create(CompanyRequest req) {
        Company company = new Company();
        apply(company, req);
        if (company.getOwner() == null) {
            company.setOwner(users.getReferenceById(CurrentUser.id()));
        }
        companies.save(company);
        audit.log(AuditService.CREATE, "Company", company.getId(), company.getName());
        return CompanyDto.of(company, 0);
    }

    public CompanyDto update(Long id, CompanyRequest req) {
        Company company = find(id);
        apply(company, req);
        audit.log(AuditService.UPDATE, "Company", id, company.getName());
        return CompanyDto.of(company, contactCounts(List.of(company)).getOrDefault(id, 0L));
    }

    public void setArchived(Long id, boolean archived) {
        Company company = find(id);
        company.setArchived(archived);
        audit.log(archived ? AuditService.ARCHIVE : AuditService.RESTORE, "Company", id, company.getName());
    }

    public void delete(Long id) {
        Company company = find(id);
        if (!contacts.findByCompany_IdAndArchivedFalseOrderByLastNameAsc(id).isEmpty()) {
            throw BusinessException.conflict("Cette entreprise a encore des contacts : archivez-la plutôt");
        }
        companies.delete(company);
        audit.log(AuditService.DELETE, "Company", id, company.getName());
    }

    private void apply(Company c, CompanyRequest req) {
        String ice = StringUtils.hasText(req.ice()) ? req.ice().trim() : null;
        if (ice != null) {
            companies.findByIce(ice).filter(other -> !other.getId().equals(c.getId())).ifPresent(other -> {
                throw BusinessException.conflict("L'ICE " + ice + " est déjà utilisé par " + other.getName());
            });
        }
        c.setName(req.name().trim());
        c.setSector(blankToNull(req.sector()));
        c.setIce(ice);
        c.setCity(blankToNull(req.city()));
        c.setAddress(blankToNull(req.address()));
        c.setPhone(blankToNull(req.phone()));
        c.setEmail(blankToNull(req.email()));
        c.setWebsite(blankToNull(req.website()));
        c.setStatus(req.status() == null ? CompanyStatus.PROSPECT : req.status());
        c.setTags(cleanTags(req.tags()));
        if (req.ownerId() != null) {
            c.setOwner(users.findById(req.ownerId()).orElseThrow(() -> NotFoundException.of("Utilisateur", req.ownerId())));
        }
    }

    // ---- Import / export ----

    @Transactional
    public byte[] exportCsv(Filter filter) {
        return export(filter, false);
    }

    @Transactional
    public byte[] exportXlsx(Filter filter) {
        return export(filter, true);
    }

    private byte[] export(Filter filter, boolean excel) {
        List<Company> list = companies.findAll(spec(filter), Sort.by("name"));
        audit.log(AuditService.EXPORT, "Company", null, list.size() + " entreprises exportées (" + (excel ? "Excel" : "CSV") + ")");
        java.util.function.Function<Company, Object[]> row = c -> new Object[]{c.getName(), c.getSector(), c.getIce(),
                c.getCity(), c.getAddress(), c.getPhone(), c.getEmail(), c.getWebsite(), c.getStatus().name(),
                CsvSupport.joinTags(c.getTags()), c.getOwner() == null ? null : c.getOwner().getEmail()};
        return excel ? ExcelSupport.write("Entreprises", CSV_HEADERS, list, row) : CsvSupport.write(CSV_HEADERS, list, row);
    }

    /** Import idempotent : une ligne met à jour l'entreprise de même ICE, sinon de même raison sociale. */
    public ImportResult importCsv(InputStream input) {
        List<CSVRecord> records = CsvSupport.read(input);
        int created = 0;
        int updated = 0;
        List<String> errors = new ArrayList<>();
        User currentUser = users.getReferenceById(CurrentUser.id());
        for (CSVRecord r : records) {
            long line = r.getRecordNumber() + 1;
            String name = CsvSupport.get(r, "raisonSociale");
            if (name == null) {
                errors.add("Ligne " + line + " : raison sociale manquante");
                continue;
            }
            String ice = CsvSupport.get(r, "ice");
            if (ice != null && !ice.matches("\\d{15}")) {
                errors.add("Ligne " + line + " : ICE invalide (15 chiffres attendus)");
                continue;
            }
            Optional<Company> existing = ice != null ? companies.findByIce(ice) : companies.findFirstByNameIgnoreCase(name);
            Company c = existing.orElseGet(Company::new);
            c.setName(name);
            c.setSector(CsvSupport.get(r, "secteur"));
            c.setIce(ice);
            c.setCity(CsvSupport.get(r, "ville"));
            c.setAddress(CsvSupport.get(r, "adresse"));
            c.setPhone(CsvSupport.get(r, "telephone"));
            c.setEmail(CsvSupport.get(r, "email"));
            c.setWebsite(CsvSupport.get(r, "siteWeb"));
            String status = CsvSupport.get(r, "statut");
            try {
                c.setStatus(status == null ? CompanyStatus.PROSPECT : CompanyStatus.valueOf(status.toUpperCase()));
            } catch (IllegalArgumentException e) {
                errors.add("Ligne " + line + " : statut inconnu « " + status + " »");
                continue;
            }
            c.setTags(CsvSupport.splitTags(CsvSupport.get(r, "tags")));
            String ownerEmail = CsvSupport.get(r, "commercial");
            c.setOwner(ownerEmail == null ? (c.getOwner() == null ? currentUser : c.getOwner())
                    : users.findByEmailIgnoreCase(ownerEmail).orElse(currentUser));
            if (existing.isPresent()) {
                updated++;
            } else {
                companies.save(c);
                created++;
            }
        }
        audit.log(AuditService.IMPORT, "Company", null, created + " créées, " + updated + " mises à jour");
        return new ImportResult(created, updated, errors);
    }

    Company find(Long id) {
        return companies.findById(id).orElseThrow(() -> NotFoundException.of("Entreprise", id));
    }

    static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    static Set<String> cleanTags(Set<String> tags) {
        if (tags == null) {
            return new HashSet<>();
        }
        return tags.stream().filter(StringUtils::hasText).map(String::trim)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
