package com.couto.chefe_api.dto;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Reputacao;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Perfil do próprio chef, com o que falta preencher para aparecer na busca. */
public record ChefePerfilDto(
        UUID id,
        String nome,
        String nacionalidade,
        Set<Especialidades> especialidades,
        Integer anosDeExperiencia,
        Senioridade senioridade,
        boolean disponivel,
        String idiomas,
        /** Menor preço por pessoa entre os menus ativos; nulo sem menu ativo. */
        BigDecimal precoAPartirDe,
        String imageUrl,
        boolean perfilCompleto,
        List<String> camposPendentes,
        boolean suspenso,
        boolean ocultoPorDenuncias,
        BigDecimal notaMedia,
        int totalAvaliacoes,
        boolean telefoneVerificado,
        String cidade,
        String estado,
        int folgaMinutos
) {

    /**
     * O menu ativo mora noutra tabela: quem monta o perfil informa se existe e o resultado entra no fim de
     * {@code camposPendentes}. {@code perfilCompleto} sai da lista final (mesma regra de ChefeRepository.VISIVEL).
     */
    public static ChefePerfilDto de(ChefeModel chefe, boolean ocultoPorDenuncias, boolean temMenuAtivo,
                                    BigDecimal precoAPartirDe) {
        List<String> pendentes = new ArrayList<>(chefe.camposPendentes());
        if (!temMenuAtivo) pendentes.add("menu");
        // só informação: a verificação de telefone está parada e não é exigida (ver README)
        boolean telefone = chefe.getTelefoneVerificadoEm() != null;
        return new ChefePerfilDto(chefe.getId(), chefe.getNome(), chefe.getNacionalidade(), Set.copyOf(chefe.getEspecialidades()),
                chefe.getAnosDeExperiencia(), chefe.getSenioridade(), chefe.isDisponivel(), chefe.getIdiomas(),
                precoAPartirDe, chefe.getImageUrl(), pendentes.isEmpty(), pendentes,
                chefe.isSuspenso(), ocultoPorDenuncias,
                Reputacao.media(chefe.getSomaNotas(), chefe.getTotalAvaliacoes()), chefe.getTotalAvaliacoes(), telefone,
                chefe.getCidade(), chefe.getEstado(), chefe.getFolgaMinutos());
    }
}
