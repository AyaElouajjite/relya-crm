package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.dto.SalesDtos.PublicLeadRequest;
import ma.crm.exception.NotFoundException;
import ma.crm.service.ActivityService;
import ma.crm.service.CalendarFeedService;
import ma.crm.service.LeadService;
import ma.crm.service.QuoteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Points d'accès sans authentification, protégés par anti-spam ou par un jeton secret. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public")
public class PublicController {

    private final LeadService leads;
    private final QuoteService quotes;
    private final ActivityService activities;
    private final CalendarFeedService calendarFeed;

    /** Formulaire web de capture de leads (site vitrine). */
    @PostMapping("/leads")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> captureLead(@Valid @RequestBody PublicLeadRequest req, HttpServletRequest http) {
        leads.capturePublic(req, http.getRemoteAddr());
        return Map.of("message", "Merci, votre demande a bien été reçue. Un conseiller vous contactera rapidement.");
    }

    /** Devis partagé par lien (e-mail, WhatsApp). */
    @GetMapping("/quotes/{token}/pdf")
    public ResponseEntity<byte[]> quotePdf(@PathVariable String token) {
        var quote = quotes.findByPublicToken(token);
        return PdfResponses.inline(quote.getNumber() + ".pdf", quotes.publicPdf(token));
    }

    /** Abonnement iCalendar signé. */
    @GetMapping(value = "/calendar/{userId}/{signature}.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<String> calendar(@PathVariable Long userId, @PathVariable String signature) {
        if (!calendarFeed.verify(userId, signature)) {
            throw new NotFoundException("Agenda introuvable");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .body(activities.ics(userId));
    }
}
