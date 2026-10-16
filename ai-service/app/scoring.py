"""Modèle de scoring des leads (scikit-learn)."""

from __future__ import annotations

import json
import os
import threading
from datetime import datetime, timezone
from pathlib import Path

import joblib
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import GradientBoostingClassifier
from sklearn.metrics import accuracy_score, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from . import synthetic
from .features import CATEGORICAL, FEATURES, NUMERIC, TARGET

MODEL_DIR = Path(os.environ.get("MODEL_DIR", Path(__file__).resolve().parent.parent / "models"))
MODEL_PATH = MODEL_DIR / "lead_scoring.joblib"
META_PATH = MODEL_DIR / "lead_scoring.json"
MIN_ROWS = 20

_lock = threading.Lock()
_model: Pipeline | None = None
_meta: dict = {}


def build_pipeline() -> Pipeline:
    pre = ColumnTransformer([
        ("cat", OneHotEncoder(handle_unknown="ignore"), CATEGORICAL),
        ("num", StandardScaler(), NUMERIC),
    ])
    clf = GradientBoostingClassifier(n_estimators=150, max_depth=3, learning_rate=0.08, random_state=42)
    return Pipeline([("pre", pre), ("clf", clf)])


def train(df: pd.DataFrame, source: str) -> dict:
    """Entraîne, évalue sur 20 % des données, puis ré-entraîne sur l'ensemble et sauvegarde."""
    if len(df) < MIN_ROWS:
        raise ValueError(f"Au moins {MIN_ROWS} lignes sont nécessaires ({len(df)} reçues)")
    if df[TARGET].nunique() < 2:
        raise ValueError("Les données doivent contenir des leads convertis et non convertis")
    X, y = df[FEATURES], df[TARGET].astype(int)
    stratify = y if y.value_counts().min() >= 2 else None
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=stratify)
    pipeline = build_pipeline().fit(X_train, y_train)
    proba = pipeline.predict_proba(X_test)[:, 1]
    metrics = {
        "auc": round(float(roc_auc_score(y_test, proba)), 3) if y_test.nunique() > 1 else None,
        "accuracy": round(float(accuracy_score(y_test, proba >= 0.5)), 3),
    }
    final = build_pipeline().fit(X, y)
    meta = {
        "source": source,
        "rows": int(len(df)),
        "conversion_rate": round(float(y.mean()), 3),
        "metrics": metrics,
        "trained_at": datetime.now(timezone.utc).isoformat(),
        "version": f"gb-{datetime.now(timezone.utc):%Y%m%d%H%M%S}",
    }
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    with _lock:
        joblib.dump(final, MODEL_PATH)
        META_PATH.write_text(json.dumps(meta, indent=2), encoding="utf-8")
        global _model, _meta
        _model, _meta = final, meta
    return meta


def load() -> None:
    """Charge le modèle sauvegardé, ou en entraîne un sur des données synthétiques au premier démarrage."""
    global _model, _meta
    if MODEL_PATH.exists():
        with _lock:
            _model = joblib.load(MODEL_PATH)
            _meta = json.loads(META_PATH.read_text(encoding="utf-8")) if META_PATH.exists() else {}
    else:
        train(synthetic.generate(), source="synthetic")


def score(rows: list[dict]) -> list[float]:
    if _model is None:
        load()
    df = pd.DataFrame(rows)
    for col in FEATURES:
        if col not in df:
            df[col] = "INCONNU" if col in CATEGORICAL else 0
    with _lock:
        return [round(float(p), 4) for p in _model.predict_proba(df[FEATURES])[:, 1]]


def info() -> dict:
    if _model is None:
        load()
    return dict(_meta)
