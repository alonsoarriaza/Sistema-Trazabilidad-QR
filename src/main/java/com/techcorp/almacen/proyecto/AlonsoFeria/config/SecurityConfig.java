package com.techcorp.almacen.proyecto.AlonsoFeria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Desactivar CSRF para simplificar las llamadas API desde el taller
            .cors(cors -> {}) // Permitir CORS
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // Todo es libre para que el frontend maneje las rutas por rol
            );

        return http.build();
    }
}




