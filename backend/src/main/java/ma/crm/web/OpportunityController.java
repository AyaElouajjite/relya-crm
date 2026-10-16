package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.OpportunityStage;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.SalesDtos.*;
import ma.crm.service.OpportunityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/opportunities")
@RequiredArgsConstructor
@Tag(name = "Opportunités")
public class OpportunityController {

    private final OpportunityService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY_MANAGE', 'DASHBOARD_ALL')")
    public PageResponse<OpportunityDto> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) OpportunityStage stage,
                                               @RequestParam(required = false) Long ownerId,
                                               @RequestParam(required = false) Long companyId,
                                               @RequestParam(defaultValue = "false") boolean openOnly,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(defaultValue = "createdAt") String sort,
                                               @RequestParam(defaultValue = "desc") String dir) {
        var filter = new OpportunityService.Filter(q, stage, ownerId, companyId, openOnly, null, null);
        return service.search(filter, page, Math.min(size, 100), sort, dir);
    }

    @GetMapping("/pipeline")
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY_MANAGE', 'DASHBOARD_ALL')")
    public List<PipelineColumn> pipeline(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Long ownerId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeFrom,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closeTo) {
        return service.pipeline(new OpportunityService.Filter(q, null, ownerId, null, false, closeFrom, closeTo));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('OPPORTUNITY_MANAGE', 'DASHBOARD_ALL')")
    public OpportunityDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('OPPORTUNITY_MANAGE')")
    public OpportunityDto create(@Valid @RequestBody OpportunityRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OPPORTUNITY_MANAGE')")
    public OpportunityDto update(@PathVariable Long id, @Valid @RequestBody OpportunityRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/stage")
    @PreAuthorize("hasAuthority('OPPORTUNITY_MANAGE')")
    public OpportunityDto moveStage(@PathVariable Long id, @Valid @RequestBody StageChangeRequest req) {
        return service.moveStage(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('OPPORTUNITY_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
