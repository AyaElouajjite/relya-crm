package ma.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, String frontendUrl, boolean seedDemoData) {

    /** @param secret clé HMAC encodée en Base64 (au moins 256 bits) */
    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
