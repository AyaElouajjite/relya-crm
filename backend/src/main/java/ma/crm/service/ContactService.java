package ma.crm.service;

import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Company;
import ma.crm.domain.Contact;
import ma.crm.domain.User;
import ma.crm.dto.CommonDtos.ImportResult;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.ContactDtos.ContactDto;
import ma.crm.dto.ContactDtos.ContactRequest;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.CompanyRepository;
import ma.crm.repository.ContactRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.CurrentUser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ma.crm.service.CompanyService.blankToNull;
import static ma.crm.service.CompanyService.cleanTags;

@Service
@RequiredArgsConstructor
@Transactional
public class ContactService {

    static final String[] CSV_HEADERS = {"prenom", "nom", "email", "telephone", "poste", "entreprise", "tags"};
    private static final Set<String> SORTABLE = Set.of("lastName", "firstName", "email", "position", "createdAt");

    private final ContactRepository contacts;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final AuditService audit;

    /** Segmentation : tags, secteur, ville et statut de l'entreprise du contact. */
    public record Filter(String q, Long companyId, String tag, Long ownerId, boolean archived,
                         String sector, String city, ma.crm.domain.CompanyStatus companyStatus) {
        public Filter(String q, Long companyId, String tag, Long ownerId, boolean archived) {
            this(q, companyId, tag, ownerId, archived, null, null, null);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ContactDto> search(Filter filter, int page, int size, String sort, String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "lastName";
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromOptionalString(dir)
                .orElse(Sort.Direction.ASC), sortField));
        return PageResponse.of(contacts.findAll(spec(filter), pageable), ContactDto::of);
    }

    private Specification<Contact> spec(Filter f) {
        Specification<Contact> spec = (root, query, cb) -> cb.equal(root.get("archived"), f.archived());
        if (StringUtils.hasText(f.q())) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> {
                var company = root.join("company", JoinType.LEFT);
                return cb.or(
                        cb.like(cb.lower(root.get("firstName")), like),
                        cb.like(cb.lower(root.get("lastName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("phone")), like),
                        cb.like(cb.lower(company.get("name")), like));
            });
        }
        if (f.companyId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("id"), f.companyId()));
        }
        if (f.ownerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
        }
        if (StringUtils.hasText(f.sector())) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("sector"), f.sector()));
        }
        if (StringUtils.hasText(f.city())) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("city"), f.city()));
        }
        if (f.companyStatus() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("status"), f.companyStatus()));
        }
        if (StringUtils.hasText(f.tag())) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("tags", JoinType.INNER), f.tag());
            });
        }
        return spec;
    }

    @Transactional(readOnly = true)
    public ContactDto get(Long id) {
        return ContactDto.of(find(id), users.findByContact_Id(id).isPresent());
    }

    public ContactDto create(ContactRequest req) {
        Contact contact = new Contact();
        apply(contact, req);
        if (contact.getOwner() == null) {
            contact.setOwner(users.getReferenceById(CurrentUser.id()));
        }
        contacts.save(contact);
        audit.log(AuditService.CREATE, "Contact", contact.getId(), contact.getFullName());
        return ContactDto.of(contact);
    }

    public ContactDto update(Long id, ContactRequest req) {
        Contact contact = find(id);
        apply(contact, req);
        audit.log(AuditService.UPDATE, "Contact", id, contact.getFullName());
        return ContactDto.of(contact);
    }

    public void setArchived(Long id, boolean archived) {
        Contact contact = find(id);
        contact.setArchived(archived);
        audit.log(archived ? AuditService.ARCHIVE : AuditService.RESTORE, "Contact", id, contact.getFullName());
    }

    public void delete(Long id) {
        Contact contact = find(id);
        contacts.delete(contact);
        audit.log(AuditService.DELETE, "Contact", id, contact.getFullName());
    }

    private void apply(Contact c, ContactRequest req) {
        c.setFirstName(req.firstName().trim());
        c.setLastName(req.lastName().trim());
        c.setEmail(blankToNull(req.email()));
        c.setPhone(blankToNull(req.phone()));
        c.setPosition(blankToNull(req.position()));
        c.setTags(cleanTags(req.tags()));
        // Loi 09-08 : on horodate le recueil du consentement.
        if (req.marketingConsent() && !c.isMarketingConsent()) {
            c.setConsentDate(java.time.Instant.now());
        } else if (!req.marketingConsent()) {
            c.setConsentDate(null);
        }
        c.setMarketingConsent(req.marketingConsent());
        c.setCompany(req.companyId() == null ? null
                : companies.findById(req.companyId()).orElseThrow(() -> NotFoundException.of("Entreprise", req.companyId())));
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
        List<Contact> list = contacts.findAll(spec(filter), Sort.by("lastName", "firstName"));
        audit.log(AuditService.EXPORT, "Contact", null, list.size() + " contacts exportés (" + (excel ? "Excel" : "CSV") + ")");
        java.util.function.Function<Contact, Object[]> row = c -> new Object[]{c.getFirstName(), c.getLastName(),
                c.getEmail(), c.getPhone(), c.getPosition(), c.getCompany() == null ? null : c.getCompany().getName(),
                CsvSupport.joinTags(c.getTags())};
        return excel ? ExcelSupport.write("Contacts", CSV_HEADERS, list, row) : CsvSupport.write(CSV_HEADERS, list, row);
    }

    /** Les contacts sont rattachés à l'entreprise portant la même raison sociale, créée si besoin. */
    public ImportResult importCsv(InputStream input) {
        List<CSVRecord> records = CsvSupport.read(input);
        int created = 0;
        List<String> errors = new ArrayList<>();
        User currentUser = users.getReferenceById(CurrentUser.id());
        for (CSVRecord r : records) {
            long line = r.getRecordNumber() + 1;
            String firstName = CsvSupport.get(r, "prenom");
            String lastName = CsvSupport.get(r, "nom");
            if (firstName == null || lastName == null) {
                errors.add("Ligne " + line + " : prénom et nom obligatoires");
                continue;
            }
            Contact c = new Contact();
            c.setFirstName(firstName);
            c.setLastName(lastName);
            c.setEmail(CsvSupport.get(r, "email"));
            c.setPhone(CsvSupport.get(r, "telephone"));
            c.setPosition(CsvSupport.get(r, "poste"));
            c.setTags(CsvSupport.splitTags(CsvSupport.get(r, "tags")));
            c.setOwner(currentUser);
            String companyName = CsvSupport.get(r, "entreprise");
            if (companyName != null) {
                c.setCompany(companies.findFirstByNameIgnoreCase(companyName).orElseGet(() -> {
                    Company company = new Company();
                    company.setName(companyName);
                    company.setOwner(currentUser);
                    return companies.save(company);
                }));
            }
            contacts.save(c);
            created++;
        }
        audit.log(AuditService.IMPORT, "Contact", null, created + " contacts importés");
        return new ImportResult(created, 0, errors);
    }

    Contact find(Long id) {
        return contacts.findById(id).orElseThrow(() -> NotFoundException.of("Contact", id));
    }
}
