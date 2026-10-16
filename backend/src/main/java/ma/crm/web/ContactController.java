package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.domain.CompanyStatus;
import ma.crm.dto.CommonDtos.ImportResult;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.ContactDtos.ContactDto;
import ma.crm.dto.ContactDtos.ContactRequest;
import ma.crm.dto.UserDtos.UserDto;
import ma.crm.service.ContactService;
import ma.crm.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts")
public class ContactController {

    private final ContactService service;
    private final UserService users;

    @GetMapping
    @PreAuthorize("hasAuthority('CONTACT_READ')")
    public PageResponse<ContactDto> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Long companyId,
                                           @RequestParam(required = false) String tag,
                                           @RequestParam(required = false) Long ownerId,
                                           @RequestParam(required = false) String sector,
                                           @RequestParam(required = false) String city,
                                           @RequestParam(required = false) CompanyStatus companyStatus,
                                           @RequestParam(defaultValue = "false") boolean archived,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "lastName") String sort,
                                           @RequestParam(defaultValue = "asc") String dir) {
        var filter = new ContactService.Filter(q, companyId, tag, ownerId, archived, sector, city, companyStatus);
        return service.search(filter, page, Math.min(size, 100), sort, dir);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTACT_READ')")
    public ContactDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CONTACT_WRITE')")
    public ContactDto create(@Valid @RequestBody ContactRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTACT_WRITE')")
    public ContactDto update(@PathVariable Long id, @Valid @RequestBody ContactRequest req) {
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

    /** Ouvre un accès au portail client pour ce contact. */
    @PostMapping("/{id}/portal-access")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('USER_MANAGE', 'TICKET_MANAGE', 'CONTACT_WRITE')")
    public UserDto invitePortal(@PathVariable Long id) {
        return users.invitePortal(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('DATA_EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String q,
                                         @RequestParam(required = false) Long companyId,
                                         @RequestParam(required = false) String tag,
                                         @RequestParam(required = false) Long ownerId,
                                         @RequestParam(required = false) String sector,
                                         @RequestParam(required = false) String city,
                                         @RequestParam(required = false) CompanyStatus companyStatus,
                                         @RequestParam(defaultValue = "false") boolean archived,
                                         @RequestParam(defaultValue = "csv") String format) {
        var filter = new ContactService.Filter(q, companyId, tag, ownerId, archived, sector, city, companyStatus);
        return "xlsx".equalsIgnoreCase(format)
                ? PdfResponses.xlsx("contacts", service.exportXlsx(filter))
                : CsvResponses.attachment("contacts", service.exportCsv(filter));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('DATA_IMPORT')")
    public ImportResult importCsv(@RequestParam("file") MultipartFile file) throws IOException {
        return service.importCsv(file.getInputStream());
    }
}
