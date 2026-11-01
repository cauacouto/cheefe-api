package com.couto.chefe_api.repository;

import com.couto.chefe_api.service.DenunciaService;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Denuncia;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.domain.Reputacao;
import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.OcasiaoMenu;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
                cb.isNotEmpty(root.<Set<Especialidades>>get("especialidades")),
                cb.isNotNull(root.get("senioridade")),
                cb.isNotNull(root.get("anosDeExperiencia")),
                cb.notEqual(cb.trim(cb.coalesce(root.get("idiomas"), "")), ""),
                cb.isNotNull(root.get("cidade")),
                cb.isNotNull(root.get("estado")),
                temMenuAtivo(root, query, cb, null, null, null));
    }

    /**
     * Pelo menos um menu ativo com a ocasião e a faixa de preço por pessoa pedidas, todos os filtros no MESMO menu
     * (um menu de aniversário caro e um de empresa barato não formam "aniversário barato"). Filtro nulo = sem filtro.
     */
    public static Specification<ChefeModel> comMenuAtivo(OcasiaoMenu ocasiao, BigDecimal minimo, BigDecimal maximo) {
        return (root, query, cb) -> ocasiao == null && minimo == null && maximo == null
                ? null : temMenuAtivo(root, query, cb, ocasiao, minimo, maximo);
    }

    private static Predicate temMenuAtivo(Root<ChefeModel> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                          OcasiaoMenu ocasiao, BigDecimal minimo, BigDecimal maximo) {
        Subquery<Integer> existe = query.subquery(Integer.class);
        Root<Menu> menu = existe.from(Menu.class);
        List<Predicate> regras = new ArrayList<>();
        regras.add(cb.equal(menu.get("chefe"), root));
        regras.add(cb.isTrue(menu.get("ativo")));
        if (ocasiao != null) regras.add(cb.isMember(ocasiao, menu.<Set<OcasiaoMenu>>get("ocasioes")));
        if (minimo != null) regras.add(cb.greaterThanOrEqualTo(menu.<BigDecimal>get("precoPorPessoa"), minimo));
        if (maximo != null) regras.add(cb.lessThanOrEqualTo(menu.<BigDecimal>get("precoPorPessoa"), maximo));
        return cb.exists(existe.select(cb.literal(1)).where(regras.toArray(Predicate[]::new)));
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
        return (root, query, cb) -> especialidade == null ? null
                : cb.isMember(especialidade, root.<Set<Especialidades>>get("especialidades"));
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

    /**
     * Menor preço por pessoa entre os menus ativos primeiro (ou maior, em {@code DESC}), depois as outras ordens
     * pedidas e o id (página estável). A ordem fica aqui porque o preço não é coluna do chef.
     */
    public static Specification<ChefeModel> ordenadoPorPreco(Sort.Direction direcao, Sort demais) {
        return (root, query, cb) -> {
            if (Long.class.equals(query.getResultType())) {
                return null; // contagem da página: sem ordem
            }
            Subquery<BigDecimal> menor = query.subquery(BigDecimal.class);
            Root<Menu> menu = menor.from(Menu.class);
            menor.select(cb.min(menu.<BigDecimal>get("precoPorPessoa")))
                    .where(cb.equal(menu.get("chefe"), root), cb.isTrue(menu.get("ativo")));
            List<Order> ordens = new ArrayList<>();
            ordens.add(direcao.isAscending() ? cb.asc(menor, Nulls.LAST) : cb.desc(menor, Nulls.LAST));
            ordens.addAll(QueryUtils.toOrders(demais, root, cb));
            ordens.add(cb.asc(root.get("id")));
            query.orderBy(ordens);
            return null;
        };
    }
}
