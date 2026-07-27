package com.couto.chefe_api.Security;

import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.User.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

  private  final TokenService tokenService;

  private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        var token = this.recoverToken(request);

        if (token != null && !token.isEmpty()){
            var email = tokenService.validarToken(token);
            var usuario = usuarioRepository.findByEmail(email).orElseThrow(UsuarioException::new);

            var authentication = new UsernamePasswordAuthenticationToken(usuario,null, List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);

        }
     filterChain.doFilter(request,response);
    }

    private String recoverToken(HttpServletRequest request){
    var authHeader = request.getHeader("authorization");
    if (authHeader == null) return null;
    return  authHeader.replace("Bearer ","");
    }
}
