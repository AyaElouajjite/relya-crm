"""Caractéristiques d'un lead utilisées par le modèle de scoring.

Les noms doivent rester alignés avec LeadScoringService.features() côté back-end Java.
"""

CATEGORICAL = ["source", "sector", "city"]
NUMERIC = [
    "has_email",
    "has_phone",
    "has_company",
    "seniority",
    "budget",
    "marketing_consent",
    "activity_count",
    "age_days",
]
FEATURES = CATEGORICAL + NUMERIC
TARGET = "converted"

SOURCES = ["MANUEL", "FORMULAIRE_WEB", "IMPORT", "SALON", "RECOMMANDATION", "RESEAUX_SOCIAUX", "AUTRE"]
SECTORS = ["Distribution", "Informatique", "Textile", "Transport", "Cosmétique", "BTP", "Agroalimentaire",
           "Santé", "Tourisme", "Banque", "INCONNU"]
CITIES = ["Casablanca", "Rabat", "Marrakech", "Fès", "Tanger", "Agadir", "Meknès", "Oujda", "Kénitra",
          "Tétouan", "INCONNU"]
