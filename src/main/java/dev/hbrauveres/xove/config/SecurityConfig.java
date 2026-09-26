package dev.hbrauveres.xove.config;

import dev.hbrauveres.xove.auth.AdminAuthoritiesMapper;
import dev.hbrauveres.xove.auth.LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSuccessHandler loginSuccessHandler,
                                            AdminAuthoritiesMapper adminAuthoritiesMapper)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/health/**").permitAll()
                .requestMatchers("/api/oauth2/**", "/api/login/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())

            // Google login, with every URL under /api so Caddy routes it to this app
            .oauth2Login(oauth -> oauth
                .loginPage("/")
                .authorizationEndpoint(e -> e.baseUri("/api/oauth2/authorization"))
                .redirectionEndpoint(e -> e.baseUri("/api/login/oauth2/code/*"))
                .userInfoEndpoint(u -> u.userAuthoritiesMapper(adminAuthoritiesMapper))
                .successHandler(loginSuccessHandler)
                .failureHandler((request, response, ex) -> response.sendRedirect("/?error=login-failed")))

            // An API answers 401, it doesn't redirect to a login page
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))

            // CSRF for a single-page app: token in a readable XSRF-TOKEN cookie,
            // sent back by the frontend in the X-XSRF-TOKEN header
            .csrf(csrf -> csrf.spa());

        return http.build();
    }
}
