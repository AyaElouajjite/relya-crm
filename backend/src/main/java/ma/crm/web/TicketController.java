package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.TicketPriority;
import ma.crm.domain.Enums.TicketStatus;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.TicketDtos.*;
import ma.crm.service.TicketService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets SAV")
public class TicketController {

    private final TicketService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('TICKET_MANAGE', 'CONTACT_READ')")
    public PageResponse<TicketDto> search(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) TicketStatus status,
                                          @RequestParam(required = false) TicketPriority priority,
                                          @RequestParam(required = false) Long assigneeId,
                                          @RequestParam(required = false) Long companyId,
                                          @RequestParam(defaultValue = "false") boolean openOnly,
                                          @RequestParam(defaultValue = "false") boolean overdueOnly,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        var f = new TicketService.Filter(q, status, priority, assigneeId, companyId, openOnly, overdueOnly);
        return service.search(f, page, Math.min(size, 100));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('TICKET_MANAGE', 'CONTACT_READ')")
    public TicketDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TICKET_MANAGE')")
    public TicketDto create(@Valid @RequestBody TicketRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('TICKET_MANAGE')")
    public TicketDto update(@PathVariable Long id, @Valid @RequestBody TicketUpdateRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('TICKET_MANAGE')")
    public TicketDto reply(@PathVariable Long id, @Valid @RequestBody MessageRequest req) {
        return service.reply(id, req);
    }
}
