package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.Activity;
import ma.crm.domain.Enums.ActivityStatus;
import ma.crm.domain.Enums.ActivityType;
import ma.crm.domain.Opportunity;
import ma.crm.repository.ActivityRepository;
import ma.crm.repository.OpportunityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Relances automatiques : « opportunité sans activité depuis N jours → tâche + notification au commercial ».
 * N est réglable dans les paramètres (7 jours par défaut).
 */
@Service
@RequiredArgsConstructor
public class AutomationService {

    static final String FOLLOW_UP_PREFIX = "Relance : ";
    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    private final OpportunityRepository opportunities;
    private final ActivityRepository activities;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final PrivacyService privacy;

    /** Tous les jours à 8 h, et au démarrage + 2 min. */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void dailyRun() {
        runFollowUps();
        privacy.applyRetention();
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = Long.MAX_VALUE)
    @Transactional
    public void startupRun() {
        runFollowUps();
    }

    @Transactional
    public Map<String, Integer> runFollowUps() {
        int days = settings.getInt(SettingsService.FOLLOWUP_INACTIVITY_DAYS);
        Instant before = Instant.now().minus(days, ChronoUnit.DAYS);
        int created = 0;
        for (Opportunity o : opportunities.findInactiveSince(before)) {
            // Une seule relance ouverte à la fois par opportunité.
            if (activities.existsByOpportunity_IdAndAutomaticTrueAndStatusAndSubjectStartingWith(
                    o.getId(), ActivityStatus.A_FAIRE, FOLLOW_UP_PREFIX)) {
                continue;
            }
            Activity task = new Activity();
            task.setType(ActivityType.TACHE);
            task.setSubject(FOLLOW_UP_PREFIX + o.getTitle());
            task.setDescription("Aucune activité depuis plus de " + days + " jours sur cette opportunité ("
                    + o.getCompany().getName() + "). Reprenez contact avec le client.");
            task.setDueAt(Instant.now().plus(1, ChronoUnit.DAYS));
            task.setOpportunity(o);
            task.setCompany(o.getCompany());
            task.setContact(o.getContact());
            task.setOwner(o.getOwner());
            task.setAutomatic(true);
            activities.save(task);
            notifications.notify(o.getOwner(), "FOLLOW_UP", "Relance à faire : " + o.getTitle(),
                    o.getCompany().getName() + " — sans activité depuis " + days + " jours", "/opportunities/" + o.getId(), true);
            created++;
        }
        if (created > 0) {
            log.info("{} relance(s) automatique(s) créée(s)", created);
        }
        return Map.of("created", created);
    }
}
