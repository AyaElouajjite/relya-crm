package ma.crm.config;

import lombok.RequiredArgsConstructor;
import ma.crm.domain.*;
import ma.crm.domain.Enums.*;
import ma.crm.repository.*;
import ma.crm.service.SequenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static ma.crm.domain.Permission.*;

/**
 * Crée les rôles (avec leurs permissions par défaut), le compte administrateur et, en démonstration,
 * un jeu de données couvrant tous les modules (leads, pipeline, devis, factures, activités, tickets, objectifs).
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    static final String DEMO_PASSWORD = "Demo@2026";
    /** À incrémenter quand les permissions par défaut évoluent : les rôles existants sont alors réalignés. */
    static final String RBAC_VERSION = "3";
    private static final String RBAC_KEY = "rbac.version";

    private final RoleRepository roles;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final LeadRepository leads;
    private final OpportunityRepository opportunities;
    private final ProductRepository products;
    private final QuoteRepository quotes;
    private final InvoiceRepository invoices;
    private final ActivityRepository activities;
    private final TicketRepository tickets;
    private final ObjectiveRepository objectives;
    private final AppSettingRepository settings;
    private final SequenceService sequences;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Value("${ADMIN_EMAIL:admin@crm.ma}")
    private String adminEmail;
    @Value("${ADMIN_PASSWORD:Admin@2026}")
    private String adminPassword;

    public static Map<String, Set<Permission>> defaultPermissions() {
        Map<String, Set<Permission>> map = new LinkedHashMap<>();
        Set<Permission> admin = EnumSet.allOf(Permission.class);
        admin.remove(PORTAL_ACCESS);
        map.put(Role.ADMIN, admin);
        map.put(Role.DIRECTEUR, EnumSet.of(CONTACT_READ, CONTACT_WRITE, CONTACT_DELETE, LEAD_MANAGE, LEAD_ASSIGN,
                OPPORTUNITY_MANAGE, PRODUCT_MANAGE, QUOTE_MANAGE, QUOTE_APPROVE, ACTIVITY_MANAGE, ALL_RECORDS,
                DASHBOARD_VIEW, DASHBOARD_ALL, OBJECTIVE_MANAGE, DATA_EXPORT, DATA_IMPORT, AUDIT_READ));
        map.put(Role.COMMERCIAL, EnumSet.of(CONTACT_READ, CONTACT_WRITE, LEAD_MANAGE, OPPORTUNITY_MANAGE,
                QUOTE_MANAGE, ACTIVITY_MANAGE, DASHBOARD_VIEW, DATA_EXPORT, DATA_IMPORT));
        map.put(Role.SUPPORT, EnumSet.of(CONTACT_READ, TICKET_MANAGE));
        map.put(Role.CLIENT, EnumSet.of(PORTAL_ACCESS));
        return map;
    }

    private static final Map<String, String> LABELS = Map.of(Role.ADMIN, "Administrateur",
            Role.DIRECTEUR, "Directeur commercial", Role.COMMERCIAL, "Commercial",
            Role.SUPPORT, "Agent support", Role.CLIENT, "Client (portail)");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Role> byCode = seedRoles();
        if (users.count() > 0) {
            return;
        }
        user("Admin", "Système", adminEmail, adminPassword, byCode.get(Role.ADMIN));
        log.info("Compte administrateur créé : {}", adminEmail);
        if (props.seedDemoData()) {
            new Demo(byCode).run();
            log.info("Données de démonstration créées (mot de passe des comptes de démo : {})", DEMO_PASSWORD);
        }
    }

    private Map<String, Role> seedRoles() {
        boolean reset = !settings.findById(RBAC_KEY).map(AppSetting::getValue).orElse("").equals(RBAC_VERSION);
        Map<String, Role> byCode = new HashMap<>();
        defaultPermissions().forEach((code, perms) -> {
            Optional<Role> existing = roles.findByCode(code);
            Role role = existing.orElseGet(() -> new Role(code, LABELS.get(code), perms));
            if (existing.isPresent() && reset) {
                role.getPermissions().clear();
                role.getPermissions().addAll(perms);
            }
            byCode.put(code, roles.save(role));
        });
        if (reset) {
            settings.save(new AppSetting(RBAC_KEY, RBAC_VERSION));
            log.info("Permissions par défaut des rôles appliquées (version {})", RBAC_VERSION);
        }
        return byCode;
    }

    private User user(String first, String last, String email, String password, Role role) {
        User u = new User();
        u.setFirstName(first);
        u.setLastName(last);
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role);
        return users.save(u);
    }

    /** Jeu de démonstration déterministe (graine fixe). */
    private final class Demo {
        private final Map<String, Role> roleMap;
        private final Random rnd = new Random(2026);
        private final Instant now = Instant.now();

        Demo(Map<String, Role> roleMap) {
            this.roleMap = roleMap;
        }

        void run() {
            User directeur = user("Karim", "Bennani", "directeur@crm.ma", DEMO_PASSWORD, roleMap.get(Role.DIRECTEUR));
            User salma = user("Salma", "El Idrissi", "salma@crm.ma", DEMO_PASSWORD, roleMap.get(Role.COMMERCIAL));
            User youssef = user("Youssef", "Alaoui", "youssef@crm.ma", DEMO_PASSWORD, roleMap.get(Role.COMMERCIAL));
            User support = user("Nadia", "Tazi", "support@crm.ma", DEMO_PASSWORD, roleMap.get(Role.SUPPORT));
            List<User> sellers = List.of(salma, youssef);

            List<Product> catalog = List.of(
                    product("Licence CRM Cloud — 1 utilisateur / an", "LIC-CRM", ProductType.SERVICE, "4800", "an"),
                    product("Pack d'implémentation", "SRV-IMPL", ProductType.SERVICE, "25000", "forfait"),
                    product("Formation utilisateurs (jour)", "SRV-FORM", ProductType.SERVICE, "6500", "jour"),
                    product("Support premium (mois)", "SRV-SUP", ProductType.SERVICE, "3000", "mois"),
                    product("Terminal point de vente", "MAT-TPV", ProductType.PRODUIT, "7900", "unité"),
                    product("Imprimante thermique", "MAT-IMP", ProductType.PRODUIT, "2300", "unité"),
                    product("Tablette durcie", "MAT-TAB", ProductType.PRODUIT, "5400", "unité"),
                    product("Développement spécifique (jour)", "SRV-DEV", ProductType.SERVICE, "7000", "jour"));

            String[][] companyData = {
                    {"Atlas Distribution", "Distribution", "Casablanca", "CLIENT"},
                    {"Sahara Tech", "Informatique", "Rabat", "PROSPECT"},
                    {"Médina Textile", "Textile", "Fès", "CLIENT"},
                    {"Océan Logistique", "Transport", "Tanger", "PROSPECT"},
                    {"Argan Cosmétiques", "Cosmétique", "Agadir", "INACTIF"},
                    {"Clinique Al Amal", "Santé", "Marrakech", "PROSPECT"},
                    {"BTP Rif Construction", "BTP", "Tétouan", "CLIENT"},
                    {"Saveurs du Souss", "Agroalimentaire", "Agadir", "PROSPECT"},
                    {"Riad Voyages", "Tourisme", "Marrakech", "CLIENT"},
                    {"Banque Populaire Région Est", "Banque", "Oujda", "PROSPECT"}};
            String[][] contactData = {
                    {"Omar", "Chraibi", "Directeur achats"}, {"Mehdi", "Fassi", "CEO"},
                    {"Hind", "Berrada", "Responsable export"}, {"Rachid", "Amrani", "Directeur général"},
                    {"Laila", "Ouazzani", "Gérante"}, {"Dr Samir", "Kettani", "Directeur"},
                    {"Fatima", "Zahra Idrissi", "Directrice financière"}, {"Anas", "Benkirane", "Responsable SI"},
                    {"Imane", "Lahlou", "Directrice commerciale"}, {"Khalid", "Rami", "Chef de projet"}};
            List<Company> companyList = new ArrayList<>();
            List<Contact> mainContacts = new ArrayList<>();
            for (int i = 0; i < companyData.length; i++) {
                String[] d = companyData[i];
                User owner = sellers.get(i % 2);
                Company c = new Company();
                c.setName(d[0]);
                c.setSector(d[1]);
                c.setCity(d[2]);
                c.setStatus(CompanyStatus.valueOf(d[3]));
                c.setIce(String.format("%09d%06d", 1234567 + i * 1111, 89 + i));
                c.setAddress((12 + i * 7) + ", boulevard Mohammed V");
                c.setPhone("+212 5 22 " + (40 + i) + " 1" + i + " 2" + i);
                c.setEmail("contact@" + d[0].toLowerCase().replaceAll("[^a-z]", "") + ".ma");
                c.setOwner(owner);
                c.setCreatedAt(daysAgo(300 - i * 20));
                c.getTags().addAll(i % 3 == 0 ? Set.of("grand-compte") : i % 3 == 1 ? Set.of("pme") : Set.of("export", "fidèle"));
                companyList.add(companies.save(c));
                String[] p = contactData[i];
                Contact ct = contact(p[0], p[1], p[2], c, owner, i % 2 == 0);
                mainContacts.add(ct);
                if (i < 4) {
                    contact(i % 2 == 0 ? "Sara" : "Yassine", i % 2 == 0 ? "Bennis" : "Tahiri", "Assistant(e) de direction", c, owner, false);
                }
            }

            // Compte du portail client pour Omar Chraibi (Atlas Distribution).
            User client = user(mainContacts.get(0).getFirstName(), mainContacts.get(0).getLastName(), "client@atlas.ma",
                    DEMO_PASSWORD, roleMap.get(Role.CLIENT));
            client.setContact(mainContacts.get(0));

            seedLeads(sellers);
            List<Opportunity> opps = seedOpportunities(companyList, mainContacts, sellers);
            seedQuotes(opps, catalog, directeur);
            seedActivities(opps, sellers);
            seedTickets(companyList, mainContacts, support);
            seedObjectives(sellers);
        }

        private void seedLeads(List<User> sellers) {
            String[] first = {"Amine", "Nora", "Hamza", "Salma", "Ilyas", "Meryem", "Othmane", "Kenza", "Reda", "Hajar",
                    "Adil", "Zineb", "Soufiane", "Ghita", "Yassir", "Asmae", "Tarik", "Sanaa", "Badr", "Houda",
                    "Nabil", "Loubna", "Ayoub", "Rim", "Jalal", "Wiam", "Hicham", "Chaimae"};
            String[] last = {"Bennani", "Alami", "Tazi", "Idrissi", "Berrada", "Fassi", "Chraibi", "Lahlou", "Kettani",
                    "Sqalli", "Benjelloun", "Ouazzani", "Amrani", "Naciri"};
            String[] companiesNames = {"Nova Services", "Datacom Maroc", "Green Energy SA", "Pharma Plus", "Auto Parts Nord",
                    "Hôtel Les Palmiers", "Smart School", "Agri Souss", "Fintech Atlas", "Ciment du Nord"};
            String[] sectors = {"Informatique", "Distribution", "Santé", "Tourisme", "BTP", "Agroalimentaire", "Banque"};
            String[] cities = {"Casablanca", "Rabat", "Marrakech", "Fès", "Tanger", "Agadir", "Meknès"};
            String[] positions = {"CEO", "Directeur commercial", "Responsable achats", "Chef de projet", "Assistant", "Gérant"};
            LeadSource[] sources = LeadSource.values();
            for (int i = 0; i < first.length; i++) {
                Lead l = new Lead();
                l.setFirstName(first[i]);
                l.setLastName(last[i % last.length]);
                l.setEmail(first[i].toLowerCase() + "." + last[i % last.length].toLowerCase() + "@example.ma");
                l.setPhone(i % 3 == 0 ? null : "+212 6 " + (10 + i) + " " + (20 + i) + " " + (30 + i) + " " + (40 + i));
                l.setCompanyName(companiesNames[i % companiesNames.length]);
                l.setPosition(positions[i % positions.length]);
                l.setSector(sectors[i % sectors.length]);
                l.setCity(cities[i % cities.length]);
                l.setSource(sources[i % sources.length]);
                l.setEstimatedBudget(i % 4 == 0 ? null : BigDecimal.valueOf(15000L + rnd.nextInt(40) * 5000L));
                l.setMarketingConsent(i % 2 == 0);
                l.setAssignedTo(i % 7 == 6 ? null : sellers.get(i % 2));
                l.setCreatedAt(daysAgo(5 + rnd.nextInt(200)));
                l.setStatus(i < 6 ? LeadStatus.CONVERTI : i < 10 ? LeadStatus.NON_QUALIFIE
                        : i < 15 ? LeadStatus.QUALIFIE : i < 21 ? LeadStatus.CONTACTE : LeadStatus.NOUVEAU);
                if (l.getStatus() == LeadStatus.CONVERTI) {
                    l.setConvertedAt(l.getCreatedAt().plus(10 + rnd.nextInt(20), ChronoUnit.DAYS));
                }
                l.setScore(20 + rnd.nextInt(70));
                l.setScoredAt(now);
                leads.save(l);
            }
        }

        private List<Opportunity> seedOpportunities(List<Company> companyList, List<Contact> contactsList, List<User> sellers) {
            Object[][] data = {
                    {"Déploiement CRM 25 utilisateurs", 185000, OpportunityStage.GAGNEE, null},
                    {"Équipement 12 points de vente", 98000, OpportunityStage.GAGNEE, null},
                    {"Formation équipe export", 32000, OpportunityStage.GAGNEE, null},
                    {"Refonte du système de caisse", 140000, OpportunityStage.NEGOCIATION, null},
                    {"Tablettes terrain chantiers", 64000, OpportunityStage.PROPOSITION, null},
                    {"Portail de réservation", 120000, OpportunityStage.QUALIFICATION, null},
                    {"Support premium annuel", 36000, OpportunityStage.PROSPECTION, null},
                    {"Digitalisation des dossiers patients", 210000, OpportunityStage.PROPOSITION, null},
                    {"Module traçabilité export", 75000, OpportunityStage.PERDUE, LossReason.PRIX},
                    {"Équipement agences", 160000, OpportunityStage.PERDUE, LossReason.CONCURRENT},
                    {"Extension licences", 48000, OpportunityStage.GAGNEE, null},
                    {"Application mobile commerciaux", 90000, OpportunityStage.NEGOCIATION, null},
                    {"Audit et conseil SI", 28000, OpportunityStage.PERDUE, LossReason.TIMING},
                    {"Bornes de commande", 54000, OpportunityStage.QUALIFICATION, null}};
            List<Opportunity> result = new ArrayList<>();
            for (int i = 0; i < data.length; i++) {
                Company c = companyList.get(i % companyList.size());
                Opportunity o = new Opportunity();
                o.setTitle((String) data[i][0]);
                o.setAmount(BigDecimal.valueOf((int) data[i][1]));
                OpportunityStage stage = (OpportunityStage) data[i][2];
                o.setStage(stage);
                o.setProbability(stage.defaultProbability);
                o.setCompany(c);
                o.setContact(contactsList.get(i % contactsList.size()));
                o.setOwner(c.getOwner() != null ? c.getOwner() : sellers.get(i % 2));
                o.setCreatedAt(daysAgo(40 + i * 15));
                o.setExpectedCloseDate(LocalDate.now().plusDays(stage.isClosed() ? -(20 + i * 12L) : 10 + i * 9L));
                if (stage.isClosed()) {
                    o.setClosedAt(daysAgo(15 + i * 12));
                }
                o.setLossReason((LossReason) data[i][3]);
                if (o.getLossReason() != null) {
                    o.setLossComment("Retour client lors de la négociation finale.");
                }
                // Deux opportunités ouvertes sans activité récente déclencheront une relance automatique.
                o.setLastActivityAt(stage.isClosed() ? o.getClosedAt() : daysAgo(i % 5 == 0 ? 12 : 2));
                result.add(opportunities.save(o));
            }
            return result;
        }

        private void seedQuotes(List<Opportunity> opps, List<Product> catalog, User directeur) {
            for (Opportunity o : opps) {
                if (o.getStage() == OpportunityStage.PROSPECTION || o.getStage() == OpportunityStage.QUALIFICATION) {
                    continue;
                }
                Quote q = new Quote();
                q.setNumber(sequences.next("DEV"));
                q.setOpportunity(o);
                q.setCompany(o.getCompany());
                q.setContact(o.getContact());
                q.setOwner(o.getOwner());
                LocalDate date = LocalDate.ofInstant(o.getStage().isClosed() ? o.getClosedAt().minus(10, ChronoUnit.DAYS)
                        : now.minus(6, ChronoUnit.DAYS), ZoneId.systemDefault());
                q.setDate(date);
                q.setValidUntil(date.plusDays(30));
                // Lignes construites pour approcher le montant de l'opportunité.
                BigDecimal remaining = o.getAmount();
                int pos = 0;
                for (Product p : List.of(catalog.get(1), catalog.get(0), catalog.get(2))) {
                    BigDecimal qty = remaining.divide(p.getPriceHT(), 0, RoundingMode.DOWN).min(BigDecimal.valueOf(pos == 0 ? 1 : 20));
                    if (qty.signum() <= 0) {
                        continue;
                    }
                    q.getLines().add(line(q, p, qty, pos++));
                    remaining = remaining.subtract(qty.multiply(p.getPriceHT()));
                }
                BigDecimal ht = BigDecimal.ZERO;
                BigDecimal vat = BigDecimal.ZERO;
                for (QuoteLine l : q.getLines()) {
                    ht = ht.add(l.getTotalHT());
                    vat = vat.add(l.getTotalVAT());
                }
                q.setTotalHT(ht);
                q.setTotalVAT(vat);
                q.setTotalTTC(ht.add(vat));
                Instant sent = date.atStartOfDay(ZoneId.systemDefault()).toInstant().plus(1, ChronoUnit.DAYS);
                switch (o.getStage()) {
                    case GAGNEE -> {
                        q.setStatus(QuoteStatus.ACCEPTE);
                        q.setSentAt(sent);
                        q.setRespondedAt(o.getClosedAt());
                    }
                    case PERDUE -> {
                        q.setStatus(QuoteStatus.REFUSE);
                        q.setSentAt(sent);
                        q.setRespondedAt(o.getClosedAt());
                    }
                    case NEGOCIATION -> {
                        q.setStatus(QuoteStatus.ENVOYE);
                        q.setSentAt(sent);
                    }
                    default -> q.setStatus(q.getTotalTTC().compareTo(BigDecimal.valueOf(100000)) >= 0
                            ? QuoteStatus.EN_VALIDATION : QuoteStatus.BROUILLON);
                }
                if (q.getStatus() != QuoteStatus.BROUILLON && q.getStatus() != QuoteStatus.EN_VALIDATION
                        && q.getTotalTTC().compareTo(BigDecimal.valueOf(100000)) >= 0) {
                    q.setApprovedBy(directeur);
                    q.setApprovedAt(sent.minus(1, ChronoUnit.HOURS));
                }
                q.setPublicToken(q.getSentAt() == null ? null : UUID.randomUUID().toString().replace("-", ""));
                quotes.save(q);
                if (q.getStatus() == QuoteStatus.ACCEPTE) {
                    o.setAmount(ht);
                    Invoice inv = new Invoice();
                    inv.setNumber(sequences.next("FAC"));
                    inv.setQuote(q);
                    inv.setCompany(q.getCompany());
                    inv.setIssueDate(LocalDate.ofInstant(o.getClosedAt(), ZoneId.systemDefault()));
                    inv.setDueDate(inv.getIssueDate().plusDays(30));
                    inv.setTotalHT(ht);
                    inv.setTotalVAT(vat);
                    inv.setTotalTTC(ht.add(vat));
                    boolean paid = inv.getDueDate().isBefore(LocalDate.now().minusDays(5));
                    inv.setStatus(paid ? InvoiceStatus.PAYEE : InvoiceStatus.EMISE);
                    inv.setPaidDate(paid ? inv.getDueDate().minusDays(3) : null);
                    invoices.save(inv);
                }
            }
        }

        private void seedActivities(List<Opportunity> opps, List<User> sellers) {
            String[][] planned = {
                    {"APPEL", "Point d'avancement avec le client"}, {"REUNION", "Démonstration de la solution"},
                    {"EMAIL", "Envoyer la documentation technique"}, {"TACHE", "Préparer la proposition commerciale"},
                    {"REUNION", "Rendez-vous de négociation"}, {"APPEL", "Relance téléphonique devis"}};
            int k = 0;
            for (Opportunity o : opps) {
                if (o.getStage().isClosed()) {
                    activity(ActivityType.REUNION, "Réunion de cadrage", o, daysAgo(30 + k), ActivityStatus.TERMINEE);
                    continue;
                }
                String[] p = planned[k % planned.length];
                // Une partie dans le passé (dont des tâches en retard), une partie à venir.
                Instant due = k % 3 == 0 ? now.minus(2 + k, ChronoUnit.DAYS) : now.plus(1 + k, ChronoUnit.DAYS)
                        .truncatedTo(ChronoUnit.HOURS).plus(9 + k % 6, ChronoUnit.HOURS);
                Activity a = activity(ActivityType.valueOf(p[0]), p[1], o, due,
                        k % 6 == 0 ? ActivityStatus.TERMINEE : ActivityStatus.A_FAIRE);
                if (a.getStatus() == ActivityStatus.A_FAIRE && due.isAfter(now)) {
                    a.setReminderAt(due.minus(30, ChronoUnit.MINUTES));
                }
                k++;
            }
        }

        private Activity activity(ActivityType type, String subject, Opportunity o, Instant due, ActivityStatus status) {
            Activity a = new Activity();
            a.setType(type);
            a.setSubject(subject);
            a.setDueAt(due);
            a.setDurationMinutes(type == ActivityType.REUNION ? 60 : 30);
            a.setStatus(status);
            a.setCompletedAt(status == ActivityStatus.TERMINEE ? due : null);
            a.setOpportunity(o);
            a.setCompany(o.getCompany());
            a.setContact(o.getContact());
            a.setOwner(o.getOwner());
            return activities.save(a);
        }

        private void seedTickets(List<Company> companyList, List<Contact> contactsList, User support) {
            Object[][] data = {
                    {"Impossible d'imprimer les tickets de caisse", TicketPriority.HAUTE, TicketStatus.EN_COURS, 0, 1},
                    {"Demande d'ajout d'un utilisateur", TicketPriority.BASSE, TicketStatus.RESOLU, 0, 20},
                    {"Erreur de synchronisation des stocks", TicketPriority.URGENTE, TicketStatus.OUVERT, 2, 0},
                    {"Question sur la facture FAC", TicketPriority.NORMALE, TicketStatus.FERME, 6, 45},
                    {"Lenteurs le matin", TicketPriority.NORMALE, TicketStatus.EN_ATTENTE_CLIENT, 8, 4},
                    {"Formation complémentaire souhaitée", TicketPriority.BASSE, TicketStatus.RESOLU, 2, 60}};
            for (Object[] d : data) {
                int idx = (int) d[3];
                Ticket t = new Ticket();
                t.setNumber(sequences.next("TCK"));
                t.setSubject((String) d[0]);
                t.setDescription("Ticket de démonstration : " + d[0] + ".");
                t.setPriority((TicketPriority) d[1]);
                t.setStatus((TicketStatus) d[2]);
                t.setChannel(idx == 0 ? TicketChannel.PORTAIL : TicketChannel.TELEPHONE);
                t.setCategory(idx % 2 == 0 ? "Technique" : "Facturation");
                t.setCompany(companyList.get(idx));
                t.setContact(contactsList.get(idx));
                t.setAssignee(support);
                Instant opened = daysAgo((int) d[4]).minus(3, ChronoUnit.HOURS);
                t.setOpenedAt(opened);
                t.setDueAt(opened.plus(t.getPriority().slaHours, ChronoUnit.HOURS));
                if (t.getStatus() != TicketStatus.OUVERT) {
                    t.setFirstResponseAt(opened.plus(2, ChronoUnit.HOURS));
                }
                if (!t.getStatus().isOpen()) {
                    t.setResolvedAt(opened.plus(6 + rnd.nextInt(30), ChronoUnit.HOURS));
                }
                tickets.save(t);
            }
        }

        private void seedObjectives(List<User> sellers) {
            int year = LocalDate.now().getYear();
            for (User u : sellers) {
                for (int m = 1; m <= 12; m++) {
                    Objective o = new Objective();
                    o.setUser(u);
                    o.setYear(year);
                    o.setMonth(m);
                    o.setTargetAmount(BigDecimal.valueOf(u.getFirstName().equals("Salma") ? 60000 : 45000));
                    objectives.save(o);
                }
            }
        }

        private Product product(String name, String ref, ProductType type, String price, String unit) {
            Product p = new Product();
            p.setName(name);
            p.setReference(ref);
            p.setType(type);
            p.setPriceHT(new BigDecimal(price));
            p.setUnit(unit);
            return products.save(p);
        }

        private QuoteLine line(Quote q, Product p, BigDecimal qty, int pos) {
            QuoteLine l = new QuoteLine();
            l.setQuote(q);
            l.setProduct(p);
            l.setPosition(pos);
            l.setDescription(p.getName());
            l.setQuantity(qty);
            l.setUnitPrice(p.getPriceHT());
            l.setDiscount(pos == 0 ? BigDecimal.valueOf(5) : BigDecimal.ZERO);
            l.setVatRate(p.getVatRate());
            return l;
        }

        private Contact contact(String first, String last, String position, Company c, User owner, boolean consent) {
            Contact ct = new Contact();
            ct.setFirstName(first);
            ct.setLastName(last);
            ct.setEmail((first.replaceAll("[^A-Za-z]", "").charAt(0) + "." + last.replaceAll("[^A-Za-z]", "")).toLowerCase()
                    + "@" + c.getName().toLowerCase().replaceAll("[^a-z]", "") + ".ma");
            ct.setPhone("+212 6 6" + rnd.nextInt(10) + " " + (10 + rnd.nextInt(89)) + " " + (10 + rnd.nextInt(89)) + " " + (10 + rnd.nextInt(89)));
            ct.setPosition(position);
            ct.setCompany(c);
            ct.setOwner(owner);
            ct.setMarketingConsent(consent);
            ct.setConsentDate(consent ? c.getCreatedAt() : null);
            return contacts.save(ct);
        }

        private Instant daysAgo(int days) {
            return now.minus(days, ChronoUnit.DAYS);
        }
    }
}
