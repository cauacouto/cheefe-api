package com.couto.chefe_api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.couto.chefe_api.enums.TipoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final AuthCookies authCookies;
    private final ModeracaoRegistry moderacaoRegistry;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        authCookies.ler(request, AuthCookies.ACCESS_TOKEN)
                .flatMap(tokenService::validarToken)
                // tirar alguém de MODERACAO_EMAILS derruba a sessão na hora, não só quando o token expira
                .filter(usuario -> usuario.tipo() != TipoUsuario.MODERADOR || moderacaoRegistry.reconhece(usuario))
                .ifPresent(usuario -> {
                    var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });

        filterChain.doFilter(request, response);
    }
}
