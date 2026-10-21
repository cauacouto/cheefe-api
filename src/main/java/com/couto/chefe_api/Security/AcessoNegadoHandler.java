package com.couto.chefe_api.Security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
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
}
