"""Service IA du CRM : scoring des leads et assistant de rédaction."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from typing import Literal

import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

from . import assistant, scoring
from .features import FEATURES, TARGET

logging.basicConfig(level=logging.INFO)


@asynccontextmanager
async def lifespan(_: FastAPI):
    scoring.load()
    yield


app = FastAPI(title="CRM — service IA", version="1.0.0", lifespan=lifespan)


class LeadFeatures(BaseModel):
    source: str = "AUTRE"
    sector: str = "INCONNU"
    city: str = "INCONNU"
    has_email: int = 0
    has_phone: int = 0
    has_company: int = 0
    seniority: int = Field(0, ge=0, le=3)
    budget: float = Field(0, ge=0)
    marketing_consent: int = 0
    activity_count: int = Field(0, ge=0)
    age_days: int = Field(0, ge=0)


class ScoreRequest(BaseModel):
    leads: list[LeadFeatures] = Field(min_length=1, max_length=1000)


class TrainingRow(LeadFeatures):
    converted: int = Field(ge=0, le=1)


class TrainRequest(BaseModel):
    rows: list[TrainingRow] = Field(min_length=scoring.MIN_ROWS)
    # Complète l'historique réel avec le jeu synthétique tant qu'il est peu fourni.
    blend_synthetic: bool = True


class DraftRequest(BaseModel):
    purpose: Literal["relance", "devis", "remerciement", "prise_de_contact"] = "relance"
    tone: str | None = Field(None, max_length=60)
    context: dict[str, str | float | int | None] = {}


@app.get("/health")
def health():
    return {"status": "ok", "model": scoring.info().get("version")}


@app.get("/model")
def model_info():
    return scoring.info()


@app.post("/score")
def score(req: ScoreRequest):
    probabilities = scoring.score([lead.model_dump() for lead in req.leads])
    return {"scores": probabilities, "model": scoring.info().get("version")}


@app.post("/train")
def train(req: TrainRequest):
    real = pd.DataFrame([r.model_dump() for r in req.rows])
    df = real
    source = "crm"
    if req.blend_synthetic and len(real) < 500:
        from . import synthetic

        df = pd.concat([real, synthetic.generate(1000).drop(columns=["name"])], ignore_index=True)
        source = "crm+synthetic"
    try:
        meta = scoring.train(df[[*FEATURES, TARGET]], source=source)
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    return meta


@app.post("/draft-email")
def draft_email(req: DraftRequest):
    try:
        return assistant.draft_email(req.purpose, req.context, req.tone)
    except assistant.AssistantUnavailable as e:
        raise HTTPException(status_code=503, detail=str(e)) from e
