# Dossier de conception — CRM

Ce document fait le lien entre le cahier des charges et la réalisation. Les diagrammes UML sont dans
[`docs/uml/`](uml/) au format PlantUML (rendu avec l'extension PlantUML de VS Code, IntelliJ, ou sur plantuml.com).

| Diagramme | Fichier |
|---|---|
| Cas d'utilisation | [`uml/cas-utilisation.puml`](uml/cas-utilisation.puml) |
| Classes (modèle de données) | [`uml/classes.puml`](uml/classes.puml) |
| Séquences (authentification, lead, devis, relances) | [`uml/sequences.puml`](uml/sequences.puml) |

## 1. Architecture

```
Navigateur ──HTTPS──▶ nginx (Angular + reverse proxy) ──▶ API Spring Boot ──SQL──▶ PostgreSQL
                                     │ /ws (WebSocket STOMP)      │
                                     └──────────────────────────▶ ├──HTTP──▶ Service IA (FastAPI, scikit-learn, API Claude)
                                                                  └──SMTP──▶ Serveur de messagerie
```

* Le front-end n'accède jamais à la base : toutes les requêtes passent par l'API, qui contrôle les droits de chaque rôle.
* L'API est organisée en couches : `web` (contrôleurs REST) → `service` (règles métier, transactions) →
  `repository` (Spring Data JPA) → `domain` (entités). Les échanges utilisent des DTO (`dto`), jamais les entités.
* Le service IA est séparé : s'il est indisponible, l'API calcule un score heuristique et l'assistant renvoie une erreur claire.

### Correspondance des noms (cahier des charges → code)

Le code suit la convention anglaise ; les libellés de l'interface sont en français.

| Cahier des charges | Code |
|---|---|
| Utilisateur, Rôle | `User`, `Role` |
| Entreprise (raisonSociale, ICE) | `Company` (`name`, `ice`) |
| Contact | `Contact` |
| Lead (dateCréation) | `Lead` (`createdAt`) |
| Opportunité (étape, dateClôture) | `Opportunity` (`stage`, `expectedCloseDate`) |
| Produit (prixHT, tauxTVA) | `Product` (`priceHT`, `vatRate`) |
| Devis, LigneDevis (prixUnitaire, remise) | `Quote`, `QuoteLine` (`unitPrice`, `discount`) |
| Activité (sujet, date) | `Activity` (`subject`, `dueAt`) |
| Ticket (objet, dateOuverture, dateRésolution) | `Ticket` (`subject`, `openedAt`, `resolvedAt`) |
| Ajouts | `Invoice` (facture), `TicketMessage`, `Objective`, `Notification`, `AuditLog`, `LoginLog`, `AppSetting` |

## 2. Rôles et permissions (RBAC)

Chaque rôle porte un ensemble de permissions, vérifiées par l'API (`@PreAuthorize`) et reflétées dans l'interface
(menus, boutons, routes). L'administrateur peut modifier la matrice depuis **Utilisateurs et rôles**.

| Permission | Admin | Directeur | Commercial | Support | Client |
|---|:-:|:-:|:-:|:-:|:-:|
| Utilisateurs, rôles, paramètres, droits 09-08 | ✔ | | | | |
| Journal d'audit | ✔ | ✔ | | | |
| Entreprises et contacts : lecture | ✔ | ✔ | ✔ | ✔ | |
| Entreprises et contacts : écriture / suppression | ✔ | ✔ / ✔ | ✔ / — | | |
| Leads, pipeline, devis, activités | ✔ | ✔ | ✔ | | |
| Attribuer les leads, valider les devis importants | ✔ | ✔ | | | |
| Modifier le portefeuille des autres commerciaux | ✔ | ✔ | | | |
| Tableau de bord personnel / de l'équipe | ✔ | ✔ / ✔ | ✔ / — | | |
| Fixer les objectifs | ✔ | ✔ | | | |
| Tickets SAV | ✔ | | | ✔ | |
| Portail client (ses devis, factures, tickets) | | | | | ✔ |
| Export / import | ✔ | ✔ | ✔ | | |

Portefeuille : un commercial consulte toutes les entreprises et opportunités, mais ne modifie que les siennes ;
il ne voit que ses leads et ceux qui ne sont pas encore attribués.

## 3. Règles métier principales

| Domaine | Règle |
|---|---|
| Leads | Score de conversion calculé à la création/modification ; attribution automatique au commercial le moins chargé pour le formulaire web ; conversion = entreprise + contact (+ opportunité). |
| Pipeline | Probabilité par défaut selon l'étape (10 / 25 / 50 / 75 / 100 / 0 %) ; **motif de perte obligatoire** ; gagnée ⇒ entreprise passe « Client ». |
| Devis | Numérotation `DEV-AAAA-NNNN` ; totaux HT/TVA/TTC avec remise par ligne ; au-delà du seuil (100 000 MAD par défaut) validation du directeur avant envoi ; toute modification après validation impose une nouvelle validation ; envoi par e-mail avec PDF ; partage WhatsApp par lien public à jeton secret. |
| Commande / facture | Devis accepté = commande ; facture `FAC-AAAA-NNNN` émise automatiquement ; opportunité gagnée au montant HT du devis. |
| Activités | Rappels (notification + e-mail) ; historique automatique de chaque événement (conversion, étape, devis, ticket) ; flux iCal signé pour synchroniser son agenda. |
| SAV | Délai cible (SLA) selon la priorité : 4 h / 24 h / 48 h / 72 h ; attribution automatique à l'agent le moins chargé ; le client est notifié à chaque changement ; notes internes invisibles du client. |
| Relances | Chaque jour à 8 h : opportunité sans activité depuis N jours (7 par défaut) ⇒ tâche + notification au commercial (une seule relance ouverte à la fois). |
| Tableau de bord | CA réalisé = factures émises (HT, hors annulées) ; prévisionnel = Σ montant × probabilité des opportunités ouvertes ; conversion = leads convertis / leads créés sur la période. |

## 4. Sécurité

| Exigence | Mise en œuvre |
|---|---|
| Authentification JWT | Jeton HMAC-SHA256 (8 h) contenant une **version** : la déconnexion ou le changement de mot de passe l'incrémentent, ce qui révoque tous les jetons émis. |
| Mots de passe | BCrypt ; 8 caractères minimum ; lien de réinitialisation à usage unique valable 1 h. |
| Force brute | Blocage 15 min après 5 échecs ; journal des connexions (IP, navigateur). |
| HTTPS | nginx en TLS 1.2/1.3, HSTS, redirection HTTP → HTTPS ; certificat auto-signé généré si aucun n'est fourni. |
| Injection SQL | Requêtes JPA paramétrées (Criteria API), aucune concaténation SQL. |
| XSS | Échappement systématique d'Angular ; Content-Security-Policy stricte (`script-src 'self'`) ; liens web filtrés (`http(s)` uniquement). |
| Injection CSV | Cellules commençant par `= + - @` neutralisées à l'export (numéros de téléphone préservés). |
| WebSocket | Authentification à la trame STOMP CONNECT ; abonnement limité aux files personnelles. |
| Traçabilité | `AuditLog` : création, modification, suppression, archivage, import, export, anonymisation. |

## 5. Conformité à la loi 09-08

| Principe | Mise en œuvre |
|---|---|
| Information | Page publique « Protection des données personnelles » liée au formulaire et au portail. |
| Consentement | Case obligatoire de traitement sur le formulaire web ; consentement marketing distinct, horodaté (`consentDate`). |
| Droit d'accès | Export JSON de toutes les données d'un contact (identité, interactions, tickets). |
| Droit d'effacement / d'opposition | Anonymisation d'un contact ou d'un lead (historique commercial conservé, compte portail désactivé). |
| Durée de conservation | Leads non convertis anonymisés automatiquement après N mois (24 par défaut, paramétrable). |
| Minimisation | L'assistant IA ne reçoit que le contexte utile (nom, entreprise, objet, dernière interaction). |
| Sécurité | Voir section 4. |

La déclaration du traitement auprès de la CNDP reste une démarche administrative de l'entreprise.

## 6. Conformité au cahier des charges

| Exigence | État |
|---|---|
| Module 1 — Authentification, mot de passe oublié, déconnexion, RBAC, profil, journal des connexions | ✅ |
| Module 2 — CRUD, archivage, recherche, fiche 360°, segmentation (tags, secteur, ville, statut), import CSV, export CSV / Excel | ✅ |
| Module 3 — Leads : saisie, formulaire web, import ; qualification ; attribution ; conversion | ✅ |
| Module 4 — Kanban 6 étapes, montant / probabilité / clôture, motif de perte obligatoire | ✅ |
| Module 5 — Catalogue, devis PDF + e-mail, statuts brouillon / envoyé / accepté / refusé (+ validation, facture) | ✅ |
| Module 6 — Planification liée au client, calendrier partagé, rappels notification / e-mail, historique automatique | ✅ |
| Module 7 — Tickets agent et portail, priorité / statut / assignation / délai, notification au client | ✅ |
| Module 8 — CA réalisé et prévisionnel, conversion, performance par commercial, tickets ouverts, délai moyen, export PDF et Excel | ✅ |
| Valeur ajoutée — scoring IA, assistant de rédaction, relances automatiques, notifications WebSocket, intégrations (SMTP, iCal, WhatsApp), DevOps | ✅ (6 sur 6) |
| Non fonctionnel — JWT, BCrypt, HTTPS, SQLi / XSS, responsive, français, Docker, sauvegarde quotidienne, couches, tests, Swagger, loi 09-08, traçabilité | ✅ (voir limites dans le README) |
