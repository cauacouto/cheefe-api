package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Reputacao;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Perfil do próprio chef, com o que falta preencher para aparecer na busca. */
public record ChefePerfilDto(
        UUID id,
        String nome,
        String nacionalidade,
        Especialidades especialidade,
        Integer anosDeExperiencia,
        Senioridade senioridade,
        boolean disponivel,
        String idiomas,
        BigDecimal valorHora,
        String imageUrl,
        boolean perfilCompleto,
        List<String> camposPendentes,
        boolean suspenso,
        boolean ocultoPorDenuncias,
        BigDecimal notaMedia,
        int totalAvaliacoes,
        boolean telefoneVerificado
) {

    /** Pendência que não é campo do PATCH: sem telefone verificado o chef também não aparece na busca. */
    static final String TELEFONE_PENDENTE = "telefoneVerificado";

    public static ChefePerfilDto de(ChefeModel chefe, boolean ocultoPorDenuncias) {
        boolean telefone = chefe.getTelefoneVerificadoEm() != null;
        List<String> pendentes = new ArrayList<>(chefe.camposPendentes());
        if (!telefone) {
            pendentes.add(TELEFONE_PENDENTE);
        }
        return new ChefePerfilDto(chefe.getId(), chefe.getNome(), chefe.getNacionalidade(), chefe.getEspecialidade(),
                chefe.getAnosDeExperiencia(), chefe.getSenioridade(), chefe.isDisponivel(), chefe.getIdiomas(),
                chefe.getValorHora(), chefe.getImageUrl(), pendentes.isEmpty(), pendentes,
                chefe.isSuspenso(), ocultoPorDenuncias,
                Reputacao.media(chefe.getSomaNotas(), chefe.getTotalAvaliacoes()), chefe.getTotalAvaliacoes(), telefone);
    }
}
