package ma.crm.security;

import ma.crm.domain.Permission;
import ma.crm.domain.User;
import ma.crm.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static Optional<AppUserDetails> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AppUserDetails require() {
        return get().orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Authentification requise"));
    }

    public static Long id() {
        return get().map(AppUserDetails::id).orElse(null);
    }

    public static String email() {
        return get().map(AppUserDetails::email).orElse("system");
    }

    public static boolean has(Permission permission) {
        return get().map(u -> u.has(permission)).orElse(false);
    }

    /** Vrai si l'utilisateur voit tout (directeur, admin) ou s'il est le responsable de l'enregistrement. */
    public static boolean canAccess(User owner) {
        return has(Permission.ALL_RECORDS) || owner == null || Objects.equals(owner.getId(), id());
    }

    /** Un commercial ne modifie que son portefeuille ; le directeur et l'administrateur, tout. */
    public static void checkOwnership(User owner) {
        if (!canAccess(owner)) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                    "Cet enregistrement appartient au portefeuille de " + owner.getFullName());
        }
    }
}
