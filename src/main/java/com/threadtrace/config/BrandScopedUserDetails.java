package com.threadtrace.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class BrandScopedUserDetails extends User {

    private final String brandCode;

    public BrandScopedUserDetails(
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            String brandCode) {
        super(username, password, authorities);
        this.brandCode = brandCode;
    }

    public String getBrandCode() {
        return brandCode;
    }
}
