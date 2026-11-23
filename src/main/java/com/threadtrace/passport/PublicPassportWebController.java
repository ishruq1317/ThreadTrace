package com.threadtrace.passport;

import com.threadtrace.config.ThreadTraceProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.concurrent.TimeUnit;

@Controller
public class PublicPassportWebController {

    private final PassportService passportService;
    private final ThreadTraceProperties properties;

    public PublicPassportWebController(PassportService passportService, ThreadTraceProperties properties) {
        this.passportService = passportService;
        this.properties = properties;
    }

    @GetMapping({"/", "/ui", "/login", "/home", "/passport-view"})
    public String viewPortalHome(Model model) {
        model.addAttribute("configuredBaseUrl", properties.getPassport().getPublicBaseUrl());
        return "index";
    }

    @GetMapping("/p/{passportCode}")
    public String viewPublicPassport(
            @PathVariable String passportCode,
            @RequestParam(required = false) Integer version,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            HttpServletResponse httpResponse,
            Model model) {

        PassportService.PublicPassportResponse response = passportService.getPublicPassportWithFallback(passportCode, version);
        String cacheHeader = CacheControl.maxAge(properties.getPassport().getCacheTtlSeconds(), TimeUnit.SECONDS)
                .cachePublic()
                .staleWhileRevalidate(properties.getPassport().getStaleWhileRevalidateSeconds(), TimeUnit.SECONDS)
                .getHeaderValue();
        httpResponse.setHeader("Cache-Control", cacheHeader);
        httpResponse.setHeader("ETag", response.etag());
        httpResponse.setHeader("X-ThreadTrace-Cache", response.servedFromFallbackCache() ? "FALLBACK" : "ORIGIN");

        if (ifNoneMatch != null && ifNoneMatch.equals(response.etag())) {
            httpResponse.setStatus(HttpStatus.NOT_MODIFIED.value());
            return null;
        }

        model.addAttribute("passport", response.view());
        model.addAttribute("servedFromFallbackCache", response.servedFromFallbackCache());
        return "passport";
    }
}
