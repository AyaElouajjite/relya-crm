package ma.crm.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.crm.dto.CommonDtos.AuditLogDto;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.UserDtos.*;
import ma.crm.service.AuditService;
import ma.crm.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs et rôles")
public class UserController {

    private final UserService userService;
    private final AuditService auditService;

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public PageResponse<UserDto> search(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) String role,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return userService.search(q, role, page, Math.min(size, 100));
    }

    /** Liste courte des commerciaux, accessible à tous pour les champs « attribué à ». */
    @GetMapping("/users/assignable")
    public List<UserSummary> assignable() {
        return userService.assignable();
    }

    /** Agents support actifs, pour l'attribution des tickets. */
    @GetMapping("/users/agents")
    @PreAuthorize("hasAnyAuthority('TICKET_MANAGE', 'USER_MANAGE')")
    public List<UserSummary> agents() {
        return userService.byRoles(List.of(ma.crm.domain.Role.SUPPORT));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto create(@Valid @RequestBody UserRequest req) {
        return userService.create(req);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserRequest req) {
        return userService.update(id, req);
    }

    @PatchMapping("/users/{id}/active")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto setActive(@PathVariable Long id, @RequestParam boolean value) {
        return userService.setActive(id, value);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<RoleDto> roles() {
        return userService.roles();
    }

    public record RoleUpdate(@jakarta.validation.constraints.NotNull java.util.Set<ma.crm.domain.Permission> permissions,
                             String label) {
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public RoleDto updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdate body) {
        return userService.updateRole(id, body.permissions(), body.label());
    }

    @GetMapping("/admin/logins")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public PageResponse<LoginLogDto> logins(@RequestParam(required = false) String email,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return userService.loginHistory(email, page, Math.min(size, 200));
    }

    @GetMapping("/admin/audit")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public PageResponse<AuditLogDto> audit(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return auditService.list(page, Math.min(size, 200));
    }
}
