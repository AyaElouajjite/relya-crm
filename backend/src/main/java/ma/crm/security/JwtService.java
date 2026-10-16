package ma.crm.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ma.crm.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.jwt().secret()));
        this.expirationMinutes = props.jwt().expirationMinutes();
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    /** Contenu utile d'un jeton valide. */
    public record TokenClaims(String email, int version) {
    }

    public IssuedToken generate(AppUserDetails user) {
        Instant now = Instant.now();
        Instant exp = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(user.email())
                .claim("uid", user.id())
                .claim("role", user.roleCode())
                .claim("ver", user.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, exp);
    }

    /** Retourne le sujet et la version si la signature est valide et le jeton non expiré. */
    public Optional<TokenClaims> validate(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Integer version = claims.get("ver", Integer.class);
            if (claims.getSubject() == null || version == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(claims.getSubject(), version));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
