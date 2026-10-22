package com.couto.chefe_api.service;

import com.couto.chefe_api.enums.CategoriaConteudo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtro de conteúdo puro (sem banco): compara palavra a palavra, sem caixa, acentos e pontuação.
 * Ofensa vira "***" no texto exibido; ódio e ameaça são apenas detectados (quem chama decide o que
 * fazer). Prioridade: ódio, depois ameaça, depois ofensa.
 */
@Component
public class FiltroConteudo {

    public record Violacao(CategoriaConteudo categoria, String termo) {}

    public record ResultadoFiltro(String textoExibido, Optional<Violacao> violacao) {}

    private static final Pattern PALAVRA = Pattern.compile("[\\p{L}\\p{N}\\p{M}]+");
    private static final Pattern MARCAS = Pattern.compile("\\p{M}+");
    private static final List<CategoriaConteudo> PRIORIDADE =
            List.of(CategoriaConteudo.ODIO, CategoriaConteudo.AMEACA, CategoriaConteudo.OFENSA);

    private final Map<CategoriaConteudo, List<List<String>>> termos = new EnumMap<>(CategoriaConteudo.class);
    private final Map<CategoriaConteudo, List<String>> originais = new EnumMap<>(CategoriaConteudo.class);

    public FiltroConteudo(Map<CategoriaConteudo, List<String>> termos) {
        for (CategoriaConteudo c : CategoriaConteudo.values()) {
            List<String> lista = termos.getOrDefault(c, List.of());
            this.originais.put(c, List.copyOf(lista));
            List<List<String>> palavras = new ArrayList<>();
            for (String t : lista) {
                List<String> p = palavrasNormalizadas(t);
                if (!p.isEmpty()) palavras.add(p);
            }
            this.termos.put(c, palavras);
        }
    }

    /** Construtor de produção: lê ofensa/odio/ameaca (listas) do YAML. */
    @Autowired
    public FiltroConteudo(@Value("classpath:moderacao/termos.yml") Resource yml) {
        this(carregar(yml));
    }

    /** Termos configurados (como escritos no arquivo), por categoria. */
    Map<CategoriaConteudo, List<String>> termos() {
        return originais;
    }

    public ResultadoFiltro analisar(String texto) {
        if (texto == null || texto.isBlank()) return new ResultadoFiltro(texto, Optional.empty());

        List<int[]> posicoes = new ArrayList<>();
        List<String> palavras = new ArrayList<>();
        Matcher m = PALAVRA.matcher(texto);
        while (m.find()) {
            posicoes.add(new int[]{m.start(), m.end()});
            palavras.add(normalizar(m.group()));
        }

        for (CategoriaConteudo c : PRIORIDADE) {
            if (c == CategoriaConteudo.OFENSA) continue;
            for (List<String> termo : termos.get(c)) {
                if (indiceDe(palavras, termo, 0) >= 0) {
                    return new ResultadoFiltro(texto, Optional.of(new Violacao(c, String.join(" ", termo))));
                }
            }
        }

        // ofensa: cada trecho casado vira um "***" (palavras já cobertas por outro termo são puladas)
        boolean[] coberta = new boolean[palavras.size()];
        List<int[]> trechos = new ArrayList<>();
        List<List<String>> ofensas = new ArrayList<>(termos.get(CategoriaConteudo.OFENSA));
        ofensas.sort((x, y) -> Integer.compare(y.size(), x.size())); // o termo mais longo vence
        for (List<String> termo : ofensas) {
            int de = 0;
            int i;
            while ((i = indiceDe(palavras, termo, de)) >= 0) {
                de = i + 1;
                boolean livre = true;
                for (int k = i; k < i + termo.size(); k++) livre &= !coberta[k];
                if (!livre) continue;
                for (int k = i; k < i + termo.size(); k++) coberta[k] = true;
                trechos.add(new int[]{posicoes.get(i)[0], posicoes.get(i + termo.size() - 1)[1]});
                de = i + termo.size();
            }
        }
        trechos.sort((x, y) -> Integer.compare(y[0], x[0]));
        StringBuilder sb = new StringBuilder(texto);
        for (int[] t : trechos) sb.replace(t[0], t[1], "***");
        return new ResultadoFiltro(sb.toString(), Optional.empty());
    }

    private static int indiceDe(List<String> palavras, List<String> termo, int de) {
        for (int i = de; i + termo.size() <= palavras.size(); i++) {
            if (palavras.subList(i, i + termo.size()).equals(termo)) return i;
        }
        return -1;
    }

    private static List<String> palavrasNormalizadas(String texto) {
        List<String> r = new ArrayList<>();
        Matcher m = PALAVRA.matcher(texto == null ? "" : texto);
        while (m.find()) r.add(normalizar(m.group()));
        return r;
    }

    private static String normalizar(String s) {
        return MARCAS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Map<CategoriaConteudo, List<String>> carregar(Resource yml) {
        try (InputStream in = yml.getInputStream()) {
            Object raiz = new Yaml().load(in);
            Map<String, Object> mapa = raiz instanceof Map<?, ?> r ? (Map<String, Object>) r : Map.of();
            Map<CategoriaConteudo, List<String>> out = new EnumMap<>(CategoriaConteudo.class);
            out.put(CategoriaConteudo.OFENSA, lista(mapa.get("ofensa")));
            out.put(CategoriaConteudo.ODIO, lista(mapa.get("odio")));
            out.put(CategoriaConteudo.AMEACA, lista(mapa.get("ameaca")));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + yml, e);
        }
    }

    private static List<String> lista(Object o) {
        return o instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }
}
