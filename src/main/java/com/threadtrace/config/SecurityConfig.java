package com.threadtrace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(SecurityProperties securityProperties, PasswordEncoder passwordEncoder) {
        Map<String, BrandScopedUserDetails> accounts = new ConcurrentHashMap<>();
        for (SecurityProperties.UserAccount account : securityProperties.getUsers()) {
            List<SimpleGrantedAuthority> authorities = account.getRoles().stream()
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            String encoded = account.getPassword().startsWith("{")
                    ? account.getPassword()
                    : passwordEncoder.encode(account.getPassword());
            accounts.put(account.getUsername(), new BrandScopedUserDetails(
                    account.getUsername(), encoded, authorities, account.getBrandCode()));
        }
        return username -> {
            BrandScopedUserDetails user = accounts.get(username);
            if (user == null) {
                throw new UsernameNotFoundException("User not found: " + username);
            }
            return new BrandScopedUserDetails(
                    user.getUsername(),
                    user.getPassword(),
                    user.getAuthorities(),
                    user.getBrandCode()
            );
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AuthenticationEntryPoint problemAuthEntryPoint = (request, response, authException) -> {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, authException.getMessage());
            pd.setTitle("Authentication Required");
            pd.setType(URI.create("https://threadtrace.local/problems/unauthorized"));
            pd.setInstance(URI.create(request.getRequestURI()));
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "Basic realm=\"ThreadTrace\"");
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            jsonMapper.writeValue(response.getWriter(), pd);
        };
        AccessDeniedHandler problemAccessDeniedHandler = (request, response, accessDeniedException) -> {
            ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, accessDeniedException.getMessage());
            pd.setTitle("Access Denied");
            pd.setType(URI.create("https://threadtrace.local/problems/access-denied"));
            pd.setInstance(URI.create(request.getRequestURI()));
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            jsonMapper.writeValue(response.getWriter(), pd);
        };

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/p/**", "/api/v1/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/**").hasAnyRole("ADMIN", "AUDITOR")
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(problemAuthEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemAuthEntryPoint)
                        .accessDeniedHandler(problemAccessDeniedHandler)
                );
        return http.build();
    }
}
