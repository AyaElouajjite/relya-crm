"""Jeu de données réaliste pour l'entraînement initial (risque « données insuffisantes pour l'IA »).

Les leads sont générés avec Faker ; la probabilité de conversion suit des règles métier
plausibles (recommandation > salon > formulaire web, budget, séniorité de l'interlocuteur,
nombre d'interactions, ancienneté du lead), plus du bruit, pour que le modèle ait
quelque chose de non trivial à apprendre.
"""

import math
import random

import pandas as pd
from faker import Faker

from .features import CITIES, SECTORS, SOURCES

SOURCE_WEIGHT = {
    "RECOMMANDATION": 1.4,
    "SALON": 0.8,
    "FORMULAIRE_WEB": 0.6,
    "RESEAUX_SOCIAUX": 0.1,
    "MANUEL": 0.3,
    "IMPORT": -0.4,
    "AUTRE": 0.0,
}
SECTOR_WEIGHT = {"Informatique": 0.4, "Santé": 0.3, "Banque": 0.3, "Distribution": 0.2, "Tourisme": -0.2,
                 "Textile": -0.1, "INCONNU": -0.5}


def _sigmoid(x: float) -> float:
    return 1 / (1 + math.exp(-x))


def generate(n: int = 2000, seed: int = 42) -> pd.DataFrame:
    fake = Faker("fr_FR")
    Faker.seed(seed)
    rng = random.Random(seed)
    rows = []
    for _ in range(n):
        source = rng.choices(SOURCES, weights=[20, 25, 15, 10, 8, 15, 7])[0]
        sector = rng.choice(SECTORS)
        city = rng.choices(CITIES, weights=[30, 15, 10, 8, 8, 6, 5, 4, 4, 4, 6])[0]
        has_email = int(rng.random() < 0.9)
        has_phone = int(rng.random() < 0.7)
        has_company = int(rng.random() < 0.85)
        seniority = rng.choices([0, 1, 2, 3], weights=[15, 40, 30, 15])[0]
        budget = 0.0 if rng.random() < 0.35 else round(rng.lognormvariate(10.3, 1.0), -2)
        consent = int(rng.random() < 0.55)
        activities = min(15, int(rng.expovariate(0.35)))
        age = rng.randint(0, 240)

        logit = -2.2
        logit += SOURCE_WEIGHT[source] + SECTOR_WEIGHT.get(sector, 0.0)
        logit += 0.3 * has_email + 0.5 * has_phone + 0.6 * has_company
        logit += 0.45 * seniority
        logit += 0.35 * math.log10(1 + budget) - 0.9 if budget else -0.3
        logit += 0.25 * activities
        logit += 0.3 * consent
        logit -= 0.008 * age
        logit += rng.gauss(0, 0.6)
        converted = int(rng.random() < _sigmoid(logit))

        rows.append({
            "name": fake.name(),  # non utilisé par le modèle : rend le jeu lisible
            "source": source, "sector": sector, "city": city,
            "has_email": has_email, "has_phone": has_phone, "has_company": has_company,
            "seniority": seniority, "budget": budget, "marketing_consent": consent,
            "activity_count": activities, "age_days": age, "converted": converted,
        })
    return pd.DataFrame(rows)


if __name__ == "__main__":
    df = generate()
    df.to_csv("synthetic_leads.csv", index=False)
    print(f"{len(df)} leads générés, taux de conversion {df.converted.mean():.1%}")
