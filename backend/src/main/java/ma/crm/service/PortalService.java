package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.Company;
import ma.crm.domain.Enums.QuoteStatus;
import ma.crm.domain.Invoice;
import ma.crm.domain.Quote;
import ma.crm.dto.QuoteDtos.DecisionRequest;
import ma.crm.dto.QuoteDtos.InvoiceDto;
import ma.crm.dto.QuoteDtos.QuoteDto;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.CompanyRepository;
import ma.crm.repository.InvoiceRepository;
import ma.crm.repository.QuoteRepository;
import ma.crm.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Portail client : un compte CLIENT ne voit que les devis envoyés et les factures de son entreprise.
 * Les brouillons et devis en validation interne ne sont jamais exposés.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PortalService {

    private static final Set<QuoteStatus> VISIBLE = EnumSet.of(QuoteStatus.ENVOYE, QuoteStatus.ACCEPTE, QuoteStatus.REFUSE);

    private final CompanyRepository companies;
    private final QuoteRepository quotes;
    private final InvoiceRepository invoices;
    private final QuoteService quoteService;
    private final InvoiceService invoiceService;
    private final PdfService pdf;

    public record PortalHome(String companyName, String city, long openQuotes, long unpaidInvoices) {
    }

    @Transactional(readOnly = true)
    public PortalHome home() {
        Company c = companies.findById(companyId()).orElseThrow();
        long open = quotes.findByCompany_IdOrderByDateDesc(c.getId()).stream()
                .filter(q -> q.getStatus() == QuoteStatus.ENVOYE).count();
        long unpaid = invoices.findByCompany_IdOrderByIssueDateDesc(c.getId()).stream()
                .filter(i -> i.getStatus() == ma.crm.domain.Enums.InvoiceStatus.EMISE).count();
        return new PortalHome(c.getName(), c.getCity(), open, unpaid);
    }

    @Transactional(readOnly = true)
    public List<QuoteDto> quotes() {
        return quotes.findByCompany_IdOrderByDateDesc(companyId()).stream()
                .filter(q -> VISIBLE.contains(q.getStatus())).map(quoteService::dto).toList();
    }

    @Transactional(readOnly = true)
    public byte[] quotePdf(Long id) {
        return pdf.quote(quote(id));
    }

    public QuoteDto accept(Long id) {
        quote(id);
        return quoteService.accept(id);
    }

    public QuoteDto refuse(Long id, DecisionRequest req) {
        quote(id);
        return quoteService.refuse(id, req);
    }

    @Transactional(readOnly = true)
    public List<InvoiceDto> invoices() {
        return invoices.findByCompany_IdOrderByIssueDateDesc(companyId()).stream().map(InvoiceDto::of).toList();
    }

    @Transactional(readOnly = true)
    public byte[] invoicePdf(Long id) {
        Invoice i = invoiceService.find(id);
        if (!i.getCompany().getId().equals(companyId())) {
            throw new NotFoundException("Facture introuvable");
        }
        return pdf.invoice(i);
    }

    public String quoteNumber(Long id) {
        return quote(id).getNumber();
    }

    public String invoiceNumber(Long id) {
        return invoiceService.find(id).getNumber();
    }

    private Quote quote(Long id) {
        Quote q = quotes.findById(id).orElseThrow(() -> new NotFoundException("Devis introuvable"));
        if (!q.getCompany().getId().equals(companyId()) || !VISIBLE.contains(q.getStatus())) {
            throw new NotFoundException("Devis introuvable");
        }
        return q;
    }

    private Long companyId() {
        Long id = CurrentUser.require().companyId();
        if (id == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Votre compte n'est rattaché à aucune entreprise");
        }
        return id;
    }
}
