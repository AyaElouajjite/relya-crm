package ma.crm.security;

import lombok.RequiredArgsConstructor;
import ma.crm.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(AppUserDetails::of)
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }

    @Transactional(readOnly = true)
    public Optional<AppUserDetails> findActive(String email) {
        return users.findByEmailIgnoreCase(email).map(AppUserDetails::of).filter(AppUserDetails::isEnabled);
    }
}
