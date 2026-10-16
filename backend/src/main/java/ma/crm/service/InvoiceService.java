package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.InvoiceStatus;
import ma.crm.domain.Invoice;
import ma.crm.domain.Quote;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.QuoteDtos.InvoiceDto;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.InvoiceRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final SequenceService sequences;
    private final SettingsService settings;
    private final PdfService pdf;
    private final AuditService audit;

    /** Facture émise automatiquement à l'acceptation d'un devis. */
    public Invoice createFromQuote(Quote q) {
        return invoices.findByQuote_Id(q.getId()).orElseGet(() -> {
            Invoice i = new Invoice();
            i.setNumber(sequences.next("FAC"));
            i.setQuote(q);
            i.setCompany(q.getCompany());
            i.setIssueDate(LocalDate.now());
            i.setDueDate(LocalDate.now().plusDays(settings.getInt(SettingsService.INVOICE_PAYMENT_DAYS)));
            i.setTotalHT(q.getTotalHT());
            i.setTotalVAT(q.getTotalVAT());
            i.setTotalTTC(q.getTotalTTC());
            invoices.save(i);
            audit.log(AuditService.CREATE, "Invoice", i.getId(), i.getNumber() + " depuis " + q.getNumber());
            return i;
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceDto> search(String q, InvoiceStatus status, Long companyId, int page, int size) {
        Specification<Invoice> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(q)) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("number")), like),
                    cb.like(cb.lower(root.get("company").get("name")), like)));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (companyId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("company").get("id"), companyId));
        }
        return PageResponse.of(invoices.findAll(spec, PageRequest.of(page, size, Sort.by("issueDate").descending())),
                InvoiceDto::of);
    }

    public InvoiceDto setStatus(Long id, InvoiceStatus status) {
        Invoice i = find(id);
        if (i.getStatus() == InvoiceStatus.ANNULEE) {
            throw new BusinessException("Une facture annulée ne peut plus changer de statut");
        }
        i.setStatus(status);
        i.setPaidDate(status == InvoiceStatus.PAYEE ? LocalDate.now() : null);
        audit.log(AuditService.UPDATE, "Invoice", id, i.getNumber() + " → " + status);
        return InvoiceDto.of(i);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        return pdf.invoice(find(id));
    }

    @Transactional(readOnly = true)
    public Invoice find(Long id) {
        return invoices.findById(id).orElseThrow(() -> NotFoundException.of("Facture", id));
    }
}
