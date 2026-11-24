package com.threadtrace;

import com.threadtrace.config.SecurityConfig;
import com.threadtrace.config.SecurityProperties;
import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.footprint.FootprintRollupVisitor;
import com.threadtrace.passport.PassportDtos;
import com.threadtrace.passport.PassportService;
import com.threadtrace.passport.PassportStatus;
import com.threadtrace.passport.PublicPassportController;
import com.threadtrace.passport.PublicPassportWebController;
import com.threadtrace.passport.QrCodeGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PublicPassportController.class, PublicPassportWebController.class})
@Import({SecurityConfig.class, QrCodeGenerator.class})
@EnableConfigurationProperties({ThreadTraceProperties.class, SecurityProperties.class})
class PublicPassportWebSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PassportService passportService;

    @Test
    void servesPublicPassportJsonHtmlAndQrWithCachingHeaders() throws Exception {
        PassportDtos.PublicPassportView view = new PassportDtos.PublicPassportView(
                "PP-SLICE-1",
                1,
                "ST-SLICE",
                "Slice Test Jacket",
                "BRAND_A",
                PassportStatus.PUBLISHED,
                List.of(new PassportDtos.FibreComponent("Recycled Polyester", "RECYCLED", BigDecimal.valueOf(75))),
                List.of(new PassportDtos.PublicOriginStep(1, "cut", "Demo Factory", "FACTORY", "Bangladesh", "Gazipur")),
                List.of(),
                "Wash cold",
                "Recycle",
                new FootprintRollupVisitor.FootprintSummary(
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.valueOf(0.1), BigDecimal.valueOf(0.1), 10, "BY_MASS"),
                Map.of("season", "AW26"),
                "https://threadtrace.onrender.com/api/v1/public/passports/PP-SLICE-1/qr",
                Instant.now()
        );

        when(passportService.getPublicPassportWithFallback("PP-SLICE-1", null))
                .thenReturn(new PassportService.PublicPassportResponse(view, "\"etag-slice-1\"", false));

        mockMvc.perform(get("/api/v1/public/passports/PP-SLICE-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"etag-slice-1\""))
                .andExpect(jsonPath("$.passportCode").value("PP-SLICE-1"));

        mockMvc.perform(get("/api/v1/public/passports/PP-SLICE-1").header("If-None-Match", "\"etag-slice-1\""))
                .andExpect(status().isNotModified());

        mockMvc.perform(get("/p/PP-SLICE-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Slice Test Jacket")))
                .andExpect(content().string(containsString("Hasin Ishruq")))
                .andExpect(content().string(not(containsString("CSE 444"))));

        mockMvc.perform(get("/api/v1/public/passports/PP-SLICE-1/qr"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void servesInteractivePortalUiAndHandlesForwardedHostQr() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ThreadTrace")))
                .andExpect(content().string(containsString("MILL-GAZIPUR-1")))
                .andExpect(content().string(containsString("GOTS-2026-BD-01")))
                .andExpect(content().string(containsString("LOT-RAW-001")))
                .andExpect(content().string(containsString("LOT-GARMENT-001")))
                .andExpect(content().string(containsString("PP-HOODIE-2026")))
                .andExpect(content().string(containsString("https://threadtrace.onrender.com/p/PP-HOODIE-2026")));

        mockMvc.perform(get("/ui"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Authorize")));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("change_me_operator_pw")));

        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"page-login\"")))
                .andExpect(content().string(containsString("id=\"page-home\"")))
                .andExpect(content().string(containsString("id=\"page-passport\"")))
                .andExpect(content().string(containsString("Hasin Ishruq")))
                .andExpect(content().string(not(containsString("CSE 444"))))
                .andExpect(content().string(not(containsString("topAuthorizeBtn"))))
                .andExpect(content().string(not(containsString("View or Edit Request Body (JSON)"))))
                .andExpect(content().string(not(containsString("Back to Home Page"))))
                .andExpect(content().string(containsString("passportLogoutBtn")))
                .andExpect(content().string(containsString("ishruq1317")))
                .andExpect(content().string(containsString("tt_completed_steps")))
                .andExpect(content().string(containsString(".onrender.com")))
                .andExpect(content().string(containsString("executeRegisterProductionLine")))
                .andExpect(content().string(containsString("executeSplitLot")))
                .andExpect(content().string(containsString("executeMergeLots")))
                .andExpect(content().string(containsString("executeCorrectionEvent")))
                .andExpect(content().string(containsString("executeTraceability")))
                .andExpect(content().string(containsString("executeEvaluateLotClaims")))
                .andExpect(content().string(containsString("executeCalculateFootprint")))
                .andExpect(content().string(containsString("executeUploadCertificateMultipart")))
                .andExpect(content().string(containsString("executeEvaluateExpiry")))
                .andExpect(content().string(containsString("executeListPassportVersions")))
                .andExpect(content().string(containsString("executeAudienceProjectionCheck")))
                .andExpect(content().string(containsString("executeBulkImport")))
                .andExpect(content().string(containsString("executeVerifyPassportIntegrity")))
                .andExpect(content().string(not(containsString("swagger-ui.html"))))
                .andExpect(content().string(not(containsString("localhost"))))
                .andExpect(content().string(not(containsString("runFullDemoPipeline"))));

        mockMvc.perform(get("/passport-view"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://threadtrace.onrender.com/p/PP-HOODIE-2026")));

        mockMvc.perform(get("/api/v1/public/passports/PP-HOODIE-2026/qr")
                        .header("X-Forwarded-Host", "threadtrace.onrender.com")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }
}
