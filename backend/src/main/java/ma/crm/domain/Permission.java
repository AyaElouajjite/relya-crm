package ma.crm.domain;

/** Permissions fines attribuées aux rôles (RBAC). */
public enum Permission {
    // Administration
    USER_MANAGE,
    SETTINGS_MANAGE,
    AUDIT_READ,
    DATA_PRIVACY,
    // Données commerciales
    CONTACT_READ,
    CONTACT_WRITE,
    CONTACT_DELETE,
    LEAD_MANAGE,
    LEAD_ASSIGN,
    OPPORTUNITY_MANAGE,
    PRODUCT_MANAGE,
    QUOTE_MANAGE,
    QUOTE_APPROVE,
    ACTIVITY_MANAGE,
    /** Voir et modifier les enregistrements de tous les commerciaux (sinon : seulement son portefeuille). */
    ALL_RECORDS,
    // Pilotage
    DASHBOARD_VIEW,
    DASHBOARD_ALL,
    OBJECTIVE_MANAGE,
    // Support
    TICKET_MANAGE,
    // Portail client
    PORTAL_ACCESS,
    // Import / export
    DATA_EXPORT,
    DATA_IMPORT
}
