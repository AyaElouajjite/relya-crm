"""Assistant de rédaction : brouillons d'e-mails de relance via l'API Claude.

Sans clé d'API configurée (ANTHROPIC_API_KEY), un modèle de texte simple est renvoyé
pour que la fonctionnalité reste utilisable en démonstration.
"""

from __future__ import annotations

import logging
import os

import anthropic

log = logging.getLogger(__name__)

MODEL = os.environ.get("ASSISTANT_MODEL", "claude-opus-5-5")

SYSTEM_PROMPT = """Tu es l'assistant commercial d'une PME marocaine. Tu rédiges des brouillons d'e-mails \
professionnels en français, que le commercial relira avant envoi.

Règles :
- Ton courtois, chaleureux et direct ; vouvoiement.
- 80 à 160 mots, sans jargon ni formules creuses.
- Appuie-toi uniquement sur le contexte fourni : n'invente ni chiffres, ni dates, ni engagements.
- Termine par une proposition d'action concrète (appel, rendez-vous, réponse au devis).
- Réponds avec exactement ce format :
Objet : <objet de l'e-mail>

<corps de l'e-mail, signé du nom du commercial>"""

PURPOSES = {
    "relance": "relancer le client après une période sans nouvelles",
    "devis": "relancer le client au sujet d'un devis envoyé",
    "remerciement": "remercier le client après un rendez-vous ou une commande",
    "prise_de_contact": "prendre un premier contact avec un prospect",
}


class AssistantUnavailable(Exception):
    """L'API de langage n'a pas pu produire de brouillon (erreur réseau, quota, refus)."""


def _client() -> anthropic.Anthropic | None:
    if not os.environ.get("ANTHROPIC_API_KEY") and not os.environ.get("ANTHROPIC_AUTH_TOKEN"):
        return None
    return anthropic.Anthropic(timeout=60.0, max_retries=2)


def _context_text(ctx: dict) -> str:
    labels = {
        "contact_name": "Interlocuteur", "company": "Entreprise", "opportunity": "Opportunité",
        "amount": "Montant", "stage": "Étape", "last_interaction": "Dernière interaction",
        "quote_number": "Devis", "notes": "Notes du commercial", "sender_name": "Commercial (signataire)",
        "sender_company": "Société du commercial",
    }
    return "\n".join(f"- {labels.get(k, k)} : {v}" for k, v in ctx.items() if v not in (None, ""))


def draft_email(purpose: str, context: dict, tone: str | None = None) -> dict:
    goal = PURPOSES.get(purpose, PURPOSES["relance"])
    client = _client()
    if client is None:
        return {"text": _template(purpose, context), "model": "modele-local", "generated": False}

    instruction = f"Objectif de l'e-mail : {goal}.\n"
    if tone:
        instruction += f"Ton souhaité : {tone}.\n"
    instruction += f"\nContexte CRM :\n{_context_text(context)}"

    try:
        response = client.beta.messages.create(
            model=MODEL,
            max_tokens=2000,
            system=SYSTEM_PROMPT,
            output_config={"effort": "low"},
            # Repli serveur si le modèle principal décline la demande.
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
            messages=[{"role": "user", "content": instruction}],
        )
    except anthropic.RateLimitError as e:
        log.warning("Quota de l'API atteint : %s", e)
        raise AssistantUnavailable("L'assistant est momentanément saturé, réessayez dans un instant") from e
    except anthropic.AuthenticationError as e:
        log.error("Clé d'API invalide : %s", e)
        raise AssistantUnavailable("L'assistant n'est pas correctement configuré") from e
    except anthropic.APIStatusError as e:
        log.error("Erreur de l'API (%s) : %s", e.status_code, e.message)
        raise AssistantUnavailable("L'assistant a rencontré une erreur") from e
    except anthropic.APIConnectionError as e:
        log.error("API injoignable : %s", e)
        raise AssistantUnavailable("L'assistant est injoignable") from e

    if response.stop_reason == "refusal":
        raise AssistantUnavailable("L'assistant n'a pas pu rédiger ce message")
    text = "".join(block.text for block in response.content if block.type == "text").strip()
    if not text:
        raise AssistantUnavailable("Réponse vide de l'assistant")
    return {"text": text, "model": response.model, "generated": True}


def _template(purpose: str, ctx: dict) -> str:
    """Brouillon de secours sans API de langage."""
    name = ctx.get("contact_name") or "Madame, Monsieur"
    sender = ctx.get("sender_name") or ""
    company = ctx.get("sender_company") or ""
    subject_ref = ctx.get("opportunity") or ctx.get("quote_number") or "notre échange"
    if purpose == "devis":
        body = (f"Je reviens vers vous au sujet de notre devis {ctx.get('quote_number', '')}. "
                "Avez-vous pu en prendre connaissance ? Je reste disponible pour l'ajuster à vos besoins "
                "ou en discuter lors d'un court appel cette semaine.")
        subject = f"Votre devis {ctx.get('quote_number', '')}".strip()
    elif purpose == "remerciement":
        body = ("Je tenais à vous remercier pour votre confiance et pour la qualité de nos échanges. "
                "N'hésitez pas à me solliciter pour toute question.")
        subject = "Merci pour votre confiance"
    elif purpose == "prise_de_contact":
        body = (f"Je me permets de vous contacter de la part de {company}. Nous accompagnons des entreprises "
                "comme la vôtre et je serais ravi d'échanger quelques minutes sur vos besoins.")
        subject = f"Présentation {company}".strip()
    else:
        body = (f"Je me permets de revenir vers vous concernant {subject_ref}. Où en est votre réflexion ? "
                "Seriez-vous disponible pour un point téléphonique dans les prochains jours ?")
        subject = f"Suite à {subject_ref}"
    return f"Objet : {subject}\n\nBonjour {name},\n\n{body}\n\nBien cordialement,\n{sender}\n{company}".rstrip()
