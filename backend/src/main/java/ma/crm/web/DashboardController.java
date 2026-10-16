package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.service.DashboardService;
import ma.crm.service.ObjectiveService;
import ma.crm.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Tableaux de bord et objectifs")
public class DashboardController {

    private final DashboardService dashboard;
    private final ReportService reports;
    private final ObjectiveService objectives;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW', 'DASHBOARD_ALL')")
    public DashboardService.Dashboard get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) Long ownerId) {
        return dashboard.build(from, to, ownerId);
    }

    @GetMapping("/reports/pdf")
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW', 'DASHBOARD_ALL') and hasAuthority('DATA_EXPORT')")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) Long ownerId) {
        return PdfResponses.inline("rapport-commercial-" + LocalDate.now() + ".pdf", reports.pdf(from, to, ownerId));
    }

    @GetMapping("/reports/excel")
    @PreAuthorize("hasAnyAuthority('DASHBOARD_VIEW', 'DASHBOARD_ALL') and hasAuthority('DATA_EXPORT')")
    public ResponseEntity<byte[]> excel(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(required = false) Long ownerId) {
        return PdfResponses.xlsx("rapport-commercial", reports.excel(from, to, ownerId));
    }

    @GetMapping("/objectives")
    @PreAuthorize("hasAnyAuthority('OBJECTIVE_MANAGE', 'DASHBOARD_ALL')")
    public List<ObjectiveService.ObjectiveRow> objectives(@RequestParam int year) {
        return objectives.year(year);
    }

    @PutMapping("/objectives")
    @PreAuthorize("hasAuthority('OBJECTIVE_MANAGE')")
    public List<ObjectiveService.ObjectiveRow> setObjectives(@RequestBody List<ObjectiveService.@Valid ObjectiveRequest> body) {
        objectives.set(body);
        return body.isEmpty() ? List.of() : objectives.year(body.get(0).year());
    }
}
