package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.Enums.LeadSource;
import ma.crm.domain.Enums.LeadStatus;
import ma.crm.dto.CommonDtos.ImportResult;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.SalesDtos.*;
import ma.crm.service.LeadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@Tag(name = "Leads")
@PreAuthorize("hasAuthority('LEAD_MANAGE')")
public class LeadController {

    private final LeadService service;

    @GetMapping
    public PageResponse<LeadDto> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) LeadStatus status,
                                        @RequestParam(required = false) LeadSource source,
                                        @RequestParam(required = false) Long assignedToId,
                                        @RequestParam(required = false) Integer minScore,
                                        @RequestParam(defaultValue = "false") boolean includeConverted,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(defaultValue = "createdAt") String sort,
                                        @RequestParam(defaultValue = "desc") String dir) {
        var filter = new LeadService.Filter(q, status, source, assignedToId, minScore, includeConverted);
        return service.search(filter, page, Math.min(size, 100), sort, dir);
    }

    @GetMapping("/{id}")
    public LeadDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadDto create(@Valid @RequestBody LeadRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public LeadDto update(@PathVariable Long id, @Valid @RequestBody LeadRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
    public LeadDto assign(@PathVariable Long id, @RequestParam Long userId) {
        return service.assign(id, userId);
    }

    @PostMapping("/{id}/convert")
    public ConvertLeadResult convert(@PathVariable Long id, @Valid @RequestBody ConvertLeadRequest req) {
        return service.convert(id, req);
    }

    @PostMapping("/{id}/score")
    public LeadDto rescore(@PathVariable Long id) {
        return service.rescore(id);
    }

    @PostMapping("/score-all")
    @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
    public Map<String, Integer> rescoreAll() {
        return Map.of("scored", service.rescoreAll());
    }

    @PostMapping("/model/train")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public Map<?, ?> retrain() {
        return service.retrainModel();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('DATA_EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) LeadStatus status,
                                         @RequestParam(required = false) LeadSource source,
                                         @RequestParam(required = false) Long assignedToId) {
        var filter = new LeadService.Filter(q, status, source, assignedToId, null, true);
        return CsvResponses.attachment("leads", service.exportCsv(filter));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DATA_IMPORT')")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return service.importCsv(file.getInputStream());
    }
}
