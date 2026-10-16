package ma.crm.service;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.AppSetting;
import ma.crm.exception.BusinessException;
import ma.crm.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Paramètres de la plateforme (en-tête des PDF, seuils, règles d'automatisation, conservation des données). */
@Service
@RequiredArgsConstructor
public class SettingsService {

    public static final String COMPANY_NAME = "company.name";
    public static final String COMPANY_ADDRESS = "company.address";
    public static final String COMPANY_ICE = "company.ice";
    public static final String COMPANY_PHONE = "company.phone";
    public static final String COMPANY_EMAIL = "company.email";
    public static final String QUOTE_APPROVAL_THRESHOLD = "quote.approvalThreshold";
    public static final String QUOTE_VALIDITY_DAYS = "quote.validityDays";
    public static final String QUOTE_CONDITIONS = "quote.conditions";
    public static final String INVOICE_PAYMENT_DAYS = "invoice.paymentDays";
    public static final String FOLLOWUP_INACTIVITY_DAYS = "automation.inactivityDays";
    public static final String LEAD_RETENTION_MONTHS = "privacy.leadRetentionMonths";

    /** Valeurs par défaut, dans l'ordre d'affichage de l'écran Paramètres. */
    static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(COMPANY_NAME, "Ma Société SARL");
        DEFAULTS.put(COMPANY_ADDRESS, "Casablanca, Maroc");
        DEFAULTS.put(COMPANY_ICE, "");
        DEFAULTS.put(COMPANY_PHONE, "");
        DEFAULTS.put(COMPANY_EMAIL, "contact@crm.ma");
        DEFAULTS.put(QUOTE_APPROVAL_THRESHOLD, "100000");
        DEFAULTS.put(QUOTE_VALIDITY_DAYS, "30");
        DEFAULTS.put(QUOTE_CONDITIONS, "Paiement à 30 jours fin de mois. Devis valable pour la durée indiquée.");
        DEFAULTS.put(INVOICE_PAYMENT_DAYS, "30");
        DEFAULTS.put(FOLLOWUP_INACTIVITY_DAYS, "7");
        DEFAULTS.put(LEAD_RETENTION_MONTHS, "24");
    }

    private final AppSettingRepository repository;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> result = new LinkedHashMap<>(DEFAULTS);
        repository.findAll().forEach(s -> result.put(s.getKey(), s.getValue()));
        return result;
    }

    @Transactional(readOnly = true)
    public String get(String key) {
        return repository.findById(key).map(AppSetting::getValue).orElse(DEFAULTS.getOrDefault(key, ""));
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key).trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(DEFAULTS.get(key));
        }
    }

    public BigDecimal getDecimal(String key) {
        try {
            return new BigDecimal(get(key).trim());
        } catch (NumberFormatException e) {
            return new BigDecimal(DEFAULTS.get(key));
        }
    }

    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        values.forEach((key, value) -> {
            if (!DEFAULTS.containsKey(key)) {
                throw new BusinessException("Paramètre inconnu : " + key);
            }
            repository.save(new AppSetting(key, value == null ? "" : value.trim()));
        });
        audit.log(AuditService.UPDATE, "Settings", null, String.join(", ", values.keySet()));
        return all();
    }
}
