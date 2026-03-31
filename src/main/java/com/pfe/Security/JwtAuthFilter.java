package com.pfe.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        System.out.println("=== JWT FILTER ===");
        System.out.println("PATH = " + path);
        System.out.println("AUTH HEADER = " + header);

        if (header == null || !header.startsWith("Bearer ")) {
            System.out.println("NO BEARER TOKEN");
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        System.out.println("TOKEN FOUND");

        if (!jwtService.isValid(token)) {
            System.out.println("TOKEN INVALID");
            chain.doFilter(request, response);
            return;
        }

        String email = jwtService.extractSubject(token);
        System.out.println("TOKEN SUBJECT = " + email);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            System.out.println("USER LOADED = " + userDetails.getUsername());
            System.out.println("AUTHORITIES = " + userDetails.getAuthorities());

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            System.out.println("AUTH SET IN CONTEXT");
        }

        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws-notifications")
                || path.equals("/error");
    }



}