package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.security.AppUserDetails;
import ma.crm.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Notifications, paramètres, automatisations, assistant et conformité (loi 09-08). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Plateforme")
public class PlatformController {

    private final NotificationService notifications;
    private final SettingsService settings;
    private final AutomationService automation;
    private final PrivacyService privacy;
    private final AssistantService assistant;

    // ---- Notifications ----

    @GetMapping("/notifications")
    public PageResponse<NotificationService.NotificationDto> notifications(@AuthenticationPrincipal AppUserDetails user,
                                                                           @RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "20") int size) {
        return notifications.list(user.id(), page, Math.min(size, 100));
    }

    @GetMapping("/notifications/unread")
    public Map<String, Long> unread(@AuthenticationPrincipal AppUserDetails user) {
        return notifications.unread(user.id());
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AppUserDetails user, @PathVariable Long id) {
        notifications.markRead(user.id(), id);
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AppUserDetails user) {
        notifications.markAllRead(user.id());
    }

    // ---- Paramètres ----

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public Map<String, String> settings() {
        return settings.all();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public Map<String, String> updateSettings(@RequestBody Map<String, String> values) {
        return settings.update(values);
    }

    /** Lance immédiatement la règle de relance (sinon exécutée chaque jour à 8 h). */
    @PostMapping("/automation/follow-ups/run")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE', 'DASHBOARD_ALL')")
    public Map<String, Integer> runFollowUps() {
        return automation.runFollowUps();
    }

    // ---- Assistant de rédaction ----

    @PostMapping("/assistant/draft")
    @PreAuthorize("hasAnyAuthority('ACTIVITY_MANAGE', 'QUOTE_MANAGE')")
    public AssistantService.Draft draft(@Valid @RequestBody AssistantService.DraftRequest req) {
        return assistant.draft(req);
    }

    // ---- Loi 09-08 ----

    @GetMapping("/privacy/contacts/{id}/export")
    @PreAuthorize("hasAuthority('DATA_PRIVACY')")
    public Map<String, Object> exportContact(@PathVariable Long id) {
        return privacy.exportContact(id);
    }

    @PostMapping("/privacy/contacts/{id}/anonymize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DATA_PRIVACY')")
    public void anonymizeContact(@PathVariable Long id) {
        privacy.anonymizeContact(id);
    }

    @PostMapping("/privacy/leads/{id}/anonymize")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('DATA_PRIVACY')")
    public void anonymizeLead(@PathVariable Long id) {
        privacy.anonymizeLead(id);
    }

    @PostMapping("/privacy/retention/run")
    @PreAuthorize("hasAuthority('DATA_PRIVACY')")
    public Map<String, Integer> runRetention() {
        return Map.of("anonymized", privacy.applyRetention());
    }
}
