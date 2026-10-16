import os

import pytest
from fastapi.testclient import TestClient


@pytest.fixture(scope="module")
def client(tmp_path_factory):
    os.environ["MODEL_DIR"] = str(tmp_path_factory.mktemp("models"))
    os.environ.pop("ANTHROPIC_API_KEY", None)
    from app.main import app

    with TestClient(app) as c:
        yield c


def lead(**overrides):
    base = {"source": "FORMULAIRE_WEB", "sector": "Informatique", "city": "Casablanca", "has_email": 1,
            "has_phone": 1, "has_company": 1, "seniority": 2, "budget": 50000, "marketing_consent": 1,
            "activity_count": 3, "age_days": 10}
    base.update(overrides)
    return base


def test_model_is_trained_on_synthetic_data_at_startup(client):
    info = client.get("/model").json()
    assert info["source"] == "synthetic"
    assert info["metrics"]["auc"] > 0.65


def test_scores_are_probabilities_and_follow_business_signals(client):
    strong = lead(source="RECOMMANDATION", seniority=3, budget=300000, activity_count=8, age_days=5)
    weak = lead(source="IMPORT", has_phone=0, has_company=0, seniority=0, budget=0, activity_count=0, age_days=200)
    scores = client.post("/score", json={"leads": [strong, weak]}).json()["scores"]
    assert all(0 <= s <= 1 for s in scores)
    assert scores[0] > scores[1]


def test_unknown_categories_do_not_break_scoring(client):
    res = client.post("/score", json={"leads": [lead(sector="Spatial", city="Dakhla")]})
    assert res.status_code == 200


def test_retrain_with_crm_history(client):
    rows = [dict(lead(activity_count=i % 6), converted=i % 3 == 0) for i in range(40)]
    rows = [{**r, "converted": int(r["converted"])} for r in rows]
    meta = client.post("/train", json={"rows": rows}).json()
    assert meta["source"] == "crm+synthetic"
    assert meta["rows"] == 1040


def test_draft_email_falls_back_to_template_without_api_key(client):
    res = client.post("/draft-email", json={"purpose": "devis", "context": {
        "contact_name": "Omar", "quote_number": "DEV-2026-0001", "sender_name": "Salma"}})
    body = res.json()
    assert res.status_code == 200
    assert body["generated"] is False
    assert "DEV-2026-0001" in body["text"]
    assert body["text"].startswith("Objet :")
