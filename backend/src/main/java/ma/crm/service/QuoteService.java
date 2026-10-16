package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.OpportunityStage;
import ma.crm.domain.Enums.QuoteStatus;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.QuoteDtos.*;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.*;
import ma.crm.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

import static ma.crm.service.CompanyService.blankToNull;

/**
 * Module 5 : devis. Cycle de vie : BROUILLON → (EN_VALIDATION → VALIDE si montant ≥ seuil)
 * → ENVOYE → ACCEPTE (commande + facture) ou REFUSE.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QuoteService {

    private final QuoteRepository quotes;
    private final OpportunityRepository opportunities;
    private final ContactRepository contacts;
    private final ProductRepository products;
    private final UserRepository users;
    private final InvoiceRepository invoices;
    private final InvoiceService invoiceService;
    private final SequenceService sequences;
    private final SettingsService settings;
    private final PdfService pdf;
    private final MailService mail;
    private final NotificationService notifications;
    private final HistoryService history;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public record Filter(String q, QuoteStatus status, Long companyId, Long opportunityId, Long ownerId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<QuoteSummary> search(Filter f, int page, int size) {
        Specification<Quote> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(f.q())) {
            String like = "%" + f.q().trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("number")), like),
                    cb.like(cb.lower(root.get("company").get("name")), like)));
        }
        if (f.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), f.status()));
        }
        if (f.companyId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("id"), f.companyId()));
        }
        if (f.opportunityId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("opportunity").get("id"), f.opportunityId()));
        }
        if (f.ownerId() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("owner").get("id"), f.ownerId()));
        }
        return PageResponse.of(quotes.findAll(spec, PageRequest.of(page, size, Sort.by("date", "id").descending())),
                QuoteSummary::of);
    }

    @Transactional(readOnly = true)
    public QuoteDto get(Long id) {
        return dto(find(id));
    }

    public QuoteDto create(QuoteRequest req) {
        Opportunity opp = opportunities.findById(req.opportunityId())
                .orElseThrow(() -> NotFoundException.of("Opportunité", req.opportunityId()));
        if (opp.getStage().isClosed()) {
            throw new BusinessException("Impossible de créer un devis sur une opportunité clôturée");
        }
        Quote q = new Quote();
        q.setNumber(sequences.next("DEV"));
        q.setOpportunity(opp);
        q.setCompany(opp.getCompany());
        q.setOwner(opp.getOwner() != null ? opp.getOwner() : users.getReferenceById(CurrentUser.id()));
        apply(q, req);
        quotes.save(q);
        audit.log(AuditService.CREATE, "Quote", q.getId(), q.getNumber());
        return dto(q);
    }

    public QuoteDto update(Long id, QuoteRequest req) {
        Quote q = find(id);
        CurrentUser.checkOwnership(q.getOwner());
        if (q.getStatus() != QuoteStatus.BROUILLON && q.getStatus() != QuoteStatus.VALIDE) {
            throw new BusinessException("Seul un devis brouillon ou validé est modifiable ; dupliquez-le pour une nouvelle version");
        }
        if (!q.getOpportunity().getId().equals(req.opportunityId())) {
            throw new BusinessException("L'opportunité d'un devis ne peut pas être changée");
        }
        apply(q, req);
        // Toute modification après validation impose une nouvelle validation.
        q.setStatus(QuoteStatus.BROUILLON);
        q.setApprovedBy(null);
        q.setApprovedAt(null);
        audit.log(AuditService.UPDATE, "Quote", id, q.getNumber());
        return dto(q);
    }

    /** Nouvelle version d'un devis envoyé ou refusé. */
    public QuoteDto duplicate(Long id) {
        Quote src = find(id);
        Quote q = new Quote();
        q.setNumber(sequences.next("DEV"));
        q.setOpportunity(src.getOpportunity());
        q.setCompany(src.getCompany());
        q.setContact(src.getContact());
        q.setOwner(src.getOwner());
        q.setDate(LocalDate.now());
        q.setValidUntil(LocalDate.now().plusDays(settings.getInt(SettingsService.QUOTE_VALIDITY_DAYS)));
        q.setNotes(src.getNotes());
        int pos = 0;
        for (QuoteLine l : src.getLines()) {
            QuoteLine copy = new QuoteLine();
            copy.setQuote(q);
            copy.setPosition(pos++);
            copy.setProduct(l.getProduct());
            copy.setDescription(l.getDescription());
            copy.setQuantity(l.getQuantity());
            copy.setUnitPrice(l.getUnitPrice());
            copy.setDiscount(l.getDiscount());
            copy.setVatRate(l.getVatRate());
            q.getLines().add(copy);
        }
        recompute(q);
        quotes.save(q);
        audit.log(AuditService.CREATE, "Quote", q.getId(), q.getNumber() + " (copie de " + src.getNumber() + ")");
        return dto(q);
    }

    public void delete(Long id) {
        Quote q = find(id);
        CurrentUser.checkOwnership(q.getOwner());
        if (q.getStatus() != QuoteStatus.BROUILLON) {
            throw new BusinessException("Seul un devis brouillon peut être supprimé");
        }
        quotes.delete(q);
        audit.log(AuditService.DELETE, "Quote", id, q.getNumber());
    }

    /**
     * Envoie le devis par e-mail avec le PDF en pièce jointe. Au-dessus du seuil paramétré, le devis
     * part d'abord en validation chez le directeur commercial.
     */
    public QuoteDto send(Long id, SendQuoteRequest req) {
        Quote q = find(id);
        CurrentUser.checkOwnership(q.getOwner());
        if (q.getStatus() == QuoteStatus.EN_VALIDATION) {
            throw new BusinessException("Ce devis attend la validation du directeur commercial");
        }
        if (q.getStatus() == QuoteStatus.ACCEPTE || q.getStatus() == QuoteStatus.REFUSE) {
            throw new BusinessException("Ce devis est clôturé");
        }
        if (needsApproval(q)) {
            q.setStatus(QuoteStatus.EN_VALIDATION);
            notifications.notifyAll(users.findByActiveTrueAndRole_CodeIn(List.of(Role.DIRECTEUR)), "QUOTE_APPROVAL",
                    "Devis à valider : " + q.getNumber(),
                    q.getCompany().getName() + " · " + PdfService.money(q.getTotalTTC()), "/quotes/" + q.getId());
            history.record("Devis " + q.getNumber() + " soumis à validation", null, links(q));
            audit.log(AuditService.UPDATE, "Quote", id, "soumis à validation");
            return dto(q);
        }
        String to = StringUtils.hasText(req.to()) ? req.to().trim()
                : q.getContact() == null ? null : q.getContact().getEmail();
        if (!StringUtils.hasText(to)) {
            throw new BusinessException("Aucune adresse e-mail : renseignez un destinataire");
        }
        if (q.getPublicToken() == null) {
            byte[] bytes = new byte[24];
            random.nextBytes(bytes);
            q.setPublicToken(HexFormat.of().formatHex(bytes));
        }
        String body = (StringUtils.hasText(req.message()) ? req.message().trim() : defaultMessage(q));
        mail.send(to, "Devis " + q.getNumber() + " — " + settings.get(SettingsService.COMPANY_NAME), body,
                new MailService.Attachment(q.getNumber() + ".pdf", "application/pdf", pdf.quote(q)));
        q.setStatus(QuoteStatus.ENVOYE);
        q.setSentAt(Instant.now());
        Opportunity opp = q.getOpportunity();
        if (opp.getStage().ordinal() < OpportunityStage.PROPOSITION.ordinal()) {
            opp.setStage(OpportunityStage.PROPOSITION);
            opp.setProbability(OpportunityStage.PROPOSITION.defaultProbability);
        }
        history.record("Devis " + q.getNumber() + " envoyé à " + to, PdfService.money(q.getTotalTTC()), links(q));
        audit.log(AuditService.UPDATE, "Quote", id, "envoyé à " + to);
        return dto(q);
    }

    public QuoteDto approve(Long id) {
        Quote q = find(id);
        requireStatus(q, QuoteStatus.EN_VALIDATION);
        q.setStatus(QuoteStatus.VALIDE);
        q.setApprovedBy(users.getReferenceById(CurrentUser.id()));
        q.setApprovedAt(Instant.now());
        notifications.notify(q.getOwner(), "QUOTE_APPROVED", "Devis validé : " + q.getNumber(),
                "Vous pouvez maintenant l'envoyer au client.", "/quotes/" + q.getId(), true);
        history.record("Devis " + q.getNumber() + " validé par la direction", null, links(q));
        audit.log(AuditService.UPDATE, "Quote", id, "validé");
        return dto(q);
    }

    public QuoteDto rejectApproval(Long id, DecisionRequest req) {
        Quote q = find(id);
        requireStatus(q, QuoteStatus.EN_VALIDATION);
        q.setStatus(QuoteStatus.BROUILLON);
        String comment = req == null || !StringUtils.hasText(req.comment()) ? "" : req.comment().trim();
        notifications.notify(q.getOwner(), "QUOTE_REJECTED", "Devis à revoir : " + q.getNumber(),
                comment.isEmpty() ? "La direction a demandé des modifications." : comment, "/quotes/" + q.getId(), true);
        audit.log(AuditService.UPDATE, "Quote", id, "validation refusée " + comment);
        return dto(q);
    }

    /** Le client accepte : le devis devient une commande, l'opportunité est gagnée et la facture émise. */
    public QuoteDto accept(Long id) {
        Quote q = find(id);
        requireStatus(q, QuoteStatus.ENVOYE);
        q.setStatus(QuoteStatus.ACCEPTE);
        q.setRespondedAt(Instant.now());
        Opportunity opp = q.getOpportunity();
        opp.setStage(OpportunityStage.GAGNEE);
        opp.setProbability(100);
        opp.setAmount(q.getTotalHT());
        opp.setClosedAt(Instant.now());
        opp.setLossReason(null);
        q.getCompany().setStatus(CompanyStatus.CLIENT);
        Invoice invoice = invoiceService.createFromQuote(q);
        history.record("Devis " + q.getNumber() + " accepté — facture " + invoice.getNumber(),
                PdfService.money(q.getTotalTTC()), links(q));
        if (q.getOwner() != null && !Objects.equals(q.getOwner().getId(), CurrentUser.id())) {
            notifications.notify(q.getOwner(), "QUOTE_ACCEPTED", "Devis accepté : " + q.getNumber(),
                    q.getCompany().getName() + " · " + PdfService.money(q.getTotalTTC()), "/quotes/" + q.getId(), false);
        }
        audit.log(AuditService.UPDATE, "Quote", id, "accepté");
        return dto(q);
    }

    public QuoteDto refuse(Long id, DecisionRequest req) {
        Quote q = find(id);
        requireStatus(q, QuoteStatus.ENVOYE);
        q.setStatus(QuoteStatus.REFUSE);
        q.setRespondedAt(Instant.now());
        String comment = req == null ? null : blankToNull(req.comment());
        history.record("Devis " + q.getNumber() + " refusé", comment, links(q));
        if (q.getOwner() != null && !Objects.equals(q.getOwner().getId(), CurrentUser.id())) {
            notifications.notify(q.getOwner(), "QUOTE_REFUSED", "Devis refusé : " + q.getNumber(),
                    comment == null ? q.getCompany().getName() : comment, "/quotes/" + q.getId(), false);
        }
        audit.log(AuditService.UPDATE, "Quote", id, "refusé");
        return dto(q);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        return pdf.quote(find(id));
    }

    @Transactional(readOnly = true)
    public Quote findByPublicToken(String token) {
        return quotes.findByPublicToken(token).orElseThrow(() -> new NotFoundException("Devis introuvable"));
    }

    @Transactional(readOnly = true)
    public byte[] publicPdf(String token) {
        return pdf.quote(findByPublicToken(token));
    }

    // ---- Interne ----

    private void apply(Quote q, QuoteRequest req) {
        q.setDate(req.date() != null ? req.date() : LocalDate.now());
        q.setValidUntil(req.validUntil() != null ? req.validUntil()
                : q.getDate().plusDays(settings.getInt(SettingsService.QUOTE_VALIDITY_DAYS)));
        q.setNotes(blankToNull(req.notes()));
        if (req.contactId() != null) {
            Contact c = contacts.findById(req.contactId()).orElseThrow(() -> NotFoundException.of("Contact", req.contactId()));
            if (c.getCompany() == null || !c.getCompany().getId().equals(q.getCompany().getId())) {
                throw new BusinessException("Le destinataire doit être un contact de " + q.getCompany().getName());
            }
            q.setContact(c);
        } else {
            q.setContact(q.getOpportunity().getContact());
        }
        q.getLines().clear();
        int pos = 0;
        for (QuoteLineRequest lr : req.lines()) {
            QuoteLine l = new QuoteLine();
            l.setQuote(q);
            l.setPosition(pos++);
            l.setProduct(lr.productId() == null ? null
                    : products.findById(lr.productId()).orElseThrow(() -> NotFoundException.of("Produit", lr.productId())));
            l.setDescription(lr.description().trim());
            l.setQuantity(lr.quantity());
            l.setUnitPrice(lr.unitPrice());
            l.setDiscount(lr.discount() == null ? BigDecimal.ZERO : lr.discount());
            l.setVatRate(lr.vatRate());
            q.getLines().add(l);
        }
        recompute(q);
    }

    static void recompute(Quote q) {
        BigDecimal ht = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (QuoteLine l : q.getLines()) {
            ht = ht.add(l.getTotalHT());
            vat = vat.add(l.getTotalVAT());
        }
        q.setTotalHT(ht);
        q.setTotalVAT(vat);
        q.setTotalTTC(ht.add(vat));
    }

    private boolean needsApproval(Quote q) {
        return q.getApprovedBy() == null
                && q.getTotalTTC().compareTo(settings.getDecimal(SettingsService.QUOTE_APPROVAL_THRESHOLD)) >= 0;
    }

    private String defaultMessage(Quote q) {
        String name = q.getContact() == null ? "Madame, Monsieur" : "Bonjour " + q.getContact().getFirstName();
        return name + ",\n\nVeuillez trouver ci-joint notre devis " + q.getNumber() + " d'un montant de "
                + PdfService.money(q.getTotalTTC()) + " TTC, valable jusqu'au "
                + (q.getValidUntil() == null ? "" : q.getValidUntil().format(PdfService.DATE)) + ".\n\n"
                + "Nous restons à votre disposition pour toute question.\n\nCordialement,\n"
                + (q.getOwner() == null ? "" : q.getOwner().getFullName() + "\n") + settings.get(SettingsService.COMPANY_NAME);
    }

    private static void requireStatus(Quote q, QuoteStatus expected) {
        if (q.getStatus() != expected) {
            throw new BusinessException("Action impossible : le devis est au statut " + q.getStatus());
        }
    }

    private static HistoryService.Links links(Quote q) {
        return new HistoryService.Links(q.getCompany(), q.getContact(), q.getOpportunity(), null);
    }

    QuoteDto dto(Quote q) {
        return QuoteDto.of(q, needsApproval(q), invoices.findByQuote_Id(q.getId()).orElse(null));
    }

    Quote find(Long id) {
        return quotes.findById(id).orElseThrow(() -> NotFoundException.of("Devis", id));
    }
}
