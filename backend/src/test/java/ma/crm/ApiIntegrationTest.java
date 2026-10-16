package ma.crm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    @Test
    void loginReturnsTokenAndUserWithPermissions() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Admin@crm.ma\",\"password\":\"Admin@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.user.permissions", hasItem("USER_MANAGE")));
    }

    @Test
    void badPasswordIsRejectedAndRequestsWithoutTokenAre401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"salma@crm.ma\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/companies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/companies").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rbacRestrictsWritesAndUserManagement() throws Exception {
        String support = login("support@crm.ma", "Demo@2026");
        mvc.perform(get("/api/companies").header("Authorization", support)).andExpect(status().isOk());
        mvc.perform(post("/api/companies").header("Authorization", support)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Interdit\"}"))
                .andExpect(status().isForbidden());

        String commercial = login("youssef@crm.ma", "Demo@2026");
        mvc.perform(get("/api/users").header("Authorization", commercial)).andExpect(status().isForbidden());
    }

    @Test
    void companyLifecycleWithContactsAnd360View() throws Exception {
        String token = login("salma@crm.ma", "Demo@2026");

        String created = mvc.perform(post("/api/companies").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test SARL\",\"city\":\"Rabat\",\"ice\":\"009999999000001\",\"tags\":[\"vip\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PROSPECT"))
                .andExpect(jsonPath("$.owner.fullName").value("Salma El Idrissi"))
                .andReturn().getResponse().getContentAsString();
        long companyId = json.readTree(created).get("id").asLong();

        // ICE en double refusé
        mvc.perform(post("/api/companies").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Doublon\",\"ice\":\"009999999000001\"}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/contacts").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ali\",\"lastName\":\"Test\",\"companyId\":" + companyId + "}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/companies/" + companyId).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.company.contactCount").value(1))
                .andExpect(jsonPath("$.contacts[0].fullName").value("Ali Test"));

        mvc.perform(get("/api/companies").param("tag", "vip").header("Authorization", token))
                .andExpect(jsonPath("$.content[*].name", contains("Test SARL")));

        mvc.perform(patch("/api/companies/" + companyId + "/archived").param("value", "true")
                .header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/companies").param("q", "Test SARL").header("Authorization", token))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        String token = login("salma@crm.ma", "Demo@2026");
        mvc.perform(post("/api/companies").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"ice\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.ice").exists());
    }

    @Test
    void csvImportThenExportRoundTrip() throws Exception {
        String token = login("admin@crm.ma", "Admin@2026");
        String csv = "raisonSociale;ville;statut;tags\nImport Un;Oujda;CLIENT;a|b\nImport Deux;Meknès;;\n;Sans nom;;\n";
        var file = new MockMultipartFile("file", "e.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String result = mvc.perform(multipart("/api/companies/import").file(file).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode r = json.readTree(result);
        assertThat(r.get("created").asInt()).isEqualTo(2);
        assertThat(r.get("errors")).hasSize(1);

        String exported = mvc.perform(get("/api/companies/export").param("q", "Import")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("entreprises-")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(exported).contains("Import Un;", "Import Deux;", "a|b");
    }
}
