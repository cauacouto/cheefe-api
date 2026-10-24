package com.couto.chefe_api.repository;

import com.couto.chefe_api.service.DenunciaService;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.domain.Reputacao;
import com.couto.chefe_api.enums.Especialidades;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    public static Specification<ChefeModel> naoBloqueado() {
        return (root, query, cb) -> cb.isNull(root.get("bloqueadoEm"));
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
                cb.isNotNull(root.get("valorHora")),
                cb.isNotNull(root.get("cidade")),
                cb.isNotNull(root.get("estado")));
    }

    /** Pela cidade de origem já normalizada ({@link com.couto.chefe_api.domain.Cidade#normalizar}); nula = sem filtro. */
    public static Specification<ChefeModel> naCidade(String cidadeBusca) {
        return (root, query, cb) -> cidadeBusca == null ? null : cb.equal(root.get("cidadeBusca"), cidadeBusca);
    }

    /** UF em maiúsculas; nula = sem filtro. */
    public static Specification<ChefeModel> noEstado(String uf) {
        return (root, query, cb) -> uf == null ? null : cb.equal(root.get("estado"), uf);
    }

    public static Specification<ChefeModel> daEspecialidade(Especialidades especialidade) {
        return (root, query, cb) -> especialidade == null ? null : cb.equal(root.get("especialidade"), especialidade);
    }

    /**
     * Maior média primeiro e "Novo" (menos de {@link Reputacao#MINIMO_PARA_MEDIA} avaliações) por último, depois
     * as outras ordens pedidas e o id (página estável). A ordem fica aqui porque a média não é coluna.
     */
    public static Specification<ChefeModel> ordenadoPorNotaMedia(Sort demais) {
        return (root, query, cb) -> {
            if (Long.class.equals(query.getResultType())) {
                return null; // contagem da página: sem ordem
            }
            Expression<Integer> total = root.get("totalAvaliacoes");
            Expression<Number> media = cb.<Number>selectCase()
                    .when(cb.ge(total, Reputacao.MINIMO_PARA_MEDIA),
                            cb.quot(cb.prod(root.<Integer>get("somaNotas"), 1.0), total));
            List<Order> ordens = new ArrayList<>();
            ordens.add(cb.desc(media, Nulls.LAST));
            ordens.addAll(QueryUtils.toOrders(demais, root, cb));
            ordens.add(cb.asc(root.get("id")));
            query.orderBy(ordens);
            return null;
        };
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
