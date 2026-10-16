package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.ActivityStatus;
import ma.crm.domain.Enums.ActivityType;
import ma.crm.dto.ActivityDtos.ActivityDto;
import ma.crm.dto.ActivityDtos.ActivityRequest;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.security.AppUserDetails;
import ma.crm.service.ActivityService;
import ma.crm.service.CalendarFeedService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
@Tag(name = "Activités et agenda")
public class ActivityController {

    private final ActivityService service;
    private final CalendarFeedService calendarFeed;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ACTIVITY_MANAGE', 'CONTACT_READ')")
    public PageResponse<ActivityDto> search(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                            @RequestParam(required = false) Long ownerId,
                                            @RequestParam(required = false) ActivityType type,
                                            @RequestParam(required = false) ActivityStatus status,
                                            @RequestParam(required = false) Long companyId,
                                            @RequestParam(required = false) Long contactId,
                                            @RequestParam(required = false) Long opportunityId,
                                            @RequestParam(required = false) Long leadId,
                                            @RequestParam(defaultValue = "false") boolean includeSystem,
                                            @RequestParam(defaultValue = "false") boolean overdueOnly,
                                            @RequestParam(defaultValue = "true") boolean ascending,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        var f = new ActivityService.Filter(from, to, ownerId, type, status, companyId, contactId, opportunityId, leadId,
                includeSystem, overdueOnly);
        return service.search(f, page, Math.min(size, 200), ascending);
    }

    /** Calendrier partagé : les activités de tous les utilisateurs sur une période (filtrable par utilisateur). */
    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('ACTIVITY_MANAGE')")
    public List<ActivityDto> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      @RequestParam(required = false) Long ownerId) {
        return service.calendar(new ActivityService.Filter(from, to, ownerId, null, null, null, null, null, null, false, false));
    }

    /** Lien d'abonnement iCalendar personnel (synchronisation d'agenda). */
    @GetMapping("/calendar-feed")
    @PreAuthorize("hasAuthority('ACTIVITY_MANAGE')")
    public Map<String, String> feedUrl(@AuthenticationPrincipal AppUserDetails user) {
        return Map.of("path", calendarFeed.path(user.id()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ACTIVITY_MANAGE', 'CONTACT_READ')")
    public ActivityDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ACTIVITY_MANAGE', 'TICKET_MANAGE')")
    public ActivityDto create(@Valid @RequestBody ActivityRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ACTIVITY_MANAGE', 'TICKET_MANAGE')")
    public ActivityDto update(@PathVariable Long id, @Valid @RequestBody ActivityRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ACTIVITY_MANAGE', 'TICKET_MANAGE')")
    public ActivityDto setStatus(@PathVariable Long id, @RequestParam ActivityStatus value) {
        return service.setStatus(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ACTIVITY_MANAGE', 'TICKET_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
