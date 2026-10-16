# Relya CRM — Gestion de la relation client

![Java](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-18-DD0031?logo=angular&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-009688?logo=fastapi&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)

Application web de gestion de la relation client pour PME : prospection, ventes, service après-vente et pilotage,
réalisée d'après le cahier des charges du projet. Le dossier de conception (UML, règles métier, sécurité,
conformité) est dans [`docs/conception.md`](docs/conception.md).

| Couche | Technologie |
|---|---|
| Front-end | Angular 18 (composants standalone, signals), Tailwind CSS, Angular CDK (Kanban), Chart.js |
| Back-end | Spring Boot 3.3 (Java 17), Spring Security + JWT, JPA/Hibernate, WebSocket STOMP, OpenPDF, Apache POI |
| Base de données | PostgreSQL 16 (H2 en mode PostgreSQL pour le développement) |
| Service IA | Python 3.12, FastAPI, scikit-learn (scoring), SDK Anthropic (assistant de rédaction) |
| Déploiement | Docker Compose (nginx HTTPS, sauvegarde quotidienne), GitHub Actions |

## Fonctionnalités

| Module | Contenu |
|---|---|
| 1. Authentification | JWT révocable, BCrypt, mot de passe oublié, blocage anti-force brute, profil, journal des connexions, RBAC éditable |
| 2. Clients | Entreprises et contacts, fiche 360° (contacts, opportunités, devis, factures, historique, tickets), segmentation, import CSV, export CSV / Excel |
| 3. Leads | Saisie, formulaire web public, import, score IA, attribution, conversion en client + opportunité |
| 4. Pipeline | Kanban par glisser-déposer, montants pondérés, motif de perte obligatoire |
| 5. Devis | Catalogue, éditeur de devis, validation par le directeur au-delà d'un seuil, PDF, envoi par e-mail, partage WhatsApp, factures |
| 6. Agenda | Appels, rendez-vous, tâches ; calendrier partagé ; rappels ; historique automatique ; synchronisation iCal |
| 7. SAV | Tickets avec SLA, notes internes, notification du client ; portail client (devis, factures, demandes) |
| 8. Pilotage | CA réalisé / prévisionnel / objectifs, conversion, performance par commercial, SAV ; exports PDF et Excel |
| Valeur ajoutée | Scoring IA, assistant de rédaction, relances automatiques, notifications temps réel, intégrations, DevOps |

## Lancer en développement

Prérequis : Java 17, Maven, Node 20+, Python 3.12.

```bash
# 1. Service IA – http://localhost:8000 (le modèle s'entraîne au premier démarrage)
cd ai-service
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt        # Linux/macOS : .venv/bin/pip
.venv/Scripts/python -m uvicorn app.main:app --port 8000

# 2. API – http://localhost:8080 (base H2 dans backend/data/)
cd backend
mvn spring-boot:run

# 3. Interface – http://localhost:4200 (proxy /api et /ws vers le port 8080)
cd frontend
npm install
npm start
```

Le service IA est facultatif : sans lui, l'API calcule un score heuristique et l'assistant est indisponible.
Pour que l'assistant rédige avec Claude, définissez `ANTHROPIC_API_KEY` avant de lancer le service IA ;
sinon il renvoie un modèle de texte standard.

### Comptes de démonstration

| Profil | E-mail | Mot de passe |
|---|---|---|
| Administrateur | admin@crm.ma | Admin@2026 |
| Directeur commercial | directeur@crm.ma | Demo@2026 |
| Commerciaux | salma@crm.ma, youssef@crm.ma | Demo@2026 |
| Agent support | support@crm.ma | Demo@2026 |
| Client (portail) | client@atlas.ma | Demo@2026 |

Pages publiques : formulaire de contact `/contact`, politique de confidentialité `/confidentialite`.
Sans SMTP configuré, les e-mails (devis, rappels, liens de mot de passe) sont écrits dans la console du back-end.

- Swagger : http://localhost:8080/swagger-ui.html
- Console H2 : http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/crm`, utilisateur `sa`)

## Tests

```bash
cd backend && mvn test                                   # 19 tests (API, RBAC, modules 1 à 8, automatisations, 09-08)
cd frontend && npx ng test --watch=false --browsers=ChromeHeadless   # 7 tests
cd ai-service && .venv/Scripts/python -m pytest          # 5 tests (scoring, ré-entraînement, assistant)
```

## Déploiement Docker

```bash
cp .env.example .env              # renseigner au minimum JWT_SECRET (openssl rand -base64 48)
docker compose up -d --build
```

- Application : https://localhost:8443 (HTTP 8081 redirigé). Un certificat auto-signé est généré au premier
  démarrage ; en production, placez `crm.crt` et `crm.key` dans `./certs`.
- Sauvegarde : `pg_dump` quotidien dans `./backups`, conservé 7 jours.
- Variables utiles : `PUBLIC_URL`, `MAIL_*` (SMTP), `ANTHROPIC_API_KEY`, `SEED_DEMO_DATA=false` en production.

## Structure

```
backend/      API Spring Boot : config, security, domain, repository, service, web, dto, exception
frontend/     Angular : core (auth, API, temps réel), shared, layout, features/<module>, features/portal
ai-service/   FastAPI : app/scoring.py (modèle), app/synthetic.py (jeu Faker), app/assistant.py (API Claude)
docs/         Dossier de conception et diagrammes UML (PlantUML)
```

## Limites connues

- Les images Docker et le pipeline GitHub Actions n'ont pas pu être exécutés sur le poste de développement
  (Docker absent) ; le code applicatif a été testé en local.
- L'appel réel à l'API Claude n'a pas été exercé faute de clé ; le chemin de repli (modèle de texte) est testé.
- Le jeton JWT est stocké dans le `localStorage` ; la CSP stricte limite le risque XSS, mais un cookie `HttpOnly`
  serait plus robuste.
- Le schéma est géré par `ddl-auto: update` ; pour une mise en production durable, des migrations Flyway sont préférables.

## Équipe

Projet réalisé en binôme par :

- **Aya Elouajjite** — [github.com/AyaElouajjite](https://github.com/AyaElouajjite)
- **Anas Atmani** — [github.com/AtmaniAnas1](https://github.com/AtmaniAnas1)
