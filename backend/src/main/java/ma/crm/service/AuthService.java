package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.config.AppProperties;
import ma.crm.domain.LoginLog;
import ma.crm.domain.PasswordResetToken;
import ma.crm.domain.User;
import ma.crm.dto.AuthDtos.*;
import ma.crm.dto.UserDtos.UserDto;
import ma.crm.exception.BusinessException;
import ma.crm.repository.LoginLogRepository;
import ma.crm.repository.PasswordResetTokenRepository;
import ma.crm.repository.UserRepository;
import ma.crm.security.AppUserDetails;
import ma.crm.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    /** Au-delà de ce nombre d'échecs sur la fenêtre, le compte est temporairement bloqué. */
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final long LOCK_WINDOW_MINUTES = 15;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository users;
    private final LoginLogRepository loginLogs;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    @Transactional(noRollbackFor = AuthenticationException.class)
    public AuthResponse login(LoginRequest req, String ip, String userAgent) {
        String email = req.email().trim().toLowerCase();
        Instant since = Instant.now().minus(LOCK_WINDOW_MINUTES, ChronoUnit.MINUTES);
        if (loginLogs.countByEmailIgnoreCaseAndSuccessFalseAndAtAfter(email, since) >= MAX_FAILED_ATTEMPTS) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives échouées. Réessayez dans " + LOCK_WINDOW_MINUTES + " minutes.");
        }
        try {
            var auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, req.password()));
            AppUserDetails principal = (AppUserDetails) auth.getPrincipal();
            User user = users.findById(principal.id()).orElseThrow();
            user.setLastLoginAt(Instant.now());
            recordLogin(email, true, ip, userAgent);
            JwtService.IssuedToken token = jwtService.generate(principal);
            return new AuthResponse(token.token(), token.expiresAt(), UserDto.of(user));
        } catch (AuthenticationException e) {
            recordLogin(email, false, ip, userAgent);
            throw e;
        }
    }

    private void recordLogin(String email, boolean success, String ip, String userAgent) {
        LoginLog entry = new LoginLog();
        entry.setEmail(email);
        entry.setSuccess(success);
        entry.setIpAddress(ip);
        entry.setUserAgent(userAgent == null || userAgent.length() <= 255 ? userAgent : userAgent.substring(0, 255));
        loginLogs.save(entry);
    }

    /** Ne révèle jamais si l'adresse existe, pour éviter l'énumération des comptes. */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        users.findByEmailIgnoreCase(req.email().trim()).filter(User::isActive).ifPresent(user -> {
            String link = passwordLink(user, 1);
            mailService.send(user.getEmail(), "Réinitialisation de votre mot de passe",
                    "Bonjour " + user.getFirstName() + ",\n\nPour choisir un nouveau mot de passe, ouvrez ce lien "
                            + "(valable 1 heure) :\n" + link + "\n\nSi vous n'êtes pas à l'origine de cette demande, "
                            + "ignorez ce message.");
        });
    }

    /** Invitation d'un client au portail : il choisit lui-même son mot de passe (lien valable 72 h). */
    @Transactional
    public void sendPortalInvitation(User user, String companyName) {
        String link = passwordLink(user, 72);
        mailService.send(user.getEmail(), "Votre espace client " + companyName,
                "Bonjour " + user.getFirstName() + ",\n\nUn espace client a été ouvert pour vous. Vous pourrez y "
                        + "consulter vos devis et factures et suivre vos demandes de support.\n\n"
                        + "Pour activer votre accès, choisissez votre mot de passe (lien valable 72 heures) :\n" + link
                        + "\n\nVotre identifiant est : " + user.getEmail());
    }

    private String passwordLink(User user, int validityHours) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        PasswordResetToken token = new PasswordResetToken();
        token.setToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
        token.setUser(user);
        token.setExpiresAt(Instant.now().plus(validityHours, ChronoUnit.HOURS));
        resetTokens.save(token);
        return props.frontendUrl() + "/reset-password?token=" + token.getToken();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken token = resetTokens.findByToken(req.token())
                .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessException("Lien invalide ou expiré"));
        token.setUsed(true);
        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
    }

    /**
     * Change le mot de passe et révoque toutes les autres sessions. Un nouveau jeton est renvoyé
     * pour que la session courante continue sans reconnexion.
     */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest req) {
        User user = users.findById(userId).orElseThrow();
        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw new BusinessException("Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        JwtService.IssuedToken token = jwtService.generate(AppUserDetails.of(user));
        return new AuthResponse(token.token(), token.expiresAt(), UserDto.of(user));
    }

    /** Déconnexion côté serveur : tous les jetons déjà émis pour ce compte deviennent invalides. */
    @Transactional
    public void logout(Long userId) {
        users.findById(userId).ifPresent(u -> u.setTokenVersion(u.getTokenVersion() + 1));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return UserDto.of(users.findById(userId).orElseThrow());
    }
}
