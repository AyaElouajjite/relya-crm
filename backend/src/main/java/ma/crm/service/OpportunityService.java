package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.OpportunityStage;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.SalesDtos.*;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.CompanyRepository;
import ma.crm.repository.ContactRepository;
import ma.crm.repository.OpportunityRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static ma.crm.service.CompanyService.blankToNull;

/** Module 4 : pipeline des opportunités. */
@Service
@RequiredArgsConstructor
@Transactional
public class OpportunityService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "title", "amount", "expectedCloseDate", "stage", "probability");

    private final OpportunityRepository opportunities;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final UserRepository users;
    private final HistoryService history;
    private final AuditService audit;

    public record Filter(String q, OpportunityStage stage, Long ownerId, Long companyId, boolean openOnly,
                         LocalDate closeFrom, LocalDate closeTo) {
    }

    @Transactional(readOnly = true)
    public PageResponse<OpportunityDto> search(Filter f, int page, int size, String sort, String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "createdAt";
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.fromOptionalString(dir)
                .orElse(Sort.Direction.DESC), sortField));
        return PageResponse.of(opportunities.findAll(spec(f), pageable), OpportunityDto::of);
    }

    /** Vue Kanban : une colonne par étape avec effectif, montant total et montant pondéré. */
    @Transactional(readOnly = true)
    public List<PipelineColumn> pipeline(Filter f) {
        Map<OpportunityStage, List<Opportunity>> byStage = new EnumMap<>(OpportunityStage.class);
        for (OpportunityStage s : OpportunityStage.values()) {
            byStage.put(s, new ArrayList<>());
        }
        opportunities.findAll(spec(f), Sort.by("expectedCloseDate").ascending())
                .forEach(o -> byStage.get(o.getStage()).add(o));
        return byStage.entrySet().stream().map(e -> {
            List<OpportunityDto> items = e.getValue().stream().map(OpportunityDto::of).toList();
            BigDecimal total = items.stream().map(OpportunityDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal weighted = items.stream().map(OpportunityDto::weightedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new PipelineColumn(e.getKey(), items.size(), total, weighted, items);
        }).toList();
    }

    private Specification<Opportunity> spec(Filter f) {
        Specification<Opportunity> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(f.q())) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("company").get("name")), like)));
        }
        if (f.stage() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("stage"), f.stage()));
        }
        if (f.openOnly()) {
            spec = spec.and((root, query, cb) -> root.get("stage").in(OpportunityStage.GAGNEE, OpportunityStage.PERDUE).not());
        }
        if (f.ownerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
        }
        if (f.companyId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("id"), f.companyId()));
        }
        if (f.closeFrom() != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("expectedCloseDate"), f.closeFrom()));
        }
        if (f.closeTo() != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("expectedCloseDate"), f.closeTo()));
        }
        return spec;
    }

    @Transactional(readOnly = true)
    public OpportunityDto get(Long id) {
        return OpportunityDto.of(find(id));
    }

    public OpportunityDto create(OpportunityRequest req) {
        Opportunity o = new Opportunity();
        apply(o, req);
        if (o.getOwner() == null) {
            o.setOwner(users.getReferenceById(CurrentUser.id()));
        }
        OpportunityStage stage = req.stage() == null ? OpportunityStage.PROSPECTION : req.stage();
        applyStage(o, stage, req.lossReason(), req.lossComment());
        if (req.probability() != null) {
            o.setProbability(req.probability());
        }
        o.setLastActivityAt(Instant.now());
        opportunities.save(o);
        history.record("Opportunité créée : " + o.getTitle(), null, HistoryService.Links.opportunity(o));
        audit.log(AuditService.CREATE, "Opportunity", o.getId(), o.getTitle());
        return OpportunityDto.of(o);
    }

    public OpportunityDto update(Long id, OpportunityRequest req) {
        Opportunity o = find(id);
        CurrentUser.checkOwnership(o.getOwner());
        apply(o, req);
        if (req.stage() != null && req.stage() != o.getStage()) {
            changeStage(o, req.stage(), req.lossReason(), req.lossComment());
        } else if (o.getStage() == OpportunityStage.PERDUE) {
            applyStage(o, OpportunityStage.PERDUE, req.lossReason(), req.lossComment());
        }
        if (req.probability() != null && !o.getStage().isClosed()) {
            o.setProbability(req.probability());
        }
        audit.log(AuditService.UPDATE, "Opportunity", id, o.getTitle());
        return OpportunityDto.of(o);
    }

    /** Glisser-déposer dans le Kanban. */
    public OpportunityDto moveStage(Long id, StageChangeRequest req) {
        Opportunity o = find(id);
        CurrentUser.checkOwnership(o.getOwner());
        if (req.stage() != o.getStage()) {
            changeStage(o, req.stage(), req.lossReason(), req.lossComment());
            audit.log(AuditService.UPDATE, "Opportunity", id, "étape " + req.stage());
        }
        return OpportunityDto.of(o);
    }

    public void delete(Long id) {
        Opportunity o = find(id);
        CurrentUser.checkOwnership(o.getOwner());
        opportunities.delete(o);
        audit.log(AuditService.DELETE, "Opportunity", id, o.getTitle());
    }

    private void changeStage(Opportunity o, OpportunityStage stage, Enums.LossReason reason, String comment) {
        OpportunityStage previous = o.getStage();
        applyStage(o, stage, reason, comment);
        history.record("Étape : " + label(previous) + " → " + label(stage),
                stage == OpportunityStage.PERDUE ? "Motif : " + reason + (comment == null ? "" : " — " + comment) : null,
                HistoryService.Links.opportunity(o));
        if (stage == OpportunityStage.GAGNEE && o.getCompany().getStatus() != CompanyStatus.CLIENT) {
            o.getCompany().setStatus(CompanyStatus.CLIENT);
        }
    }

    /** Règles d'étape : probabilité par défaut, date de clôture effective, motif de perte obligatoire. */
    private void applyStage(Opportunity o, OpportunityStage stage, Enums.LossReason reason, String comment) {
        if (stage == OpportunityStage.PERDUE && reason == null && o.getLossReason() == null) {
            throw new BusinessException("Le motif de perte est obligatoire pour clôturer une opportunité perdue");
        }
        o.setStage(stage);
        o.setProbability(stage.defaultProbability);
        if (stage.isClosed()) {
            if (o.getClosedAt() == null) {
                o.setClosedAt(Instant.now());
            }
        } else {
            o.setClosedAt(null);
        }
        if (stage == OpportunityStage.PERDUE) {
            if (reason != null) {
                o.setLossReason(reason);
                o.setLossComment(blankToNull(comment));
            }
        } else {
            o.setLossReason(null);
            o.setLossComment(null);
        }
    }

    private void apply(Opportunity o, OpportunityRequest req) {
        o.setTitle(req.title().trim());
        o.setAmount(req.amount());
        o.setExpectedCloseDate(req.expectedCloseDate());
        o.setDescription(blankToNull(req.description()));
        o.setCompany(companies.findById(req.companyId()).orElseThrow(() -> NotFoundException.of("Entreprise", req.companyId())));
        if (req.contactId() != null) {
            Contact c = contacts.findById(req.contactId()).orElseThrow(() -> NotFoundException.of("Contact", req.contactId()));
            if (c.getCompany() != null && !c.getCompany().getId().equals(req.companyId())) {
                throw new BusinessException("Le contact n'appartient pas à cette entreprise");
            }
            o.setContact(c);
        } else {
            o.setContact(null);
        }
        if (req.ownerId() != null) {
            o.setOwner(users.findById(req.ownerId()).orElseThrow(() -> NotFoundException.of("Utilisateur", req.ownerId())));
        }
    }

    static String label(OpportunityStage s) {
        return switch (s) {
            case PROSPECTION -> "Prospection";
            case QUALIFICATION -> "Qualification";
            case PROPOSITION -> "Proposition";
            case NEGOCIATION -> "Négociation";
            case GAGNEE -> "Gagnée";
            case PERDUE -> "Perdue";
        };
    }

    Opportunity find(Long id) {
        return opportunities.findById(id).orElseThrow(() -> NotFoundException.of("Opportunité", id));
    }
}
