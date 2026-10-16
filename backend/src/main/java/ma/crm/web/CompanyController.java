package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.CompanyStatus;
import ma.crm.dto.CommonDtos.ImportResult;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.CompanyDtos.*;
import ma.crm.service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
@Tag(name = "Entreprises")
public class CompanyController {

    private final CompanyService service;

    @GetMapping
    @PreAuthorize("hasAuthority('CONTACT_READ')")
    public PageResponse<CompanyDto> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String sector,
                                           @RequestParam(required = false) String city,
                                           @RequestParam(required = false) CompanyStatus status,
                                           @RequestParam(required = false) String tag,
                                           @RequestParam(required = false) Long ownerId,
                                           @RequestParam(defaultValue = "false") boolean archived,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "name") String sort,
                                           @RequestParam(defaultValue = "asc") String dir) {
        var filter = new CompanyService.Filter(q, sector, city, status, tag, ownerId, archived);
        return service.search(filter, page, Math.min(size, 100), sort, dir);
    }

    @GetMapping("/filters")
    @PreAuthorize("hasAuthority('CONTACT_READ')")
    public FilterOptions filters() {
        return service.filterOptions();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTACT_READ')")
    public Company360 get(@PathVariable Long id) {
        return service.get360(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTACT_WRITE')")
    public CompanyDto create(@Valid @RequestBody CompanyRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTACT_WRITE')")
    public CompanyDto update(@PathVariable Long id, @Valid @RequestBody CompanyRequest req) {
        return service.update(id, req);
    }

    @PatchMapping("/{id}/archived")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CONTACT_WRITE')")
    public void setArchived(@PathVariable Long id, @RequestParam boolean value) {
        service.setArchived(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('CONTACT_DELETE')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('DATA_EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) String sector,
                                         @RequestParam(required = false) String city,
                                         @RequestParam(required = false) CompanyStatus status,
                                         @RequestParam(required = false) String tag,
                                         @RequestParam(required = false) Long ownerId,
                                         @RequestParam(defaultValue = "false") boolean archived,
                                         @RequestParam(defaultValue = "csv") String format) {
        var filter = new CompanyService.Filter(q, sector, city, status, tag, ownerId, archived);
        return "xlsx".equalsIgnoreCase(format)
                ? PdfResponses.xlsx("entreprises", service.exportXlsx(filter))
                : CsvResponses.attachment("entreprises", service.exportCsv(filter));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DATA_IMPORT')")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return service.importCsv(file.getInputStream());
    }
}
