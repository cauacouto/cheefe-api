package com.couto.chefe_api.Chefe;

import com.couto.chefe_api.domin.ChefeModel;
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

    public static Specification<ChefeModel> valorMaximoMneor(BigDecimal valorMaximo) {
        return (root, query, cb) ->
                valorMaximo == null ? null : cb.lessThanOrEqualTo(root.get("valorHora"), valorMaximo);
    }
}
