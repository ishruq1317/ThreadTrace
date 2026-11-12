package com.threadtrace;

import com.threadtrace.lineage.LineageDtos;
import com.threadtrace.lineage.LineageService;
import com.threadtrace.lineage.LotType;
import com.threadtrace.lineage.LotUnit;
import com.threadtrace.parties.PartyDtos;
import com.threadtrace.parties.PartyService;
import com.threadtrace.parties.PartyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresTestcontainersIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("threadtrace")
            .withUsername("threadtrace")
            .withPassword("threadtrace_test");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("threadtrace.security.users[0].username", () -> "tc-admin");
        registry.add("threadtrace.security.users[0].password", () -> "{noop}tc-pass");
        registry.add("threadtrace.security.users[0].roles[0]", () -> "ADMIN");
    }

    @Autowired
    private PartyService partyService;

    @Autowired
    private LineageService lineageService;

    @Test
    @WithMockUser(username = "tc-admin", roles = {"ADMIN", "AUDITOR"})
    void postgresRecursiveTraceWorksWithFlywaySchema() {
        partyService.registerParty(new PartyDtos.CreatePartyRequest(
                "PG-MILL", "PG Mill", PartyType.FABRIC_MILL, "Bangladesh", "Gazipur",
                "Secret Rd", 100, BigDecimal.TEN));
        lineageService.recordReceive(new LineageDtos.ReceiveLotRequest(
                "PG-LOT-1", LotType.FABRIC, "Cotton", "ST-PG", "BRAND_PG",
                "PG-MILL", null, null, BigDecimal.valueOf(100), BigDecimal.valueOf(100), 0,
                LotUnit.KG, null, null, null, null, null, "PG receive", Instant.now()));
        lineageService.recordTransform(new LineageDtos.TransformLotRequest(
                "PG-LOT-1", "PG-LOT-2", LotType.CUT_BUNDLE, com.threadtrace.lineage.EventType.CUT,
                "cut", "ST-PG", null, null, BigDecimal.valueOf(100), BigDecimal.valueOf(92),
                BigDecimal.valueOf(92), 200, LotUnit.KG, null, null, null, null, null, "PG cut", Instant.now()));

        LineageDtos.TraceResponse back = lineageService.traceBackward("PG-LOT-2", 6);
        assertFalse(back.steps().isEmpty());
        assertEquals("PG-LOT-1", back.steps().getFirst().parentLotCode());
    }
}
