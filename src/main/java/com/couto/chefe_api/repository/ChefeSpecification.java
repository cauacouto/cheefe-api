package com.couto.chefe_api.repository;

import com.couto.chefe_api.service.DenunciaService;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.enums.StatusDenuncia;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ChefeSpecification {

    private ChefeSpecification() {
    }

    public static Specification<ChefeModel> ativo() {
        return (root, query, cb) -> cb.isTrue(root.get("ativo"));
    }

    public static Specification<ChefeModel> disponivel() {
        return (root, query, cb) -> cb.isTrue(root.get("disponivel"));
    }

    public static Specification<ChefeModel> naoSuspenso() {
        return (root, query, cb) -> cb.isFalse(root.get("suspenso"));
    }

    /** Menos de {@link DenunciaService#LIMITE_OCULTACAO} clientes distintos com denúncia aberta. */
    public static Specification<ChefeModel> semDenunciasEmExcesso() {
        return (root, query, cb) -> {
            Subquery<Long> denunciantes = query.subquery(Long.class);
            Root<Denuncia> denuncia = denunciantes.from(Denuncia.class);
            denunciantes.select(cb.countDistinct(denuncia.get("cliente").get("id")))
                    .where(cb.equal(denuncia.get("chefe"), root),
                            cb.equal(denuncia.get("status"), StatusDenuncia.ABERTA));
            return cb.lessThan(denunciantes, DenunciaService.LIMITE_OCULTACAO);
        };
    }

    /** Mesma regra de {@link ChefeModel#isPerfilCompleto()}. */
    public static Specification<ChefeModel> perfilCompleto() {
        return (root, query, cb) -> cb.and(
                cb.notEqual(cb.trim(cb.coalesce(root.get("nacionalidade"), "")), ""),
                cb.isNotNull(root.get("especialidade")),
                cb.isNotNull(root.get("senioridade")),
                cb.isNotNull(root.get("anosDeExperiencia")),
                cb.notEqual(cb.trim(cb.coalesce(root.get("idiomas"), "")), ""),
                cb.isNotNull(root.get("valorHora")));
    }

    public static Specification<ChefeModel> valorMinimoMaior(BigDecimal valorMinimo) {
        return (root, query, cb) ->
                valorMinimo == null ? null : cb.greaterThanOrEqualTo(root.get("valorHora"), valorMinimo);
    }

    public static Specification<ChefeModel> valorMaximoMenor(BigDecimal valorMaximo) {
        return (root, query, cb) ->
                valorMaximo == null ? null : cb.lessThanOrEqualTo(root.get("valorHora"), valorMaximo);
    }
}
