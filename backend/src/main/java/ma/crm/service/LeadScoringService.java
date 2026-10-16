package ma.crm.service;

import ma.crm.domain.Enums.LeadStatus;
import ma.crm.domain.Lead;
import ma.crm.repository.ActivityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scoring des leads par IA : appelle le service Python (FastAPI + scikit-learn). Si le service
 * est indisponible, un score heuristique simple est calculé pour ne jamais bloquer le commercial.
 */
@Service
public class LeadScoringService {

    private static final Logger log = LoggerFactory.getLogger(LeadScoringService.class);

    private final RestClient client;
    private final boolean enabled;
    private final ActivityRepository activities;

    public LeadScoringService(@Value("${app.ai.url:}") String aiUrl, ActivityRepository activities) {
        this.activities = activities;
        this.enabled = StringUtils.hasText(aiUrl);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl(enabled ? aiUrl : "http://localhost").requestFactory(factory).build();
    }

    public record ScoreResult(int score, String model) {
    }

    /** Caractéristiques envoyées au modèle ; mêmes noms que dans ai-service/features.py. */
    public Map<String, Object> features(Lead lead) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("source", lead.getSource().name());
        f.put("sector", lead.getSector() == null ? "INCONNU" : lead.getSector());
        f.put("city", lead.getCity() == null ? "INCONNU" : lead.getCity());
        f.put("has_email", StringUtils.hasText(lead.getEmail()) ? 1 : 0);
        f.put("has_phone", StringUtils.hasText(lead.getPhone()) ? 1 : 0);
        f.put("has_company", StringUtils.hasText(lead.getCompanyName()) ? 1 : 0);
        f.put("seniority", seniority(lead.getPosition()));
        f.put("budget", lead.getEstimatedBudget() == null ? 0.0 : lead.getEstimatedBudget().doubleValue());
        f.put("marketing_consent", lead.isMarketingConsent() ? 1 : 0);
        f.put("activity_count", lead.getId() == null ? 0 : activities.countByLead_Id(lead.getId()));
        Instant created = lead.getCreatedAt() == null ? Instant.now() : lead.getCreatedAt();
        f.put("age_days", Math.max(0, Duration.between(created, Instant.now()).toDays()));
        return f;
    }

    public ScoreResult score(Lead lead) {
        Map<String, Object> features = features(lead);
        if (enabled) {
            try {
                Map<?, ?> res = client.post().uri("/score").contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("leads", List.of(features))).retrieve().body(Map.class);
                List<?> scores = (List<?>) res.get("scores");
                return new ScoreResult((int) Math.round(((Number) scores.get(0)).doubleValue() * 100),
                        String.valueOf(res.get("model")));
            } catch (RuntimeException e) {
                log.warn("Service IA indisponible, score heuristique utilisé : {}", e.getMessage());
            }
        }
        return new ScoreResult(heuristic(features), "heuristique");
    }

    /** Ré-entraîne le modèle sur l'historique réel du CRM (leads convertis ou non qualifiés). */
    public Map<?, ?> retrain(List<Lead> history) {
        if (!enabled) {
            throw new ma.crm.exception.BusinessException("Le service IA n'est pas configuré (app.ai.url)");
        }
        List<Map<String, Object>> rows = history.stream().map(l -> {
            Map<String, Object> row = features(l);
            row.put("converted", l.getStatus() == LeadStatus.CONVERTI ? 1 : 0);
            return row;
        }).toList();
        return client.post().uri("/train").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("rows", rows)).retrieve().body(Map.class);
    }

    static int seniority(String position) {
        if (!StringUtils.hasText(position)) {
            return 0;
        }
        String p = position.toLowerCase();
        if (p.matches(".*(ceo|pdg|directeur g|gérant|fondateur|président|dg\\b).*")) {
            return 3;
        }
        if (p.matches(".*(directeur|director|head|responsable|chef|manager).*")) {
            return 2;
        }
        return 1;
    }

    static int heuristic(Map<String, Object> f) {
        double s = 10;
        s += switch ((String) f.get("source")) {
            case "RECOMMANDATION" -> 25;
            case "SALON" -> 15;
            case "FORMULAIRE_WEB" -> 12;
            case "RESEAUX_SOCIAUX" -> 6;
            default -> 8;
        };
        s += 6 * (int) f.get("has_email") + 6 * (int) f.get("has_phone") + 8 * (int) f.get("has_company");
        s += 6 * (int) f.get("seniority");
        double budget = (double) f.get("budget");
        s += budget >= 100_000 ? 15 : budget >= 20_000 ? 10 : budget > 0 ? 5 : 0;
        s += Math.min(10, 2 * ((Number) f.get("activity_count")).intValue());
        s -= Math.min(15, ((Number) f.get("age_days")).longValue() / 10);
        return (int) Math.max(1, Math.min(99, Math.round(s)));
    }
}
