package com.couto.chefe_api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quem modera vem de {@code moderacao.emails} (variável MODERACAO_EMAILS), não do banco: não existe
 * conta privilegiada armazenada que possa vazar. O id é derivado do e-mail, então é estável entre logins.
 */
@Component
public class ModeracaoRegistry {

    private final Set<String> emails;

    public ModeracaoRegistry(@Value("${moderacao.emails:}") String emails) {
        this.emails = Arrays.stream(emails.split(","))
                .map(ModeracaoRegistry::normalizar)
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean ehModerador(String email) {
        return email != null && emails.contains(normalizar(email));
    }

    public UUID idDe(String email) {
        return UUID.nameUUIDFromBytes(normalizar(email).getBytes(StandardCharsets.UTF_8));
    }

    /** Sessão de moderação só vale enquanto o e-mail continua na configuração. */
    public boolean reconhece(UsuarioAutenticado usuario) {
        return ehModerador(usuario.email()) && idDe(usuario.email()).equals(usuario.id());
    }

    public List<UUID> ids() {
        return emails.stream().map(this::idDe).toList();
    }

    private static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
