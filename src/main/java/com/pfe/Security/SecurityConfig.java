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

import java.time.Instant;

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
                // CORS preflight: browsers send OPTIONS without credentials.
                // The CorsFilter (enabled by http.cors above) already handles
                // and short-circuits preflight, but permit it explicitly so it
                // can never fall through to .anyRequest().authenticated().
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                // Container/orchestrator health checks (no JWT available), and
                // the Prometheus scrape endpoint (no JWT available either — a
                // ServiceMonitor has no way to carry one). Both are safe to
                // expose without auth here because neither is ever reachable
                // outside the cluster: the backend Service is ClusterIP-only
                // and the Ingress never routes to it (see KUBERNETES.md
                // "Kubernetes monitoring resources"). management.endpoints.web
                // .exposure.include=health,prometheus,info keeps every other
                // Actuator endpoint unmapped regardless of this permitAll.
                // Found via a real k3d deployment with monitoring enabled
                // (Phase 4): Prometheus's own target list showed backend as
                // "down" with a 401 until /actuator/prometheus was added here.
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                .requestMatchers("/api/v1/auth/**", "/api/v1/public/**").permitAll()
                .requestMatchers("/api/v1/tech-invitations/validate").permitAll()
                .requestMatchers("/api/v1/files/*/view").permitAll()
                .requestMatchers("/api/v1/tech-invitations/stt/**").hasRole("STT")
                .requestMatchers("/api/v1/tech-invitations/zm/**").hasRole("ZM")
                // Both the native STOMP endpoint and its SockJS fallback need to
                // be reachable pre-auth at the HTTP layer: STOMP's own auth
                // happens later, inside the CONNECT frame (see
                // WebSocketAuthChannelInterceptor), not via the initial HTTP
                // request. SockJS additionally opens an unauthenticated
                // GET .../info handshake before any transport (WS or XHR) is
                // established at all — found missing here via Phase 2 Docker
                // Compose integration testing: /ws-notifications-sockjs/info
                // 401'd because only /ws-notifications/** was ever added,
                // never the SockJS path (a different literal prefix, not a
                // sub-path of the first pattern).
                .requestMatchers("/ws-notifications/**", "/ws-notifications-sockjs/**").permitAll()
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
