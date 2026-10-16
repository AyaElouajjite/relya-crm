package ma.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.crm.domain.LoginLog;
import ma.crm.domain.Permission;
import ma.crm.domain.Role;
import ma.crm.domain.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public final class UserDtos {
    private UserDtos() {
    }

    public record UserDto(Long id, String firstName, String lastName, String fullName, String email,
                          String phone, boolean active, String roleCode, String roleLabel,
                          Set<String> permissions, Instant lastLoginAt, Instant createdAt) {
        public static UserDto of(User u) {
            Set<String> perms = new TreeSet<>();
            u.getRole().getPermissions().forEach(p -> perms.add(p.name()));
            return new UserDto(u.getId(), u.getFirstName(), u.getLastName(), u.getFullName(), u.getEmail(),
                    u.getPhone(), u.isActive(), u.getRole().getCode(), u.getRole().getLabel(), perms,
                    u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    /** Version courte, pour les listes de choix (attribution d'un commercial, etc.). */
    public record UserSummary(Long id, String fullName, String roleCode) {
        public static UserSummary of(User u) {
            return u == null ? null : new UserSummary(u.getId(), u.getFullName(), u.getRole().getCode());
        }
    }

    /** Le mot de passe est obligatoire à la création, facultatif en modification. */
    public record UserRequest(@NotBlank @Size(max = 80) String firstName,
                              @NotBlank @Size(max = 80) String lastName,
                              @NotBlank @Email @Size(max = 150) String email,
                              @Size(max = 30) String phone,
                              @NotBlank String roleCode,
                              Boolean active,
                              @Size(min = 8, max = 100) String password) {
    }

    public record ProfileRequest(@NotBlank @Size(max = 80) String firstName,
                                 @NotBlank @Size(max = 80) String lastName,
                                 @Size(max = 30) String phone) {
    }

    public record RoleDto(Long id, String code, String label, Set<Permission> permissions) {
        public static RoleDto of(Role r) {
            return new RoleDto(r.getId(), r.getCode(), r.getLabel(), new TreeSet<>(r.getPermissions()));
        }
    }

    public record LoginLogDto(Long id, String email, boolean success, String ipAddress, String userAgent, Instant at) {
        public static LoginLogDto of(LoginLog l) {
            return new LoginLogDto(l.getId(), l.getEmail(), l.isSuccess(), l.getIpAddress(), l.getUserAgent(), l.getAt());
        }
    }
}
