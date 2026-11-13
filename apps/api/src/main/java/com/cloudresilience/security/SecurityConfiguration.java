package com.cloudresilience.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.session.web.http.SessionRepositoryFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() { return new ChangeSessionIdAuthenticationStrategy(); }

    @Bean
    FilterRegistrationBean<RedisAvailabilityFilter> redisAvailabilityFilter(ObjectMapper mapper) {
        var registration = new FilterRegistrationBean<>(new RedisAvailabilityFilter(mapper));
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER - 1);
        return registration;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${app.security.cookie.secure:false}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(secure).httpOnly(true).sameSite("Lax").path("/"));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
            CsrfTokenRepository csrfTokens, ObjectMapper mapper) throws Exception {
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        http
            .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(csrfHandler))
            .securityContext(context -> context.securityContextRepository(contextRepository).requireExplicitSave(true))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .sessionFixation(fixation -> fixation.changeSessionId()))
            .requestCache(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                    .invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID")
                    .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(org.springframework.http.HttpStatus.NO_CONTENT)))
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, exception) -> ApiErrorWriter.write(request, response, mapper,
                            401, "Unauthorized", "Authentication required"))
                    .accessDeniedHandler((request, response, exception) -> ApiErrorWriter.write(request, response, mapper,
                            403, "Forbidden", "Access denied")))
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/info", "/api/v1/auth/csrf").permitAll()
                     .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                     .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/*", "/api/v1/products/*/availability", "/api/v1/categories", "/api/v1/categories/*").permitAll()
                    .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                     .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                     .requestMatchers("/api/v1/checkout", "/api/v1/orders", "/api/v1/orders/**").hasRole("CUSTOMER")
                     .requestMatchers("/api/v1/notifications", "/api/v1/notifications/**").hasRole("CUSTOMER")
                     .requestMatchers("/api/v1/cart", "/api/v1/cart/**").hasRole("CUSTOMER")
                    .requestMatchers("/api/v1/auth/me").authenticated()
                    .requestMatchers("/api/v1/**").denyAll()
                    .anyRequest().permitAll());
        return http.build();
    }
}
