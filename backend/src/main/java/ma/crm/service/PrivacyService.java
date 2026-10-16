package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Conformité à la loi marocaine 09-08 (protection des données personnelles) :
 * droit d'accès (export des données d'une personne), droit d'opposition / d'effacement
 * (anonymisation) et durée de conservation limitée des prospects non convertis.
 */
@Service
@RequiredArgsConstructor
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);
    static final String ANONYMIZED = "Anonymisé";

    private final ContactRepository contacts;
    private final LeadRepository leads;
    private final ActivityRepository activities;
    private final TicketRepository tickets;
    private final UserRepository users;
    private final SettingsService settings;
    private final AuditService audit;

    /** Droit d'accès : toutes les données personnelles détenues sur un contact. */
    @Transactional
    public Map<String, Object> exportContact(Long id) {
        Contact c = contacts.findById(id).orElseThrow(() -> NotFoundException.of("Contact", id));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("identite", Map.of("prenom", c.getFirstName(), "nom", c.getLastName(),
                "email", Objects.toString(c.getEmail(), ""), "telephone", Objects.toString(c.getPhone(), ""),
                "poste", Objects.toString(c.getPosition(), "")));
        data.put("entreprise", c.getCompany() == null ? null : c.getCompany().getName());
        data.put("consentementMarketing", c.isMarketingConsent());
        data.put("dateConsentement", c.getConsentDate());
        data.put("tags", c.getTags());
        data.put("creeLe", c.getCreatedAt());
        data.put("interactions", activities.findAll((root, q, cb) -> cb.equal(root.get("contact").get("id"), id)).stream()
                .map(a -> Map.of("date", a.getDueAt(), "type", a.getType().name(), "sujet", a.getSubject())).toList());
        data.put("tickets", tickets.findAll((root, q, cb) -> cb.equal(root.get("contact").get("id"), id)).stream()
                .map(t -> Map.of("numero", t.getNumber(), "objet", t.getSubject(), "ouvertLe", t.getOpenedAt())).toList());
        audit.log(AuditService.EXPORT, "Contact", id, "export des données personnelles (droit d'accès)");
        return data;
    }

    /** Droit d'effacement : les données identifiantes sont remplacées, l'historique commercial est conservé. */
    @Transactional
    public void anonymizeContact(Long id) {
        Contact c = contacts.findById(id).orElseThrow(() -> NotFoundException.of("Contact", id));
        c.setFirstName(ANONYMIZED);
        c.setLastName("#" + c.getId());
        c.setEmail(null);
        c.setPhone(null);
        c.setPosition(null);
        c.getTags().clear();
        c.setMarketingConsent(false);
        c.setConsentDate(null);
        c.setAnonymized(true);
        c.setArchived(true);
        users.findByContact_Id(id).ifPresent(u -> {
            u.setActive(false);
            u.setContact(null);
        });
        audit.log(AuditService.DELETE, "Contact", id, "anonymisation (droit d'effacement, loi 09-08)");
    }

    @Transactional
    public void anonymizeLead(Long id) {
        Lead l = leads.findById(id).orElseThrow(() -> NotFoundException.of("Lead", id));
        anonymize(l);
        audit.log(AuditService.DELETE, "Lead", id, "anonymisation (droit d'effacement, loi 09-08)");
    }

    /** Durée de conservation : les leads non convertis inactifs depuis N mois sont anonymisés. */
    @Transactional
    public int applyRetention() {
        int months = settings.getInt(SettingsService.LEAD_RETENTION_MONTHS);
        Instant before = Instant.now().minus(months * 30L, ChronoUnit.DAYS);
        List<Lead> stale = leads.findStaleForRetention(before);
        stale.forEach(PrivacyService::anonymize);
        if (!stale.isEmpty()) {
            audit.log(AuditService.DELETE, "Lead", null, stale.size() + " lead(s) anonymisé(s) — conservation de " + months + " mois");
            log.info("{} lead(s) anonymisé(s) au titre de la durée de conservation", stale.size());
        }
        return stale.size();
    }

    private static void anonymize(Lead l) {
        l.setFirstName(ANONYMIZED);
        l.setLastName("#" + l.getId());
        l.setEmail(null);
        l.setPhone(null);
        l.setPosition(null);
        l.setNotes(null);
        l.setMarketingConsent(false);
        l.setAnonymized(true);
    }
}
