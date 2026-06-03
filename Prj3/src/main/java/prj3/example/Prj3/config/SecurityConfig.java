package prj3.example.Prj3.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Value("${cors.allowed.origins:http://localhost:3000,http://localhost:5000}")
    private List<String> allowedOrigins;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                
                .requestMatchers("/api/auth/**").permitAll()

                
                .requestMatchers("/api/face/**").permitAll()
                .requestMatchers("/api/attendance/face/health").permitAll()

                
                .requestMatchers("/api/ping", "/api/v2/ping",
                                  "/api/v2/attendance/ping").permitAll()

                
                .requestMatchers("/api/v2/attendance/record").permitAll()
                .requestMatchers("/api/v2/attendance/batch-offline").permitAll()
                .requestMatchers("/api/v2/attendance/remote-entry").permitAll()
                .requestMatchers("/api/v1/device/**").permitAll()

                
                .requestMatchers("/api/config/master-pin").permitAll()

                
                .requestMatchers("/api/config/ping").permitAll()

                
                .requestMatchers("/ws-attendance/**").permitAll()

                
                .requestMatchers("/api/admin/organizations/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/admin/users/**").hasAnyRole("SUPER_ADMIN", "ADMIN")

                
                .requestMatchers("/api/devices/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/api/v2/devices/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "VIEWER", "OPERATOR", "HR_MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/v2/devices/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                        "/api/v2/devices/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                        "/api/v2/devices/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers("/api/config/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/api/permissions/**", "/api/v2/permissions/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "VIEWER", "OPERATOR", "HR_MANAGER")
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/permissions/**", "/api/v2/permissions/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                        "/api/permissions/**", "/api/v2/permissions/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                        "/api/permissions/**", "/api/v2/permissions/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                
                .requestMatchers(org.springframework.http.HttpMethod.POST,
                        "/api/employees/**", "/api/v2/employees/**").hasAnyRole("HR_MANAGER","ADMIN","SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.PUT,
                        "/api/employees/**", "/api/v2/employees/**").hasAnyRole("HR_MANAGER","ADMIN","SUPER_ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.DELETE,
                        "/api/employees/**", "/api/v2/employees/**").hasAnyRole("HR_MANAGER","ADMIN","SUPER_ADMIN")

                
                .requestMatchers("/api/v2/attendance/remote-unlock/**").hasAnyRole("OPERATOR","ADMIN","SUPER_ADMIN")

                
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}