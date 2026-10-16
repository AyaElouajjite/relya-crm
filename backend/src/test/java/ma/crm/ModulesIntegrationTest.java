package ma.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.crm.repository.OpportunityRepository;
import ma.crm.service.AutomationService;
import ma.crm.service.CalendarFeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Parcours métier des modules 3 à 8 et des fonctionnalités à valeur ajoutée. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModulesIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AutomationService automation;
    @Autowired
    OpportunityRepository opportunities;
    @Autowired
    CalendarFeedService calendarFeed;

    // ---- Outils ----

    private String login(String email) throws Exception {
        String password = email.startsWith("admin") ? "Admin@2026" : "Demo@2026";
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private ResultActions send(String token, String url, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long companyId(String token, String name) throws Exception {
        return read(mvc.perform(get("/api/companies").param("q", name).header("Authorization", token)))
                .get("content").get(0).get("id").asLong();
    }

    private long createOpportunity(String token, long companyId, int amount) throws Exception {
        return read(send(token, "/api/opportunities", "{\"title\":\"Opp test\",\"amount\":" + amount
                + ",\"companyId\":" + companyId + "}").andExpect(status().isCreated())).get("id").asLong();
    }

    // ---- Authentification ----

    @Test
    void logoutRevokesTheTokenServerSide() throws Exception {
        String token = login("youssef@crm.ma");
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout")
                .header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    // ---- Module 3 : leads ----

    @Test
    void leadIsScoredThenConvertedIntoCompanyContactAndOpportunity() throws Exception {
        String token = login("salma@crm.ma");
        JsonNode lead = read(send(token, "/api/leads", "{\"firstName\":\"Test\",\"lastName\":\"Conversion\","
                + "\"companyName\":\"Conversion SARL\",\"position\":\"CEO\",\"source\":\"RECOMMANDATION\","
                + "\"estimatedBudget\":80000,\"email\":\"t@conv.ma\"}").andExpect(status().isCreated()));
        assertThat(lead.get("score").asInt()).isBetween(1, 99);
        assertThat(lead.get("assignedTo").get("fullName").asText()).isEqualTo("Salma El Idrissi");

        JsonNode result = read(send(token, "/api/leads/" + lead.get("id").asLong() + "/convert",
                "{\"createOpportunity\":true,\"opportunityTitle\":\"Projet conversion\"}").andExpect(status().isOk()));
        long companyId = result.get("companyId").asLong();
        mvc.perform(get("/api/companies/" + companyId).header("Authorization", token))
                .andExpect(jsonPath("$.company.name").value("Conversion SARL"))
                .andExpect(jsonPath("$.contacts[0].fullName").value("Test Conversion"))
                .andExpect(jsonPath("$.opportunities[0].title").value("Projet conversion"))
                .andExpect(jsonPath("$.opportunities[0].amount").value(80000))
                .andExpect(jsonPath("$.history[*].subject", hasItem(startsWith("Lead converti"))));
        mvc.perform(get("/api/leads/" + lead.get("id").asLong()).header("Authorization", token))
                .andExpect(jsonPath("$.status").value("CONVERTI"));
        send(token, "/api/leads/" + lead.get("id").asLong() + "/convert", "{}").andExpect(status().isConflict());
    }

    @Test
    void publicWebFormCreatesAnAssignedLeadAndIgnoresBots() throws Exception {
        String form = "{\"firstName\":\"Web\",\"lastName\":\"Visiteur\",\"email\":\"web@visiteur.ma\","
                + "\"dataProcessingConsent\":true,\"message\":\"Je souhaite une démo\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/public/leads")
                .contentType(MediaType.APPLICATION_JSON).content(form)).andExpect(status().isAccepted());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/public/leads")
                .contentType(MediaType.APPLICATION_JSON).content(form.replace("}", ",\"website\":\"spam\"}")))
                .andExpect(status().isAccepted());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/public/leads")
                .contentType(MediaType.APPLICATION_JSON).content(form.replace("true", "false")))
                .andExpect(status().isBadRequest());

        String director = login("directeur@crm.ma");
        JsonNode page = read(mvc.perform(get("/api/leads").param("q", "web@visiteur.ma").header("Authorization", director)));
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("source").asText()).isEqualTo("FORMULAIRE_WEB");
        assertThat(page.get("content").get(0).get("assignedTo").isNull()).isFalse();
    }

    // ---- Module 4 : pipeline ----

    @Test
    void lossReasonIsMandatoryAndPortfolioIsProtected() throws Exception {
        String salma = login("salma@crm.ma");
        long oppId = createOpportunity(salma, companyId(salma, "Atlas"), 50000);

        mvc.perform(patch("/api/opportunities/" + oppId + "/stage").header("Authorization", salma)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"PERDUE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("motif de perte")));
        mvc.perform(patch("/api/opportunities/" + oppId + "/stage").header("Authorization", salma)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"NEGOCIATION\"}"))
                .andExpect(jsonPath("$.probability").value(75));

        String youssef = login("youssef@crm.ma");
        mvc.perform(patch("/api/opportunities/" + oppId + "/stage").header("Authorization", youssef)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"GAGNEE\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/opportunities/" + oppId + "/stage").header("Authorization", salma)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"PERDUE\",\"lossReason\":\"PRIX\"}"))
                .andExpect(jsonPath("$.stage").value("PERDUE"))
                .andExpect(jsonPath("$.probability").value(0))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        JsonNode pipeline = read(mvc.perform(get("/api/opportunities/pipeline").header("Authorization", salma)));
        assertThat(pipeline).hasSize(6);
        assertThat(pipeline.get(0).get("stage").asText()).isEqualTo("PROSPECTION");
    }

    // ---- Module 5 : devis ----

    @Test
    void quoteLifecycleWithApprovalAcceptanceAndInvoice() throws Exception {
        String salma = login("salma@crm.ma");
        long oppId = createOpportunity(salma, companyId(salma, "Médina"), 200000);
        String lines = "[{\"description\":\"Pack\",\"quantity\":2,\"unitPrice\":50000,\"discount\":10,\"vatRate\":20},"
                + "{\"description\":\"Formation\",\"quantity\":3,\"unitPrice\":6500,\"vatRate\":20}]";
        JsonNode quote = read(send(salma, "/api/quotes", "{\"opportunityId\":" + oppId + ",\"lines\":" + lines + "}")
                .andExpect(status().isCreated()));
        long id = quote.get("id").asLong();
        // 2 × 50 000 − 10 % = 90 000 ; 3 × 6 500 = 19 500 → HT 109 500, TVA 21 900, TTC 131 400
        assertThat(quote.get("totalHT").decimalValue()).isEqualByComparingTo("109500");
        assertThat(quote.get("totalTTC").decimalValue()).isEqualByComparingTo("131400");
        assertThat(quote.get("number").asText()).matches("DEV-\\d{4}-\\d{4}");
        assertThat(quote.get("needsApproval").asBoolean()).isTrue();

        // Au-dessus du seuil de 100 000 : validation obligatoire par le directeur.
        send(salma, "/api/quotes/" + id + "/send", "{}").andExpect(jsonPath("$.status").value("EN_VALIDATION"));
        send(salma, "/api/quotes/" + id + "/approve", "{}").andExpect(status().isForbidden());
        String director = login("directeur@crm.ma");
        send(director, "/api/quotes/" + id + "/approve", "{}").andExpect(jsonPath("$.status").value("VALIDE"));

        send(salma, "/api/quotes/" + id + "/send", "{\"to\":\"achats@medina.ma\"}")
                .andExpect(jsonPath("$.status").value("ENVOYE"))
                .andExpect(jsonPath("$.publicToken").isNotEmpty());
        mvc.perform(get("/api/quotes/" + id + "/pdf").header("Authorization", salma))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));

        JsonNode accepted = read(send(salma, "/api/quotes/" + id + "/accept", "{}"));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTE");
        assertThat(accepted.get("invoice").get("label").asText()).matches("FAC-\\d{4}-\\d{4}");
        mvc.perform(get("/api/opportunities/" + oppId).header("Authorization", salma))
                .andExpect(jsonPath("$.stage").value("GAGNEE"))
                .andExpect(jsonPath("$.amount").value(109500.0));
        long invoiceId = accepted.get("invoice").get("id").asLong();
        mvc.perform(get("/api/invoices/" + invoiceId + "/pdf").header("Authorization", salma))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/public/quotes/" + accepted.get("publicToken").asText() + "/pdf"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/quotes/inconnu/pdf")).andExpect(status().isNotFound());
    }

    // ---- Module 6 : activités ----

    @Test
    void activitiesCalendarAndSignedIcsFeed() throws Exception {
        String salma = login("salma@crm.ma");
        Instant due = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        JsonNode a = read(send(salma, "/api/activities", "{\"type\":\"REUNION\",\"subject\":\"Démo, client; test\","
                + "\"dueAt\":\"" + due + "\",\"durationMinutes\":45,\"reminderMinutesBefore\":30}")
                .andExpect(status().isCreated()));
        assertThat(a.get("reminderAt").asText()).isEqualTo(due.minus(30, ChronoUnit.MINUTES).toString());

        mvc.perform(get("/api/activities/calendar").header("Authorization", salma)
                        .param("from", due.minus(1, ChronoUnit.DAYS).toString()).param("to", due.plus(1, ChronoUnit.DAYS).toString()))
                .andExpect(jsonPath("$[*].subject", hasItem("Démo, client; test")));

        String path = read(mvc.perform(get("/api/activities/calendar-feed").header("Authorization", salma))).get("path").asText();
        String ics = mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ics).startsWith("BEGIN:VCALENDAR").contains("SUMMARY:[REUNION] Démo\\, client\\; test");
        mvc.perform(get(path.replaceAll("/[0-9a-f]+\\.ics$", "/deadbeef.ics"))).andExpect(status().isNotFound());

        send(salma, "/api/activities", "{\"type\":\"SYSTEME\",\"subject\":\"x\",\"dueAt\":\"" + due + "\"}")
                .andExpect(status().isBadRequest());
    }

    // ---- Module 7 : SAV et portail ----

    @Test
    void ticketsArePrivateToTheClientCompanyOnThePortal() throws Exception {
        String client = login("client@atlas.ma");
        JsonNode t = read(send(client, "/api/portal/tickets", "{\"subject\":\"Écran bloqué\",\"description\":\"Depuis ce matin\","
                + "\"priority\":\"URGENTE\"}").andExpect(status().isCreated()));
        assertThat(t.get("priority").asText()).isEqualTo("HAUTE"); // le client ne peut pas s'auto-attribuer URGENTE
        assertThat(t.get("channel").asText()).isEqualTo("PORTAIL");
        long id = t.get("id").asLong();

        String support = login("support@crm.ma");
        send(support, "/api/tickets/" + id + "/messages", "{\"content\":\"Note interne\",\"internal\":true}");
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", support).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLU\"}")).andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        send(support, "/api/tickets/" + id + "/messages", "{\"content\":\"C'est réglé\",\"internal\":false}");

        mvc.perform(get("/api/portal/tickets/" + id).header("Authorization", client))
                .andExpect(jsonPath("$.messages[*].content", contains("C'est réglé")))
                .andExpect(jsonPath("$.assignee").doesNotExist());

        // Ticket d'une autre entreprise : invisible.
        long other = read(mvc.perform(get("/api/tickets").param("q", "synchronisation").header("Authorization", support)))
                .get("content").get(0).get("id").asLong();
        mvc.perform(get("/api/portal/tickets/" + other).header("Authorization", client)).andExpect(status().isNotFound());
        mvc.perform(get("/api/companies").header("Authorization", client)).andExpect(status().isForbidden());

        // Seuls les devis envoyés / acceptés / refusés sont visibles.
        JsonNode quotes = read(mvc.perform(get("/api/portal/quotes").header("Authorization", client)));
        quotes.forEach(q -> assertThat(q.get("status").asText()).isIn("ENVOYE", "ACCEPTE", "REFUSE"));
        mvc.perform(get("/api/portal/invoices").header("Authorization", client)).andExpect(status().isOk());
    }

    // ---- Module 8 : tableaux de bord ----

    @Test
    void dashboardScopesAndReports() throws Exception {
        String director = login("directeur@crm.ma");
        JsonNode d = read(mvc.perform(get("/api/dashboard").header("Authorization", director)
                .param("from", LocalDate.now().minusYears(1).toString())));
        assertThat(d.get("kpis").get("revenue").decimalValue()).isPositive();
        assertThat(d.get("sellers")).hasSizeGreaterThanOrEqualTo(2);
        assertThat(d.get("monthly")).hasSize(12);

        String salma = login("salma@crm.ma");
        long youssefId = read(mvc.perform(get("/api/users/assignable").header("Authorization", salma))).findValues("id").stream()
                .filter(n -> true).toList().size(); // l'appel doit réussir pour un commercial
        assertThat(youssefId).isPositive();
        JsonNode mine = read(mvc.perform(get("/api/dashboard").header("Authorization", salma).param("ownerId", "999")));
        assertThat(mine.get("ownerId").asLong()).isNotEqualTo(999); // un commercial ne voit que ses chiffres
        assertThat(mine.get("sellers")).isEmpty();

        mvc.perform(get("/api/reports/excel").header("Authorization", director)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".xlsx")));
        mvc.perform(get("/api/reports/pdf").header("Authorization", director))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/api/dashboard").header("Authorization", login("support@crm.ma"))).andExpect(status().isForbidden());
    }

    // ---- Valeur ajoutée ----

    @Test
    void inactiveOpportunitiesTriggerOneFollowUpTask() throws Exception {
        String salma = login("salma@crm.ma");
        long oppId = createOpportunity(salma, companyId(salma, "Atlas"), 12000);
        var opp = opportunities.findById(oppId).orElseThrow();
        opp.setLastActivityAt(Instant.now().minus(10, ChronoUnit.DAYS));
        opportunities.save(opp);

        automation.runFollowUps();
        automation.runFollowUps();
        JsonNode tasks = read(mvc.perform(get("/api/activities").param("opportunityId", String.valueOf(oppId))
                .param("type", "TACHE").header("Authorization", salma)));
        assertThat(tasks.get("totalElements").asInt()).isEqualTo(1);
        assertThat(tasks.get("content").get(0).get("subject").asText()).startsWith("Relance : ");
        assertThat(read(mvc.perform(get("/api/notifications/unread").header("Authorization", salma))).get("count").asLong())
                .isPositive();
    }

    @Test
    void privacyExportAndAnonymization() throws Exception {
        String admin = login("admin@crm.ma");
        long contactId = read(send(admin, "/api/contacts", "{\"firstName\":\"Rgpd\",\"lastName\":\"Personne\","
                + "\"email\":\"rgpd@x.ma\",\"marketingConsent\":true}")).get("id").asLong();
        mvc.perform(get("/api/contacts/" + contactId).header("Authorization", admin))
                .andExpect(jsonPath("$.consentDate").isNotEmpty());
        mvc.perform(get("/api/privacy/contacts/" + contactId + "/export").header("Authorization", admin))
                .andExpect(jsonPath("$.identite.email").value("rgpd@x.ma"));
        send(admin, "/api/privacy/contacts/" + contactId + "/anonymize", "{}").andExpect(status().isNoContent());
        mvc.perform(get("/api/contacts/" + contactId).header("Authorization", admin))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.anonymized").value(true));
        mvc.perform(get("/api/privacy/contacts/" + contactId + "/export").header("Authorization", login("directeur@crm.ma")))
                .andExpect(status().isForbidden());
    }

    @Test
    void excelExportAndPortalInvitation() throws Exception {
        String salma = login("salma@crm.ma");
        mvc.perform(get("/api/contacts/export").param("format", "xlsx").param("city", "Fès").header("Authorization", salma))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("spreadsheetml")));
        long contactId = read(send(salma, "/api/contacts", "{\"firstName\":\"Portail\",\"lastName\":\"Invite\","
                + "\"email\":\"portail@invite.ma\",\"companyId\":" + companyId(salma, "Sahara") + "}")).get("id").asLong();
        send(salma, "/api/contacts/" + contactId + "/portal-access", "{}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roleCode").value("CLIENT"));
        send(salma, "/api/contacts/" + contactId + "/portal-access", "{}").andExpect(status().isConflict());
    }
}
