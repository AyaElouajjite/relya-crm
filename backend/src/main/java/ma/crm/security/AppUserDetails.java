package ma.crm.security;

import ma.crm.domain.Permission;
import ma.crm.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Principal Spring Security. Les autorités contiennent le rôle (ROLE_ADMIN, ...)
 * et chaque permission fine (CONTACT_WRITE, ...), ce qui permet d'écrire
 * {@code hasRole('ADMIN')} ou {@code hasAuthority('CONTACT_WRITE')}.
 *
 * @param contactId pour un compte du portail client, le contact représenté
 * @param companyId pour un compte du portail client, l'entreprise du contact
 */
public record AppUserDetails(Long id, String email, String password, boolean active, String roleCode,
                             int tokenVersion, Long contactId, Long companyId,
                             List<GrantedAuthority> authorities) implements UserDetails {

    public static AppUserDetails of(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));
        user.getRole().getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        Long contactId = user.getContact() == null ? null : user.getContact().getId();
        Long companyId = user.getContact() == null || user.getContact().getCompany() == null
                ? null : user.getContact().getCompany().getId();
        return new AppUserDetails(user.getId(), user.getEmail(), user.getPassword(), user.isActive(),
                user.getRole().getCode(), user.getTokenVersion(), contactId, companyId, authorities);
    }

    public boolean has(Permission permission) {
        return authorities.stream().anyMatch(a -> a.getAuthority().equals(permission.name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
