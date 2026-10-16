package ma.crm.service;

import ma.crm.config.AppProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Les applications d'agenda ne savent pas envoyer de jeton JWT : le flux iCal est protégé par une
 * signature HMAC propre à l'utilisateur, intégrée à l'URL.
 */
@Service
public class CalendarFeedService {

    private final byte[] key;

    public CalendarFeedService(AppProperties props) {
        this.key = Base64.getDecoder().decode(props.jwt().secret());
    }

    public String path(Long userId) {
        return "/api/public/calendar/" + userId + "/" + sign(userId) + ".ics";
    }

    public boolean verify(Long userId, String signature) {
        return MessageDigest.isEqual(sign(userId).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(Long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(("calendar:" + userId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
