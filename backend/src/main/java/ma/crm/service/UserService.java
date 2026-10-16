package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.Contact;
import ma.crm.domain.Role;
import ma.crm.domain.User;
import ma.crm.dto.CommonDtos.PageResponse;
import ma.crm.dto.UserDtos.*;
import ma.crm.exception.BusinessException;
import ma.crm.exception.NotFoundException;
import ma.crm.repository.LoginLogRepository;
import ma.crm.repository.RoleRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final LoginLogRepository loginLogs;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ma.crm.repository.ContactRepository contacts;
    private final AuthService authService;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> search(String q, String roleCode, int page, int size) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(q)) {
            String like = "%" + q.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), like),
                    cb.like(cb.lower(root.get("lastName")), like),
                    cb.like(cb.lower(root.get("email")), like)));
        }
        if (StringUtils.hasText(roleCode)) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role").get("code"), roleCode));
        }
        var pageable = PageRequest.of(page, size, Sort.by("lastName", "firstName"));
        return PageResponse.of(users.findAll(spec, pageable), UserDto::of);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.of(find(id));
    }

    /** Utilisateurs actifs auxquels on peut attribuer un client ou un lead. */
    @Transactional(readOnly = true)
    public List<UserSummary> assignable() {
        return users.findByActiveTrueAndRole_CodeIn(List.of(Role.COMMERCIAL, Role.DIRECTEUR)).stream()
                .map(UserSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public List<UserSummary> byRoles(List<String> roleCodes) {
        return users.findByActiveTrueAndRole_CodeIn(roleCodes).stream().map(UserSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleDto> roles() {
        return roles.findAll(Sort.by("id")).stream().map(RoleDto::of).toList();
    }

    public UserDto create(UserRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("Un utilisateur utilise déjà cet email");
        }
        if (!StringUtils.hasText(req.password())) {
            throw new BusinessException("Le mot de passe est obligatoire");
        }
        User user = new User();
        apply(user, req, email);
        user.setPassword(passwordEncoder.encode(req.password()));
        users.save(user);
        audit.log(AuditService.CREATE, "User", user.getId(), email + " (" + user.getRole().getCode() + ")");
        return UserDto.of(user);
    }

    public UserDto update(Long id, UserRequest req) {
        User user = find(id);
        String email = req.email().trim().toLowerCase();
        if (!user.getEmail().equalsIgnoreCase(email) && users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("Un utilisateur utilise déjà cet email");
        }
        boolean self = Objects.equals(id, CurrentUser.id());
        if (self && (Boolean.FALSE.equals(req.active()) || !Role.ADMIN.equals(req.roleCode()))
                && Role.ADMIN.equals(user.getRole().getCode())) {
            throw new BusinessException("Vous ne pouvez pas retirer vos propres droits d'administrateur");
        }
        apply(user, req, email);
        if (StringUtils.hasText(req.password())) {
            user.setPassword(passwordEncoder.encode(req.password()));
        }
        audit.log(AuditService.UPDATE, "User", id, email);
        return UserDto.of(user);
    }

    public UserDto setActive(Long id, boolean active) {
        if (!active && Objects.equals(id, CurrentUser.id())) {
            throw new BusinessException("Vous ne pouvez pas désactiver votre propre compte");
        }
        User user = find(id);
        user.setActive(active);
        audit.log(AuditService.UPDATE, "User", id, active ? "activé" : "désactivé");
        return UserDto.of(user);
    }

    public UserDto updateProfile(Long id, ProfileRequest req) {
        User user = find(id);
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPhone(StringUtils.hasText(req.phone()) ? req.phone().trim() : null);
        return UserDto.of(user);
    }

    /** Modification des permissions d'un rôle par l'administrateur. */
    public RoleDto updateRole(Long id, java.util.Set<ma.crm.domain.Permission> permissions, String label) {
        Role role = roles.findById(id).orElseThrow(() -> NotFoundException.of("Rôle", id));
        if (Role.ADMIN.equals(role.getCode()) && !permissions.contains(ma.crm.domain.Permission.USER_MANAGE)) {
            throw new BusinessException("Le rôle Administrateur doit conserver la gestion des utilisateurs");
        }
        role.getPermissions().clear();
        role.getPermissions().addAll(permissions);
        if (StringUtils.hasText(label)) {
            role.setLabel(label.trim());
        }
        audit.log(AuditService.UPDATE, "Role", id, role.getCode() + " : " + permissions);
        return RoleDto.of(role);
    }

    /** Ouvre un compte « Client (portail) » pour un contact et lui envoie le lien d'activation. */
    public UserDto invitePortal(Long contactId) {
        Contact contact = contacts.findById(contactId).orElseThrow(() -> NotFoundException.of("Contact", contactId));
        if (contact.getCompany() == null) {
            throw new BusinessException("Rattachez d'abord ce contact à une entreprise");
        }
        if (!StringUtils.hasText(contact.getEmail())) {
            throw new BusinessException("Ce contact n'a pas d'adresse e-mail");
        }
        if (users.findByContact_Id(contactId).isPresent()) {
            throw BusinessException.conflict("Ce contact a déjà un accès au portail");
        }
        String email = contact.getEmail().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("Un compte utilise déjà l'adresse " + email);
        }
        User user = new User();
        user.setFirstName(contact.getFirstName());
        user.setLastName(contact.getLastName());
        user.setEmail(email);
        user.setPhone(contact.getPhone());
        user.setRole(roles.findByCode(Role.CLIENT).orElseThrow());
        user.setContact(contact);
        // Mot de passe aléatoire inutilisable : le client définit le sien via le lien d'activation.
        user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
        users.save(user);
        authService.sendPortalInvitation(user, contact.getCompany().getName());
        audit.log(AuditService.CREATE, "User", user.getId(), "accès portail pour le contact #" + contactId);
        return UserDto.of(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<LoginLogDto> loginHistory(String email, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = email == null ? loginLogs.findAllByOrderByAtDesc(pageable)
                : loginLogs.findByEmailIgnoreCaseOrderByAtDesc(email, pageable);
        return PageResponse.of(result, LoginLogDto::of);
    }

    private void apply(User user, UserRequest req, String email) {
        Role role = roles.findByCode(req.roleCode())
                .orElseThrow(() -> new BusinessException("Rôle inconnu : " + req.roleCode()));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setEmail(email);
        user.setPhone(StringUtils.hasText(req.phone()) ? req.phone().trim() : null);
        user.setRole(role);
        if (req.active() != null) {
            user.setActive(req.active());
        }
    }

    User find(Long id) {
        return users.findById(id).orElseThrow(() -> NotFoundException.of("Utilisateur", id));
    }
}
