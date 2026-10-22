package com.couto.chefe_api.Security;

import com.couto.chefe_api.Security.bff.CookieOAuth2AuthorizationRequestRepository;
import com.couto.chefe_api.Security.bff.GoogleLoginFailureHandler;
import com.couto.chefe_api.Security.bff.GoogleLoginSuccessHandler;
import com.couto.chefe_api.Security.bff.NaoArmazenarAuthorizedClientRepository;
import com.couto.chefe_api.enums.TipoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration  {

    private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
    private static final String CLIENTE = TipoUsuario.CLIENTE.name();
    private static final String CHEFE = TipoUsuario.CHEFE.name();
    private static final String MODERADOR = TipoUsuario.MODERADOR.name();

    private final SecurityFilter securityFilter;
    private final CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final GoogleLoginSuccessHandler successHandler;
    private final GoogleLoginFailureHandler failureHandler;
    private final NaoArmazenarAuthorizedClientRepository authorizedClientRepository;


    @Bean
    public SecurityFilterChain securityFilterChain (HttpSecurity http,
                                                    ClientRegistrationRepository clientRegistrationRepository) throws Exception{
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/auth/me").authenticated()
                        .requestMatchers("/auth/**", "/oauth2/**", "/login/oauth2/**", "/error", "/enderecos/cep/**").permitAll()
                        .requestMatchers("/favicon.ico").permitAll()
                        // papel por rota; o dono do recurso é checado nos serviços
                        .requestMatchers("/moderacao/**").hasRole(MODERADOR)
                        .requestMatchers("/usuario/**").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.POST, "/denuncias").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.POST, "/agendamentos").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.PUT, "/agendamentos/*").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.GET, "/agendamentos/meus").hasRole(CLIENTE)
                        .requestMatchers(HttpMethod.GET, "/chefe/me").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.PATCH, "/chefe/me", "/chefe/me/inativar", "/chefe/me/ativar").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.DELETE, "/chefe/me").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.PUT, "/chefe/me/foto").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.POST, "/pratos").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.PUT, "/pratos/*").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.GET, "/pratos/meus").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.GET, "/agendamentos/recebidos").hasRole(CHEFE)
                        .requestMatchers(HttpMethod.PATCH, "/agendamentos/*/confirmar").hasRole(CHEFE)
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(authorizationRequestRepository)
                                .authorizationRequestResolver(authorizationRequestResolver(clientRegistrationRepository)))
                        .authorizedClientRepository(authorizedClientRepository)
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(new AcessoNegadoHandler()))
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, AUTHORIZATION_BASE_URI);
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "http://localhost:5173",
                "http://localhost:5174"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
