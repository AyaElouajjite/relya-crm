package ma.crm.domain;

/** Énumérations métier regroupées pour garder le package lisible. */
public final class Enums {
    private Enums() {
    }

    public enum LeadSource { MANUEL, FORMULAIRE_WEB, IMPORT, SALON, RECOMMANDATION, RESEAUX_SOCIAUX, AUTRE }

    public enum LeadStatus { NOUVEAU, CONTACTE, QUALIFIE, NON_QUALIFIE, CONVERTI }

    public enum OpportunityStage {
        PROSPECTION(10), QUALIFICATION(25), PROPOSITION(50), NEGOCIATION(75), GAGNEE(100), PERDUE(0);

        /** Probabilité proposée par défaut à l'entrée dans l'étape. */
        public final int defaultProbability;

        OpportunityStage(int defaultProbability) {
            this.defaultProbability = defaultProbability;
        }

        public boolean isClosed() {
            return this == GAGNEE || this == PERDUE;
        }
    }

    public enum LossReason { PRIX, CONCURRENT, BUDGET, TIMING, BESOIN_NON_CONFIRME, SANS_REPONSE, AUTRE }

    public enum ProductType { PRODUIT, SERVICE }

    /** EN_VALIDATION : devis au-dessus du seuil, en attente de l'accord du directeur commercial. */
    public enum QuoteStatus { BROUILLON, EN_VALIDATION, VALIDE, ENVOYE, ACCEPTE, REFUSE }

    public enum InvoiceStatus { EMISE, PAYEE, ANNULEE }

    public enum ActivityType { APPEL, EMAIL, REUNION, TACHE, NOTE, SYSTEME }

    public enum ActivityStatus { A_FAIRE, TERMINEE, ANNULEE }

    public enum TicketPriority {
        BASSE(72), NORMALE(48), HAUTE(24), URGENTE(4);

        /** Délai de traitement cible (SLA) en heures. */
        public final int slaHours;

        TicketPriority(int slaHours) {
            this.slaHours = slaHours;
        }
    }

    public enum TicketStatus {
        OUVERT, EN_COURS, EN_ATTENTE_CLIENT, RESOLU, FERME;

        public boolean isOpen() {
            return this != RESOLU && this != FERME;
        }
    }

    public enum TicketChannel { PORTAIL, EMAIL, TELEPHONE, AGENT }
}
