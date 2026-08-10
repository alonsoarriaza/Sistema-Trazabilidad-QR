package com.coanda.almacen.proyecto.AlonsoFeria.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class ApiTokenFilter extends OncePerRequestFilter {

    private static final Set<String> VALID_TOKENS = Set.of(
            "token-tecnico-1111",
            "token-comercial-coanda2026",
            "token-supervisor-1234"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod();

        // Permitir todas las peticiones de lectura (GET, OPTIONS, HEAD) y recursos estáticos
        if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Proteger operaciones de escritura/modificación (POST, PUT, DELETE, PATCH)
        String token = request.getHeader("X-Auth-Token");

        if (token != null && VALID_TOKENS.contains(token)) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Acceso denegado: Token de seguridad no autorizado o ausente.\"}");
        }
    }
}
