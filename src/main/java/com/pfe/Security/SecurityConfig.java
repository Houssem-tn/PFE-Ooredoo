package com.pfe.Security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pfe.exception.ApiError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthFilter jwtAuthFilter,
                                           ObjectMapper mapper) throws Exception {

        http.cors(Customizer.withDefaults());
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.httpBasic(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/tech-invitations/validate").permitAll()
                .requestMatchers("/api/v1/files/*/view").permitAll()
                .requestMatchers("/api/v1/tech-invitations/stt/**").hasRole("STT")
                .requestMatchers("/api/v1/tech-invitations/zm/**").hasRole("ZM")
                .requestMatchers("/ws-notifications/**").permitAll()
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/error",
                        "/api/v1/ocr/**"
                ).permitAll()
                // Stock management endpoints
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/stock/equipment/**").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/stock/equipment/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/stock/equipment/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/stock/equipment/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/stock/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/stock/zm/**").hasRole("ZM")
                .requestMatchers("/api/v1/stock/stt/**").hasRole("STT")
                .requestMatchers("/api/v1/stock/tech/**").hasRole("TECH")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/stock/dpms").hasRole("ZM")
                .requestMatchers("/api/v1/stock/dpms/**").authenticated()
                // Coverage intelligence endpoints — fine-grained checks happen inside services
                .requestMatchers("/api/v1/coverage-layers/**").hasAnyRole("ADMIN", "ZM")
                .requestMatchers("/api/v1/coverage-analysis/**").hasAnyRole("ADMIN", "ZM")
                .anyRequest().authenticated()
        );

        http.exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(res.getOutputStream(),
                            new ApiError(Instant.now(), 401, "UNAUTHORIZED",
                                    "Authentication required", req.getRequestURI()));
                })
                .accessDeniedHandler((req, res, e) -> {
                    res.setStatus(403);
                    res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    mapper.writeValue(res.getOutputStream(),
                            new ApiError(Instant.now(), 403, "FORBIDDEN",
                                    "Access denied", req.getRequestURI()));
                })
        );

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


}
