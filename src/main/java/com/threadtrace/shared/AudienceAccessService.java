package com.threadtrace.shared;

import com.threadtrace.config.BrandScopedUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AudienceAccessService {

    public Audience resolveAudience() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Audience.PUBLIC;
        }
        Set<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (roles.contains("ROLE_AUDITOR") || roles.contains("ROLE_ADMIN") || roles.contains("ROLE_OPERATOR")) {
            return Audience.AUDITOR;
        }
        if (roles.contains("ROLE_BRAND")) {
            return Audience.BRAND;
        }
        return Audience.PUBLIC;
    }

    public Optional<String> currentBrandScope() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof BrandScopedUserDetails brandUser) {
            return Optional.ofNullable(brandUser.getBrandCode());
        }
        return Optional.empty();
    }

    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }
        return auth.getName();
    }

    public void assertBrandAccess(String resourceBrandCode) {
        Audience audience = resolveAudience();
        if (audience == Audience.AUDITOR) {
            return;
        }
        if (audience == Audience.BRAND) {
            String principalBrand = currentBrandScope().orElse("");
            if (!principalBrand.equalsIgnoreCase(resourceBrandCode)) {
                throw new DomainExceptions.BrandAccessDeniedException(
                        "Brand principal '" + principalBrand + "' cannot access resources of brand '" + resourceBrandCode + "'");
            }
            return;
        }
        throw new DomainExceptions.BrandAccessDeniedException("Public audience cannot access brand-scoped resources");
    }
}
