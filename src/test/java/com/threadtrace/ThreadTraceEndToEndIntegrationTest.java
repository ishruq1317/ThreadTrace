package com.threadtrace;

import com.threadtrace.passport.ProductPassportRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "threadtrace.evidence.allowed-certificate-types=GOTS,GRS,OEKO_TEX_100,CUSTOM_MILL_CERT",
        "threadtrace.passport.extra-fields.custom-qa5-field=ConfiguredWithoutCodeEdit"
})
class ThreadTraceEndToEndIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private ProductPassportRepository passportRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        reset(passportRepository);
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void fullSupplyChainTraceabilityClaimsIntegrityConfidentialityAndResilience() throws Exception {
        // 1. Register Parties & Production Line (FR1) with confidential fields
        createParty("MILL-GOTS-1", "Dhaka Organic Mill", "FABRIC_MILL", "Bangladesh", "Gazipur",
                "Secret Mill Road 99, Gazipur", 850, "14.50");
        createParty("FAC-1", "Narayanganj Apparels", "FACTORY", "Bangladesh", "Narayanganj",
                "Confidential Factory Plot 42", 1200, "9.25");
        createParty("BRAND_A", "Nordic Loom AB", "BRAND", "Sweden", "Stockholm",
                "HQ Secret St 1", 90, null);
        createParty("BRAND_B", "Berlin Wear GmbH", "BRAND", "Germany", "Berlin",
                "Private Ave 7", 60, null);

        mockMvc.perform(post("/api/v1/parties/FAC-1/lines")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lineCode": "LINE-CUT-01",
                                  "name": "Cutting Line A",
                                  "processType": "cut",
                                  "locationDetail": "Floor 2"
                                }
                                """))
                .andExpect(status().isCreated());

        // 2. Register Certificate (FR5 + QA5 custom type)
        LocalDate validFrom = LocalDate.now().minusMonths(2);
        LocalDate validTo = LocalDate.now().plusDays(20); // within 30-day warning window -> triggers expiring alert
        mockMvc.perform(post("/api/v1/evidence/certificates")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "certificateNumber": "CERT-GOTS-2026",
                                  "certificateType": "CUSTOM_MILL_CERT",
                                  "issuer": "Control Union",
                                  "scope": "Organic Cotton Fabric",
                                  "claimedAttribute": "ORGANIC",
                                  "certifiedPct": 80.0,
                                  "supplierCode": "MILL-GOTS-1",
                                  "validFrom": "%s",
                                  "validTo": "%s"
                                }
                                """.formatted(validFrom, validTo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EXPIRING_SOON"));

        // 3. Receive Fabric Lot (FR2) for BRAND_A
        mockMvc.perform(post("/api/v1/events/receive")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lotCode": "LOT-FAB-A1",
                                  "lotType": "FABRIC",
                                  "materialType": "Organic Cotton Jersey",
                                  "styleCode": "STY-TEE-01",
                                  "brandCode": "BRAND_A",
                                  "supplierCode": "MILL-GOTS-1",
                                  "factoryCode": "FAC-1",
                                  "quantity": 100.0,
                                  "massKg": 100.0,
                                  "pieceCount": 0,
                                  "unit": "KG",
                                  "claimedAttribute": "ORGANIC",
                                  "claimedPct": 80.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));

        // Receive another lot for BRAND_B (for cross-brand isolation check QA3)
        mockMvc.perform(post("/api/v1/events/receive")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lotCode": "LOT-FAB-B1",
                                  "lotType": "FABRIC",
                                  "materialType": "Recycled Polyester",
                                  "styleCode": "STY-B-01",
                                  "brandCode": "BRAND_B",
                                  "supplierCode": "MILL-GOTS-1",
                                  "factoryCode": "FAC-1",
                                  "quantity": 50.0,
                                  "massKg": 50.0,
                                  "pieceCount": 0,
                                  "unit": "KG"
                                }
                                """))
                .andExpect(status().isCreated());

        // 4. Split LOT-FAB-A1 into two lots (FR2)
        mockMvc.perform(post("/api/v1/events/split")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentLotCode": "LOT-FAB-A1",
                                  "factoryCode": "FAC-1",
                                  "inputQuantity": 100.0,
                                  "outputs": [
                                    {
                                      "outputLotCode": "LOT-FAB-A1-S1",
                                      "outputLotType": "FABRIC",
                                      "outputQuantity": 60.0,
                                      "outputMassKg": 60.0,
                                      "outputPieceCount": 0,
                                      "unit": "KG"
                                    },
                                    {
                                      "outputLotCode": "LOT-FAB-A1-S2",
                                      "outputLotType": "FABRIC",
                                      "outputQuantity": 40.0,
                                      "outputMassKg": 40.0,
                                      "outputPieceCount": 0,
                                      "unit": "KG"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        // 5. Cut -> Sew -> Merge -> Ship for BRAND_A
        // Cut within 12% loss limit (60 kg -> 54 kg = 10% loss, 200 pieces)
        mockMvc.perform(post("/api/v1/events/transform")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputLotCode": "LOT-FAB-A1-S1",
                                  "outputLotCode": "LOT-CUT-A1",
                                  "outputLotType": "CUT_BUNDLE",
                                  "eventType": "CUT",
                                  "processStep": "cut",
                                  "factoryCode": "FAC-1",
                                  "productionLineCode": "LINE-CUT-01",
                                  "inputQuantity": 60.0,
                                  "outputQuantity": 200,
                                  "outputMassKg": 54.0,
                                  "outputPieceCount": 200,
                                  "unit": "PIECES"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PRODUCTION"));

        // Cut S2 within 12% loss (40 kg -> 36 kg, 120 pieces)
        mockMvc.perform(post("/api/v1/events/transform")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputLotCode": "LOT-FAB-A1-S2",
                                  "outputLotCode": "LOT-CUT-A2",
                                  "outputLotType": "CUT_BUNDLE",
                                  "eventType": "CUT",
                                  "processStep": "cut",
                                  "factoryCode": "FAC-1",
                                  "inputQuantity": 40.0,
                                  "outputQuantity": 120,
                                  "outputMassKg": 36.0,
                                  "outputPieceCount": 120,
                                  "unit": "PIECES"
                                }
                                """))
                .andExpect(status().isCreated());

        // Merge CUT-A1 + CUT-A2 -> SEW-A1 (320 pieces, 90 kg)
        mockMvc.perform(post("/api/v1/events/merge")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputs": [
                                    { "parentLotCode": "LOT-CUT-A1", "inputQuantity": 200, "inputMassKg": 54.0, "inputPieceCount": 200 },
                                    { "parentLotCode": "LOT-CUT-A2", "inputQuantity": 120, "inputMassKg": 36.0, "inputPieceCount": 120 }
                                  ],
                                  "outputLotCode": "LOT-SEW-A1",
                                  "outputLotType": "SEWN_GARMENT",
                                  "materialType": "Organic Cotton Jersey",
                                  "styleCode": "STY-TEE-01",
                                  "processStep": "sew",
                                  "factoryCode": "FAC-1",
                                  "outputQuantity": 320,
                                  "outputMassKg": 90.0,
                                  "outputPieceCount": 320,
                                  "unit": "PIECES",
                                  "claimedAttribute": "ORGANIC",
                                  "claimedPct": 80.0
                                }
                                """))
                .andExpect(status().isCreated());

        // Wash -> Ship
        mockMvc.perform(post("/api/v1/events/transform")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputLotCode": "LOT-SEW-A1",
                                  "outputLotCode": "LOT-SHIP-A1",
                                  "outputLotType": "SHIPMENT",
                                  "eventType": "SHIP",
                                  "processStep": "ship",
                                  "factoryCode": "FAC-1",
                                  "inputQuantity": 320,
                                  "outputQuantity": 320,
                                  "outputMassKg": 90.0,
                                  "outputPieceCount": 320,
                                  "unit": "PIECES"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 6. Test Correction Event (FR2) — appends immutable correction event on LOT-CUT-A1
        MvcResult cutEventsRes = mockMvc.perform(get("/api/v1/lots/LOT-CUT-A1/events")
                        .with(httpBasic("operator", "test-operator-pass")))
                .andExpect(status().isOk())
                .andReturn();
        String firstCutEventCode = objectMapper.readTree(cutEventsRes.getResponse().getContentAsString())
                .get("content").get(0).get("eventCode").asText();

        mockMvc.perform(post("/api/v1/events/correction")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lotCode": "LOT-CUT-A1",
                                  "correctsEventCode": "%s",
                                  "correctedInputQuantity": 60.0,
                                  "correctedOutputQuantity": 200,
                                  "unit": "PIECES",
                                  "clearFlag": false,
                                  "notes": "Verified bundle count post-inspection"
                                }
                                """.formatted(firstCutEventCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("CORRECTION"))
                .andExpect(jsonPath("$.sequenceNumber").value(2));

        // 7. Traceability (FR3): Backward and Forward trace with paths
        mockMvc.perform(get("/api/v1/traces/backward/LOT-SHIP-A1")
                        .with(httpBasic("brand_a", "test-branda-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("BACKWARD"))
                .andExpect(jsonPath("$.steps[0].path").isNotEmpty());

        mockMvc.perform(get("/api/v1/traces/forward/LOT-FAB-A1")
                        .with(httpBasic("brand_a", "test-branda-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("FORWARD"))
                .andExpect(jsonPath("$.stepCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.steps[0].buyerBrandCode").value("BRAND_A"));

        // Style-based backward trace (FR3)
        mockMvc.perform(get("/api/v1/traces/backward/style/STY-TEE-01")
                        .with(httpBasic("brand_a", "test-branda-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("BACKWARD_BY_STYLE"))
                .andExpect(jsonPath("$.stepCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));

        // 8. Footprint Roll-up (FR6): BY_MASS and BY_PIECE_COUNT (exact 224.0 L with zero diamond double-counting)
        mockMvc.perform(get("/api/v1/footprint/lots/LOT-SHIP-A1?allocationMethod=BY_MASS")
                        .with(httpBasic("brand_a", "test-branda-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationMethod").value("BY_MASS"))
                .andExpect(jsonPath("$.totalWaterLiters").value(224.0))
                .andExpect(jsonPath("$.co2eKgPerPiece").isNumber());

        mockMvc.perform(get("/api/v1/footprint/lots/LOT-SHIP-A1?allocationMethod=BY_PIECE_COUNT")
                        .with(httpBasic("brand_a", "test-branda-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationMethod").value("BY_PIECE_COUNT"));

        // 9. Generate Passport v1 (FR7, QA5 custom field)
        mockMvc.perform(post("/api/v1/passports")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passportCode": "PP-A1",
                                  "shipmentLotCode": "LOT-SHIP-A1",
                                  "styleCode": "STY-TEE-01",
                                  "styleName": "Nordic Organic Tee",
                                  "careAndRepair": "Machine wash 30C; free seam repair at stores",
                                  "endOfLifeGuidance": "Return to textile recycling bin"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.additionalFields.custom-qa5-field").value("ConfiguredWithoutCodeEdit"))
                .andExpect(jsonPath("$.originByProcessStep[0].exactAddress").isNotEmpty())
                .andExpect(jsonPath("$.originByProcessStep[0].workerCount").isNumber());

        // 10. Public Passport JSON, Thymeleaf HTML, QR PNG, ETag 304, Zero Confidential Fields (FR7, FR8, QA3, QA4)
        MvcResult pubJsonRes = mockMvc.perform(get("/api/v1/public/passports/PP-A1"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(header().string("Cache-Control", containsString("max-age=60")))
                .andExpect(content().string(not(containsString("Secret Mill Road 99"))))
                .andExpect(content().string(not(containsString("Confidential Factory Plot 42"))))
                .andExpect(content().string(not(containsString("workerCount"))))
                .andExpect(content().string(not(containsString("unitPriceUsd"))))
                .andReturn();

        String etag = pubJsonRes.getResponse().getHeader("ETag");
        mockMvc.perform(get("/api/v1/public/passports/PP-A1").header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        mockMvc.perform(get("/p/PP-A1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nordic Organic Tee")))
                .andExpect(content().string(not(containsString("Secret Mill Road 99"))))
                .andExpect(content().string(not(containsString("Confidential Factory Plot 42"))));

        mockMvc.perform(get("/api/v1/public/passports/PP-A1/qr"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // Cross-brand access refused (FR8, QA3)
        mockMvc.perform(get("/api/v1/lots/LOT-FAB-B1")
                        .with(httpBasic("brand_a", "test-branda-pass")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access Denied"));

        mockMvc.perform(get("/api/v1/passports/PP-A1")
                        .with(httpBasic("brand_b", "test-brandb-pass")))
                .andExpect(status().isForbidden());

        // Cross-brand passport code hijack refused
        mockMvc.perform(post("/api/v1/passports")
                        .with(httpBasic("brand_b", "test-brandb-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passportCode": "PP-A1",
                                  "shipmentLotCode": "LOT-FAB-B1",
                                  "styleCode": "STY-B-01",
                                  "styleName": "Hijack Attempt",
                                  "careAndRepair": "Wash",
                                  "endOfLifeGuidance": "Recycle"
                                }
                                """))
                .andExpect(status().isForbidden());

        // Lot-attached certificate for BRAND_B refused to BRAND_A
        mockMvc.perform(post("/api/v1/evidence/certificates")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "certificateNumber": "CERT-B-PRIVATE",
                                  "certificateType": "OEKO_TEX_100",
                                  "issuer": "Intertek",
                                  "scope": "Lot B1 Test",
                                  "lotCode": "LOT-FAB-B1",
                                  "validFrom": "%s",
                                  "validTo": "%s"
                                }
                                """.formatted(validFrom, validTo.plusMonths(6))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/evidence/certificates/CERT-B-PRIVATE/file")
                        .with(httpBasic("brand_a", "test-branda-pass")))
                .andExpect(status().isForbidden());

        // 11. QA4: Database-down fallback serves public passport from cache!
        doThrow(new DataAccessResourceFailureException("Simulated PostgreSQL outage"))
                .when(passportRepository).findByPassportCodeAndLatestVersionTrue("PP-A1");

        mockMvc.perform(get("/api/v1/public/passports/PP-A1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-ThreadTrace-Cache", "FALLBACK"))
                .andExpect(jsonPath("$.passportCode").value("PP-A1"));

        mockMvc.perform(get("/p/PP-A1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-ThreadTrace-Cache", "FALLBACK"))
                .andExpect(content().string(containsString("Served from resilient edge cache")));

        reset(passportRepository);

        // 12. FR5 + QA2: Certificate expires -> automatic passport v2 drops claim, v1 remains retrievable
        LocalDate expiredCheckDate = validTo.plusDays(2);
        mockMvc.perform(post("/api/v1/evidence/certificates/evaluate-expiry?asOfDate=" + expiredCheckDate)
                        .with(httpBasic("auditor", "test-auditor-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredCount").value(1));

        mockMvc.perform(get("/api/v1/public/passports/PP-A1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("CLAIMS_DOWNGRADED"))
                .andExpect(jsonPath("$.certifications").isEmpty())
                .andExpect(jsonPath("$.fibreComposition[0].verifiedPercentage").value(0));

        // Version 1 still retrievable & paginated versions list (C3, FR7)
        mockMvc.perform(get("/api/v1/passports/PP-A1/versions/1")
                        .with(httpBasic("auditor", "test-auditor-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/passports/PP-A1/versions")
                        .with(httpBasic("auditor", "test-auditor-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // 13. QA2 Planted Problems: cutting loss > 12%, over-claimed content, orphan lot
        // Cutting loss 25% (> 12% limit)
        mockMvc.perform(post("/api/v1/events/transform")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputLotCode": "LOT-FAB-B1",
                                  "outputLotCode": "LOT-CUT-BAD-LOSS",
                                  "outputLotType": "CUT_BUNDLE",
                                  "eventType": "CUT",
                                  "processStep": "cut",
                                  "factoryCode": "FAC-1",
                                  "inputQuantity": 40.0,
                                  "outputQuantity": 100,
                                  "outputMassKg": 30.0,
                                  "outputPieceCount": 100,
                                  "unit": "PIECES"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FLAGGED"));

        // Over-claimed content (LOT-FAB-B1 has 0% certified organic, claiming 90% ORGANIC)
        mockMvc.perform(post("/api/v1/events/transform")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inputLotCode": "LOT-FAB-B1",
                                  "outputLotCode": "LOT-OVERCLAIM",
                                  "outputLotType": "CUT_BUNDLE",
                                  "eventType": "CUT",
                                  "processStep": "cut",
                                  "factoryCode": "FAC-1",
                                  "inputQuantity": 40.0,
                                  "outputQuantity": 100,
                                  "outputMassKg": 38.0,
                                  "outputPieceCount": 100,
                                  "unit": "PIECES",
                                  "claimedAttribute": "ORGANIC",
                                  "claimedPct": 90.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FLAGGED"));

        // Split over-claim (LOT-FAB-A1 is 80% organic; split child claiming 100% organic must be FLAGGED)
        mockMvc.perform(post("/api/v1/events/split")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentLotCode": "LOT-FAB-A1",
                                  "factoryCode": "FAC-1",
                                  "inputQuantity": 10.0,
                                  "outputs": [
                                    {
                                      "outputLotCode": "LOT-SPLIT-OVERCLAIM",
                                      "outputLotType": "FABRIC",
                                      "outputQuantity": 10.0,
                                      "outputMassKg": 10.0,
                                      "outputPieceCount": 0,
                                      "unit": "KG",
                                      "claimedAttribute": "ORGANIC",
                                      "claimedPct": 100.0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].status").value("FLAGGED"));

        // Orphan lot (no supplierCode)
        mockMvc.perform(post("/api/v1/events/receive")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lotCode": "LOT-ORPHAN-1",
                                  "lotType": "FABRIC",
                                  "materialType": "Unknown Cotton",
                                  "brandCode": "BRAND_B",
                                  "quantity": 25.0,
                                  "massKg": 25.0,
                                  "pieceCount": 0,
                                  "unit": "KG"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orphan").value(true))
                .andExpect(jsonPath("$.status").value("FLAGGED"));

        mockMvc.perform(get("/api/v1/compliance/alerts")
                        .with(httpBasic("auditor", "test-auditor-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));

        // 14. FR9: Bulk Import CSV + Excel with valid & rejected rows
        String csvData = """
                inputLotCode,outputLotCode,outputLotType,styleCode,factoryCode,productionLineCode,inputQuantity,outputQuantity,outputMassKg,outputPieceCount,unit
                LOT-FAB-B1,LOT-CSV-OK,CUT_BUNDLE,STY-B-01,FAC-1,LINE-CUT-01,10.0,30,9.5,30,PIECES
                MISSING-LOT,LOT-CSV-BAD,CUT_BUNDLE,STY-B-01,FAC-1,LINE-CUT-01,10.0,30,9.5,30,PIECES
                LOT-FAB-B1,LOT-CSV-BADNUM,CUT_BUNDLE,STY-B-01,FAC-1,LINE-CUT-01,BAD_NUM,30,9.5,30,PIECES
                """;
        MockMultipartFile csvFile = new MockMultipartFile("file", "cutting.csv", "text/csv",
                csvData.getBytes(StandardCharsets.UTF_8));
        MvcResult csvRes = mockMvc.perform(multipart("/api/v1/imports")
                        .file(csvFile)
                        .param("importType", "CUTTING_REPORT")
                        .param("fileFormat", "CSV")
                        .with(httpBasic("operator", "test-operator-pass")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedRows").value(1))
                .andExpect(jsonPath("$.rejectedRows").value(2))
                .andReturn();
        String batchCode = objectMapper.readTree(csvRes.getResponse().getContentAsString()).get("batchCode").asText();

        mockMvc.perform(get("/api/v1/imports/" + batchCode + "/rejected")
                        .with(httpBasic("operator", "test-operator-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2));

        // Excel .xlsx import
        byte[] xlsxBytes = buildSampleWorkbook();
        MockMultipartFile xlsxFile = new MockMultipartFile("file", "packing.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes);
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(xlsxFile)
                        .param("importType", "PACKING_LIST")
                        .param("fileFormat", "EXCEL")
                        .with(httpBasic("operator", "test-operator-pass")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedRows").value(1))
                .andExpect(jsonPath("$.rejectedRows").value(1));

        // 15. FR10: Hash-chain verification passes before tampering, detects tampering after SQL UPDATE
        MvcResult okVerify = mockMvc.perform(get("/api/v1/integrity/passports/PP-A1/verify")
                        .with(httpBasic("auditor", "test-auditor-pass")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode okNode = objectMapper.readTree(okVerify.getResponse().getContentAsString());
        assertTrue(okNode.get("verified").asBoolean());

        // Simulate direct DB tampering on an event in LOT-FAB-A1
        jdbcTemplate.update("UPDATE lot_events SET output_quantity = 9999 WHERE lot_id = (SELECT id FROM lots WHERE lot_code = 'LOT-FAB-A1')");

        MvcResult tamperedVerify = mockMvc.perform(get("/api/v1/integrity/passports/PP-A1/verify")
                        .with(httpBasic("auditor", "test-auditor-pass")))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tamperedNode = objectMapper.readTree(tamperedVerify.getResponse().getContentAsString());
        assertFalse(tamperedNode.get("verified").asBoolean());

        // 16. C3 & C4: Actuator health + OpenAPI docs + RFC 9457 401/400 validation error
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/lots"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication Required"));

        mockMvc.perform(post("/api/v1/parties")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Request Validation Failed"))
                .andExpect(jsonPath("$.errors.code").exists());
    }

    private void createParty(
            String code, String name, String type, String country, String region,
            String exactAddress, Integer workers, String price) throws Exception {
        String priceJson = price == null ? "null" : price;
        mockMvc.perform(post("/api/v1/parties")
                        .with(httpBasic("operator", "test-operator-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "%s",
                                  "name": "%s",
                                  "partyType": "%s",
                                  "country": "%s",
                                  "region": "%s",
                                  "exactAddress": "%s",
                                  "workerCount": %d,
                                  "unitPriceUsd": %s
                                }
                                """.formatted(code, name, type, country, region, exactAddress, workers, priceJson)))
                .andExpect(status().isCreated());
    }

    private static byte[] buildSampleWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Packing");
            Row header = sheet.createRow(0);
            String[] cols = {"inputLotCode", "outputLotCode", "outputLotType", "styleCode", "factoryCode",
                    "productionLineCode", "inputQuantity", "outputQuantity", "outputMassKg", "outputPieceCount", "unit"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }
            Row r1 = sheet.createRow(1);
            String[] v1 = {"LOT-CSV-OK", "LOT-PACK-XLSX-1", "CARTON", "STY-B-01", "FAC-1",
                    "LINE-CUT-01", "30", "30", "9.5", "30", "PIECES"};
            for (int i = 0; i < v1.length; i++) {
                r1.createCell(i).setCellValue(v1[i]);
            }
            Row r2 = sheet.createRow(2);
            String[] v2 = {"NON_EXISTENT_LOT", "LOT-PACK-XLSX-BAD", "CARTON", "STY-B-01", "FAC-1",
                    "LINE-CUT-01", "30", "30", "9.5", "30", "PIECES"};
            for (int i = 0; i < v2.length; i++) {
                r2.createCell(i).setCellValue(v2[i]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }
}
