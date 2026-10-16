package ma.crm.service;

import jakarta.validation.constraints.Size;
import ma.crm.domain.*;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.ActivityRepository;
import ma.crm.repository.ContactRepository;
import ma.crm.repository.OpportunityRepository;
import ma.crm.repository.QuoteRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Assistant de rédaction : rassemble le contexte CRM (contact, opportunité, devis, dernière interaction)
 * et demande un brouillon d'e-mail au service IA, qui interroge le modèle de langage.
 * Les données envoyées sont limitées au strict nécessaire (minimisation, loi 09-08).
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    public record DraftRequest(String purpose, Long contactId, Long opportunityId, Long quoteId,
                               @Size(max = 60) String tone, @Size(max = 500) String notes) {
    }

    public record Draft(String subject, String body, String model, boolean generated, String to) {
    }

    private final RestClient client;
    private final boolean enabled;
    private final ContactRepository contacts;
    private final OpportunityRepository opportunities;
    private final QuoteRepository quotes;
    private final ActivityRepository activities;
    private final UserRepository users;
    private final SettingsService settings;

    public AssistantService(@Value("${app.ai.url:}") String aiUrl, ContactRepository contacts,
                            OpportunityRepository opportunities, QuoteRepository quotes,
                            ActivityRepository activities, UserRepository users, SettingsService settings) {
        this.enabled = StringUtils.hasText(aiUrl);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.client = RestClient.builder().baseUrl(enabled ? aiUrl : "http://localhost").requestFactory(factory).build();
        this.contacts = contacts;
        this.opportunities = opportunities;
        this.quotes = quotes;
        this.activities = activities;
        this.users = users;
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Draft draft(DraftRequest req) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        Contact contact = null;
        Quote quote = null;
        Opportunity opp = null;
        if (req.quoteId() != null) {
            quote = quotes.findById(req.quoteId()).orElseThrow(() -> NotFoundException.of("Devis", req.quoteId()));
            opp = quote.getOpportunity();
            contact = quote.getContact();
            ctx.put("quote_number", quote.getNumber());
            ctx.put("amount", PdfService.money(quote.getTotalTTC()) + " TTC");
        }
        if (opp == null && req.opportunityId() != null) {
            opp = opportunities.findById(req.opportunityId()).orElseThrow(() -> NotFoundException.of("Opportunité", req.opportunityId()));
        }
        if (opp != null) {
            CurrentUser.checkOwnership(opp.getOwner());
            ctx.put("opportunity", opp.getTitle());
            ctx.putIfAbsent("amount", PdfService.money(opp.getAmount()) + " HT");
            ctx.put("stage", OpportunityService.label(opp.getStage()));
            ctx.put("company", opp.getCompany().getName());
            if (contact == null) {
                contact = opp.getContact();
            }
        }
        if (contact == null && req.contactId() != null) {
            contact = contacts.findById(req.contactId()).orElseThrow(() -> NotFoundException.of("Contact", req.contactId()));
        }
        if (contact != null) {
            if (contact.isAnonymized()) {
                throw new BusinessException("Ce contact a exercé son droit d'effacement");
            }
            ctx.put("contact_name", contact.getFullName());
            if (contact.getCompany() != null) {
                ctx.putIfAbsent("company", contact.getCompany().getName());
            }
            lastInteraction(contact, opp).ifPresent(s -> ctx.put("last_interaction", s));
        }
        if (StringUtils.hasText(req.notes())) {
            ctx.put("notes", req.notes().trim());
        }
        User me = users.findById(CurrentUser.id()).orElseThrow();
        ctx.put("sender_name", me.getFullName());
        ctx.put("sender_company", settings.get(SettingsService.COMPANY_NAME));

        String purpose = StringUtils.hasText(req.purpose()) ? req.purpose() : (quote != null ? "devis" : "relance");
        String to = contact == null ? null : contact.getEmail();
        if (!enabled) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Le service IA n'est pas configuré (app.ai.url)");
        }
        try {
            Map<?, ?> res = client.post().uri("/draft-email").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("purpose", purpose, "tone", req.tone() == null ? "" : req.tone(), "context", ctx))
                    .retrieve().body(Map.class);
            return split((String) res.get("text"), String.valueOf(res.get("model")), Boolean.TRUE.equals(res.get("generated")), to);
        } catch (HttpStatusCodeException e) {
            log.warn("Assistant indisponible : {}", e.getResponseBodyAsString());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "L'assistant de rédaction est indisponible pour le moment");
        } catch (RuntimeException e) {
            log.warn("Service IA injoignable : {}", e.getMessage());
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "Le service IA est injoignable");
        }
    }

    private java.util.Optional<String> lastInteraction(Contact contact, Opportunity opp) {
        var page = activities.findAll((root, q, cb) -> opp != null
                        ? cb.equal(root.get("opportunity").get("id"), opp.getId())
                        : cb.equal(root.get("contact").get("id"), contact.getId()),
                PageRequest.of(0, 1, Sort.by("dueAt").descending()));
        return page.stream().findFirst().map(a -> a.getSubject() + " ("
                + DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()).format(a.getDueAt()) + ")");
    }

    /** Sépare la ligne « Objet : ... » du corps de l'e-mail. */
    static Draft split(String text, String model, boolean generated, String to) {
        String subject = "";
        String body = text == null ? "" : text.trim();
        if (body.toLowerCase().startsWith("objet")) {
            int nl = body.indexOf('\n');
            String first = nl < 0 ? body : body.substring(0, nl);
            subject = first.substring(first.indexOf(':') + 1).trim();
            body = nl < 0 ? "" : body.substring(nl + 1).trim();
        }
        return new Draft(subject, body, model, generated, to);
    }
}
