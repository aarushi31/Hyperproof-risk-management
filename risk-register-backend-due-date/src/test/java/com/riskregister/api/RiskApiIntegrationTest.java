package com.riskregister.api;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riskregister.repository.RiskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end through HTTP → controller → service → JPA → (H2 in PostgreSQL mode, Flyway-migrated schema). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class RiskApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RiskRepository riskRepository;

    @BeforeEach
    void clean() {
        riskRepository.deleteAll();
    }

    // ---------- the headline scenario ----------

    @Test
    void createRisk_addMitigation_fetchRisk_residualScoreUpdates() throws Exception {
        long riskId = createRisk(riskJson("Data breach", "SECURITY", 4, 5, null));

        mvc.perform(get("/api/risks/" + riskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.inherentScore").value(20))
                .andExpect(jsonPath("$.inherentSeverity").value("CRITICAL"))
                .andExpect(jsonPath("$.residualScore").value(20))   // no mitigations -> residual == inherent
                .andExpect(jsonPath("$.mitigationCount").value(0));

        long mitigationId = addMitigation(riskId, "Enforce MFA everywhere", 5);

        mvc.perform(get("/api/risks/" + riskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inherentScore").value(20))
                .andExpect(jsonPath("$.residualScore").value(5))    // 20 * 0.25
                .andExpect(jsonPath("$.residualSeverity").value("LOW"))
                .andExpect(jsonPath("$.mitigationCount").value(1))
                .andExpect(jsonPath("$.mitigations[0].description").value("Enforce MFA everywhere"));

        // weaker control -> higher residual
        mvc.perform(put("/api/risks/" + riskId + "/mitigations/" + mitigationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"MFA (partial rollout)\",\"effectiveness\":1}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/risks/" + riskId))
                .andExpect(jsonPath("$.residualScore").value(17))
                .andExpect(jsonPath("$.residualSeverity").value("HIGH"));

        // removing the control restores residual == inherent
        mvc.perform(delete("/api/risks/" + riskId + "/mitigations/" + mitigationId))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/risks/" + riskId))
                .andExpect(jsonPath("$.residualScore").value(20))
                .andExpect(jsonPath("$.mitigationCount").value(0));
    }

    // ---------- closure rule ----------

    @Test
    void cannotCloseRiskWithoutMitigations_butCanOnceOneExists() throws Exception {
        // create directly as CLOSED
        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content(riskJson("Vendor outage", "OPERATIONAL", 3, 3, "CLOSED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RISK_CLOSURE_REQUIRES_MITIGATION"));

        // update an existing risk to CLOSED
        long riskId = createRisk(riskJson("Vendor outage", "OPERATIONAL", 3, 3, null));
        mvc.perform(put("/api/risks/" + riskId).contentType(MediaType.APPLICATION_JSON)
                        .content(riskJson("Vendor outage", "OPERATIONAL", 3, 3, "CLOSED")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("no mitigations")));
        mvc.perform(get("/api/risks/" + riskId)).andExpect(jsonPath("$.status").value("OPEN"));

        addMitigation(riskId, "Secondary vendor contract", 4);
        mvc.perform(put("/api/risks/" + riskId).contentType(MediaType.APPLICATION_JSON)
                        .content(riskJson("Vendor outage", "OPERATIONAL", 3, 3, "CLOSED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void cannotDeleteLastMitigationOfClosedRisk() throws Exception {
        long riskId = createRisk(riskJson("Phishing", "SECURITY", 3, 4, null));
        long m1 = addMitigation(riskId, "Awareness training", 3);
        mvc.perform(put("/api/risks/" + riskId).contentType(MediaType.APPLICATION_JSON)
                        .content(riskJson("Phishing", "SECURITY", 3, 4, "CLOSED")))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/risks/" + riskId + "/mitigations/" + m1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_MITIGATION_ON_CLOSED_RISK"));

        long m2 = addMitigation(riskId, "Email filtering", 4);
        mvc.perform(delete("/api/risks/" + riskId + "/mitigations/" + m1)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/risks/" + riskId + "/mitigations/" + m2)).andExpect(status().isConflict());
    }

    // ---------- validation ----------

    @Test
    void invalidRatingsAreRejectedWithClearMessages() throws Exception {
        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content(riskJson("x", "SECURITY", 0, 3, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'likelihood')].message",
                        hasItem("likelihood must be an integer between 1 and 5")));

        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content(riskJson("x", "SECURITY", 3, 6, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'impact')]").isNotEmpty());

        // non-integer numbers must not be silently truncated
        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"category\":\"SECURITY\",\"owner\":\"a\",\"likelihood\":3.5,\"impact\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("likelihood must be an integer")));

        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"category\":\"SECURITY\",\"owner\":\"a\",\"likelihood\":\"abc\",\"impact\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("likelihood")));

        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"category\":\"SECURITY\",\"owner\":\"a\",\"likelihood\":3,\"impact\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')]").isNotEmpty());
    }

    @Test
    void invalidMitigationEffectivenessIsRejected() throws Exception {
        long riskId = createRisk(riskJson("Risk", "FINANCIAL", 2, 2, null));
        for (String bad : new String[] {"0", "6", "-1", "2.5"}) {
            mvc.perform(post("/api/risks/" + riskId + "/mitigations").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\":\"d\",\"effectiveness\":" + bad + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("effectiveness")));
        }
        mvc.perform(get("/api/risks/" + riskId)).andExpect(jsonPath("$.mitigationCount").value(0));
    }

    @Test
    void unknownEnumValueListsAllowedValues() throws Exception {
        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content(riskJson("x", "WEATHER", 3, 3, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", allOf(containsString("category"), containsString("SECURITY"))));
    }

    // ---------- not found / delete ----------

    @Test
    void unknownIdsReturn404_andDeletingRiskRemovesItsMitigations() throws Exception {
        mvc.perform(get("/api/risks/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(post("/api/risks/999999/mitigations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"d\",\"effectiveness\":3}"))
                .andExpect(status().isNotFound());

        long riskId = createRisk(riskJson("Temp", "STRATEGIC", 1, 1, null));
        addMitigation(riskId, "m", 2);
        mvc.perform(delete("/api/risks/" + riskId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/risks/" + riskId)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/risks/" + riskId)).andExpect(status().isNotFound());
    }

    @Test
    void mitigationMustBelongToTheRiskInThePath() throws Exception {
        long riskA = createRisk(riskJson("A", "SECURITY", 2, 2, null));
        long riskB = createRisk(riskJson("B", "SECURITY", 2, 2, null));
        long mitigationOnA = addMitigation(riskA, "m", 3);
        mvc.perform(delete("/api/risks/" + riskB + "/mitigations/" + mitigationOnA))
                .andExpect(status().isNotFound());
    }

    // ---------- list / filter / sort ----------

    @Test
    void listFiltersByCategoryAndStatus_andSortsByResidualDescendingByDefault() throws Exception {
        long a = createRisk(riskJson("A-critical-unmitigated", "SECURITY", 5, 5, null));   // 25 / 25
        long b = createRisk(riskJson("B-low", "SECURITY", 2, 2, null));                    // 4 / 4
        long c = createRisk(riskJson("C-high-but-mitigated", "FINANCIAL", 4, 4, null));    // 16 / 4
        addMitigation(c, "Hedging", 5);

        // default sort: residual desc, ties broken by inherent desc
        mvc.perform(get("/api/risks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].title").value("A-critical-unmitigated"))
                .andExpect(jsonPath("$[1].title").value("C-high-but-mitigated"))
                .andExpect(jsonPath("$[2].title").value("B-low"));

        mvc.perform(get("/api/risks?sortBy=residual&direction=asc"))
                .andExpect(jsonPath("$[0].title").value("B-low"))
                .andExpect(jsonPath("$[2].title").value("A-critical-unmitigated"));

        mvc.perform(get("/api/risks?category=security"))   // case-insensitive
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].category", everyItem(is("SECURITY"))));

        mvc.perform(get("/api/risks?status=CLOSED")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/risks?category=FINANCIAL&status=OPEN"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mitigationCount").value(1));

        mvc.perform(get("/api/risks?category=nope")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/risks?sortBy=color")).andExpect(status().isBadRequest());
    }

    // ---------- next review date ----------

    @Test
    void nextReviewDate_isReturned_andOverdueOnlyOnceDatePassed() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now();
        long past = createRisk(withReview(riskJson("Past", "SECURITY", 3, 3, null), today.minusDays(1).toString()));
        long due = createRisk(withReview(riskJson("Today", "SECURITY", 3, 3, null), today.toString()));
        long future = createRisk(withReview(riskJson("Future", "SECURITY", 3, 3, null), today.plusDays(7).toString()));
        long none = createRisk(riskJson("None", "SECURITY", 3, 3, null));

        mvc.perform(get("/api/risks/" + past))
                .andExpect(jsonPath("$.nextReviewDate").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.overdue").value(true));
        mvc.perform(get("/api/risks/" + due)).andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(get("/api/risks/" + future)).andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(get("/api/risks/" + none))
                .andExpect(jsonPath("$.nextReviewDate", nullValue()))
                .andExpect(jsonPath("$.overdue").value(false));

        // closed risks are never overdue
        addMitigation(past, "Control", 3);
        mvc.perform(put("/api/risks/" + past).contentType(MediaType.APPLICATION_JSON)
                        .content(withReview(riskJson("Past", "SECURITY", 3, 3, "CLOSED"), today.minusDays(1).toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdue").value(false));

        // list exposes the same flag
        mvc.perform(get("/api/risks?status=OPEN"))
                .andExpect(jsonPath("$[?(@.title == 'Future')].overdue", hasItem(false)));
    }

    @Test
    void invalidNextReviewDateIsRejected() throws Exception {
        mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON)
                        .content(withReview(riskJson("x", "SECURITY", 3, 3, null), "10/01/2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("nextReviewDate must be a date in YYYY-MM-DD")));
    }

    // ---------- helpers ----------

    private static String withReview(String json, String date) {
        return json.substring(0, json.length() - 1) + ",\"nextReviewDate\":\"" + date + "\"}";
    }


    private long createRisk(String json) throws Exception {
        ResultActions result = mvc.perform(post("/api/risks").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/risks/")));
        String body = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long addMitigation(long riskId, String description, int effectiveness) throws Exception {
        String body = mvc.perform(post("/api/risks/" + riskId + "/mitigations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + description + "\",\"effectiveness\":" + effectiveness + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private static String riskJson(String title, String category, int likelihood, int impact, String status) {
        String statusPart = status == null ? "" : ",\"status\":\"" + status + "\"";
        return "{\"title\":\"" + title + "\",\"description\":\"desc\",\"category\":\"" + category
                + "\",\"owner\":\"alice\",\"likelihood\":" + likelihood + ",\"impact\":" + impact + statusPart + "}";
    }
}
