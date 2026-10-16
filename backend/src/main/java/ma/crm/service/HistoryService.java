package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.ActivityStatus;
import ma.crm.domain.Enums.ActivityType;
import ma.crm.repository.ActivityRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Historique automatique des interactions (module 6) : chaque événement métier important
 * (conversion, changement d'étape, envoi de devis, ticket...) est tracé comme une activité « SYSTEME »
 * visible sur la fiche client.
 */
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ActivityRepository activities;
    private final UserRepository users;

    public record Links(Company company, Contact contact, Opportunity opportunity, Lead lead) {
        public static Links company(Company c) {
            return new Links(c, null, null, null);
        }

        public static Links opportunity(Opportunity o) {
            return new Links(o.getCompany(), o.getContact(), o, null);
        }

        public static Links lead(Lead l) {
            return new Links(null, null, null, l);
        }
    }

    @Transactional
    public void record(String subject, String description, Links links) {
        Activity a = new Activity();
        a.setType(ActivityType.SYSTEME);
        a.setSubject(subject.length() > 200 ? subject.substring(0, 200) : subject);
        a.setDescription(description);
        a.setDueAt(Instant.now());
        a.setStatus(ActivityStatus.TERMINEE);
        a.setCompletedAt(Instant.now());
        a.setAutomatic(true);
        a.setCompany(links.company());
        a.setContact(links.contact());
        a.setOpportunity(links.opportunity());
        a.setLead(links.lead());
        Long userId = CurrentUser.id();
        a.setOwner(userId == null ? null : users.getReferenceById(userId));
        activities.save(a);
        if (links.opportunity() != null) {
            links.opportunity().setLastActivityAt(Instant.now());
        }
    }
}
