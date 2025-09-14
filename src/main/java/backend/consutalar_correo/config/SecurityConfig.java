package backend.consutalar_correo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Deshabilita CSRF para pruebas
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/credentials/**").permitAll() // Deja público este endpoint
                        .requestMatchers("/api/netflix/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .anyRequest().authenticated() // El resto pide autenticación
                );

        return http.build();
    }
}
