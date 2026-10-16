package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.QuoteDtos.DecisionRequest;
import ma.crm.dto.QuoteDtos.InvoiceDto;
import ma.crm.dto.QuoteDtos.QuoteDto;
import ma.crm.dto.TicketDtos.MessageRequest;
import ma.crm.dto.TicketDtos.PortalTicketRequest;
import ma.crm.dto.TicketDtos.TicketDto;
import ma.crm.service.PortalService;
import ma.crm.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Tag(name = "Portail client")
@PreAuthorize("hasAuthority('PORTAL_ACCESS')")
public class PortalController {

    private final PortalService portal;
    private final TicketService tickets;

    @GetMapping("/home")
    public PortalService.PortalHome home() {
        return portal.home();
    }

    @GetMapping("/quotes")
    public List<QuoteDto> quotes() {
        return portal.quotes();
    }

    @GetMapping("/quotes/{id}/pdf")
    public ResponseEntity<byte[]> quotePdf(@PathVariable Long id) {
        return PdfResponses.inline(portal.quoteNumber(id) + ".pdf", portal.quotePdf(id));
    }

    @PostMapping("/quotes/{id}/accept")
    public QuoteDto accept(@PathVariable Long id) {
        return portal.accept(id);
    }

    @PostMapping("/quotes/{id}/refuse")
    public QuoteDto refuse(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req) {
        return portal.refuse(id, req);
    }

    @GetMapping("/invoices")
    public List<InvoiceDto> invoices() {
        return portal.invoices();
    }

    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> invoicePdf(@PathVariable Long id) {
        byte[] content = portal.invoicePdf(id);
        return PdfResponses.inline(portal.invoiceNumber(id) + ".pdf", content);
    }

    @GetMapping("/tickets")
    public PageResponse<TicketDto> tickets(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return tickets.portalList(page, Math.min(size, 100));
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDto openTicket(@Valid @RequestBody PortalTicketRequest req) {
        return tickets.createFromPortal(req);
    }

    @GetMapping("/tickets/{id}")
    public TicketDto ticket(@PathVariable Long id) {
        return tickets.portalGet(id);
    }

    @PostMapping("/tickets/{id}/messages")
    public TicketDto reply(@PathVariable Long id, @Valid @RequestBody MessageRequest req) {
        return tickets.portalReply(id, new MessageRequest(req.content(), false));
    }
}
