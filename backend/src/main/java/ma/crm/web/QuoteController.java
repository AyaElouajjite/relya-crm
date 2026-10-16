package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.InvoiceStatus;
import ma.crm.domain.Enums.QuoteStatus;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.QuoteDtos.*;
import ma.crm.service.InvoiceService;
import ma.crm.service.QuoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Devis et factures")
public class QuoteController {

    private final QuoteService quotes;
    private final InvoiceService invoices;

    @GetMapping("/quotes")
    @PreAuthorize("hasAnyAuthority('QUOTE_MANAGE', 'QUOTE_APPROVE')")
    public PageResponse<QuoteSummary> search(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) QuoteStatus status,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(required = false) Long opportunityId,
                                             @RequestParam(required = false) Long ownerId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return quotes.search(new QuoteService.Filter(q, status, companyId, opportunityId, ownerId), page, Math.min(size, 100));
    }

    @GetMapping("/quotes/{id}")
    @PreAuthorize("hasAnyAuthority('QUOTE_MANAGE', 'QUOTE_APPROVE')")
    public QuoteDto get(@PathVariable Long id) {
        return quotes.get(id);
    }

    @GetMapping("/quotes/{id}/pdf")
    @PreAuthorize("hasAnyAuthority('QUOTE_MANAGE', 'QUOTE_APPROVE')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        QuoteDto q = quotes.get(id);
        return PdfResponses.inline(q.number() + ".pdf", quotes.pdf(id));
    }

    @PostMapping("/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public QuoteDto create(@Valid @RequestBody QuoteRequest req) {
        return quotes.create(req);
    }

    @PutMapping("/quotes/{id}")
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public QuoteDto update(@PathVariable Long id, @Valid @RequestBody QuoteRequest req) {
        return quotes.update(id, req);
    }

    @PostMapping("/quotes/{id}/duplicate")
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public QuoteDto duplicate(@PathVariable Long id) {
        return quotes.duplicate(id);
    }

    @PostMapping("/quotes/{id}/send")
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public QuoteDto send(@PathVariable Long id, @Valid @RequestBody(required = false) SendQuoteRequest req) {
        return quotes.send(id, req == null ? new SendQuoteRequest(null, null) : req);
    }

    @PostMapping("/quotes/{id}/approve")
    @PreAuthorize("hasAuthority('QUOTE_APPROVE')")
    public QuoteDto approve(@PathVariable Long id) {
        return quotes.approve(id);
    }

    @PostMapping("/quotes/{id}/reject-approval")
    @PreAuthorize("hasAuthority('QUOTE_APPROVE')")
    public QuoteDto rejectApproval(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req) {
        return quotes.rejectApproval(id, req);
    }

    @PostMapping("/quotes/{id}/accept")
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public QuoteDto accept(@PathVariable Long id) {
        return quotes.accept(id);
    }

    @PostMapping("/quotes/{id}/refuse")
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public QuoteDto refuse(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req) {
        return quotes.refuse(id, req);
    }

    @DeleteMapping("/quotes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public void delete(@PathVariable Long id) {
        quotes.delete(id);
    }

    // ---- Factures ----

    @GetMapping("/invoices")
    @PreAuthorize("hasAnyAuthority('QUOTE_MANAGE', 'DASHBOARD_ALL')")
    public PageResponse<InvoiceDto> invoices(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) InvoiceStatus status,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return invoices.search(q, status, companyId, page, Math.min(size, 100));
    }

    @GetMapping("/invoices/{id}/pdf")
    @PreAuthorize("hasAnyAuthority('QUOTE_MANAGE', 'DASHBOARD_ALL')")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) {
        return PdfResponses.inline(invoices.find(id).getNumber() + ".pdf", invoices.pdf(id));
    }

    @PatchMapping("/invoices/{id}/status")
    @PreAuthorize("hasAuthority('QUOTE_MANAGE')")
    public InvoiceDto setInvoiceStatus(@PathVariable Long id, @RequestParam InvoiceStatus value) {
        return invoices.setStatus(id, value);
    }
}
