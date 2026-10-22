package com.couto.chefe_api.Security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.util.UrlPathHelper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 403 no mesmo formato RFC 9457 ({@code ProblemDetail}) que o {@code GlobalExceptionHandler} usa
 * nos demais erros. O 403 de papel errado nasce no {@code ExceptionTranslationFilter}, fora do MVC,
 * por isso o corpo é escrito aqui.
 */
public class AcessoNegadoHandler implements AccessDeniedHandler {

    static final String DETALHE = "acesso negado";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        // caminho sem o context-path (funciona com server.servlet.context-path configurado)
        if (UrlPathHelper.defaultInstance.getPathWithinApplication(request).startsWith("/moderacao")) {
            responderComoRotaInexistente(request, response);
            return;
        }
        HttpStatus status = HttpStatus.FORBIDDEN;
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", "about:blank");
        corpo.put("title", status.getReasonPhrase());
        corpo.put("status", status.value());
        corpo.put("detail", DETALHE);
        corpo.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON.writeValueAsString(corpo));
    }

    /**
     * A área de moderação não se revela: para quem não modera, a resposta é byte a byte a mesma
     * que o Spring MVC dá a uma rota inexistente (NoResourceFoundException).
     */
    private static void responderComoRotaInexistente(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String caminho = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("detail", "No static resource " + caminho.substring(1) + ".");
        corpo.put("instance", request.getRequestURI());
        corpo.put("status", HttpStatus.NOT_FOUND.value());
        corpo.put("title", HttpStatus.NOT_FOUND.getReasonPhrase());

        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(JSON.writeValueAsBytes(corpo));
    }
}
