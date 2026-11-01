package com.couto.chefe_api.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telefone, e-mail, @ de rede social e links, para manter cliente e chef dentro do ChefHome. Roda sobre uma
 * cópia normalizada (disfarces como dígitos de largura total ou emoji de número viram texto simples) e mascara
 * o trecho correspondente no texto original. Separado do {@link FiltroConteudo}: palavrão é lista de palavras,
 * contato é padrão. Só detecta o dado: "me chama no zap" passa.
 */
@Component
public class DetectorContato {

    private static final String MASCARA = "***";
    private static final int MINIMO_DE_DIGITOS = 8;
    /** Fora de qualquer padrão: o trecho que "não é contato" não emenda com os dígitos ao lado. */
    private static final char BLOQUEIO = '\u0001';

    private static final String SEPARADOR = "[\\s.\\-/()]";
    /** Quatro dígitos depois: o que parecia data ou valor é só o começo de um número maior. */
    private static final String SEM_DIGITOS_DEPOIS = "(?!" + SEPARADOR + "{0,2}\\d{4})";

    /** Checados antes, nesta ordem: o horário sai primeiro para a data ao lado dele não emendar nos dígitos. */
    private static final List<Pattern> NAO_SAO_CONTATO = List.of(
            // horários: 20:30, 20h30, 20h
            Pattern.compile("(?<![\\d:])([01]?\\d|2[0-3]):[0-5]\\d(?![\\d:])"),
            Pattern.compile("(?<![\\p{L}\\d])([01]?\\d|2[0-3])h([0-5]\\d)?(?![\\p{L}\\d])"),
            // valores: R$ 1.500,00, 1500 reais
            Pattern.compile("r\\$\\s*\\d[\\d.]*(,\\d{1,2})?" + SEM_DIGITOS_DEPOIS),
            Pattern.compile("(?<!\\d" + SEPARADOR + "{0,2})\\d[\\d.]*(,\\d{1,2})?\\s*(reais|real)(?!\\p{L})"),
            // datas: d/m/aa, dd/mm/aaaa, dd-mm-aaaa
            Pattern.compile("(?<![\\d/])(0?[1-9]|[12]\\d|3[01])/(0?[1-9]|1[0-2])/(\\d{4}|\\d{2})(?![\\d/])" + SEM_DIGITOS_DEPOIS),
            Pattern.compile("(?<![\\d\\-])(0[1-9]|[12]\\d|3[01])-(0[1-9]|1[0-2])-\\d{4}(?![\\d\\-])" + SEM_DIGITOS_DEPOIS),
            // CEP: 00000-000 sozinho (o endereço do agendamento já é da plataforma)
            Pattern.compile("(?<!\\d" + SEPARADOR + "{0,2})\\d{5}-\\d{3}(?!" + SEPARADOR + "{0,2}\\d)"));

    private static final Pattern TELEFONE = Pattern.compile("\\+?\\(?\\d+(?:" + SEPARADOR + "{1,2}\\d+)*");

    private static final String PALAVRA_NUMERICA = "zero|um|uma|dois|duas|tres|quatro|cinco|seis|meia|sete|oito|nove";
    private static final Pattern PALAVRA = Pattern.compile("\\p{L}+");
    private static final Pattern NUMERO_POR_EXTENSO = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(?:\\d+|" + PALAVRA_NUMERICA + ")(?:[\\s.,;:\\-/()]{1,3}(?:\\d+|" + PALAVRA_NUMERICA + "))*"
                    + "(?![\\p{L}\\p{N}])");

    /** 3 a 30 caracteres; o ponto final da frase não faz parte do nome nem do link. */
    private static final String NOME_DE_USUARIO = "[a-z0-9._]{2,29}[a-z0-9_]";
    private static final String FIM_DE_LINK = "[^\\s.,;:!?)]";

    private static final List<Pattern> PADROES = List.of(
            // e-mail, e o disfarçado: "chef arroba gmail ponto com", "chef (at) gmail.com"
            Pattern.compile("[a-z0-9._%+\\-]+@[a-z0-9\\-]+(?:\\.[a-z0-9\\-]+)+"),
            Pattern.compile("[a-z0-9._\\-]+\\s*(?:arroba|\\(at\\)|\\[at\\])\\s*[a-z0-9\\-]+(?:\\s*(?:ponto|\\.)\\s*[a-z0-9\\-]+)+"),
            // @ de rede social, e o disfarçado: "arroba chefana"
            Pattern.compile("(?<![a-z0-9._%+\\-])@" + NOME_DE_USUARIO),
            Pattern.compile("(?<!\\p{L})arroba\\s+(?!de\\b)" + NOME_DE_USUARIO),
            // link ou domínio; os hosts conhecidos (wa.me, instagram.com, linktr.ee...) caem nos domínios
            Pattern.compile("(?:https?://|www\\.)\\S*" + FIM_DE_LINK),
            Pattern.compile("(?<![a-z0-9@._\\-])[a-z0-9\\-]+(?:\\.[a-z0-9\\-]+)*\\.(?:com\\.br|com|net|org|br|me|io|app|ee|link|bio)"
                    + "(?:/\\S*" + FIM_DE_LINK + ")?(?![a-z0-9\\-])"));

    public record Resultado(String texto, boolean contatoOculto) {
    }

    private record Trecho(int inicio, int fim) {
    }

    /** Devolve o texto com cada contato trocado por "***". */
    public Resultado mascarar(String texto) {
        if (texto == null || texto.isBlank()) {
            return new Resultado(texto, false);
        }
        List<Trecho> trechos = trechos(texto);
        if (trechos.isEmpty()) {
            return new Resultado(texto, false);
        }
        StringBuilder exibido = new StringBuilder();
        int posicao = 0;
        for (Trecho trecho : trechos) {
            exibido.append(texto, posicao, trecho.inicio());
            // uma ligatura pode virar vários caracteres na cópia: o texto exibido nunca cresce
            exibido.append(MASCARA, 0, Math.min(MASCARA.length(), trecho.fim() - trecho.inicio()));
            posicao = trecho.fim();
        }
        return new Resultado(exibido.append(texto, posicao, texto.length()).toString(), true);
    }

    /** true se o texto tem algum contato (usado nos pratos). */
    public boolean contem(String texto) {
        return texto != null && !texto.isBlank() && !trechos(texto).isEmpty();
    }

    /** Trechos de contato em posições do texto original, em ordem e sem sobreposição. */
    private List<Trecho> trechos(String texto) {
        CopiaNormalizada copia = CopiaNormalizada.de(texto);
        StringBuilder livre = new StringBuilder(copia.texto());
        for (Pattern padrao : NAO_SAO_CONTATO) {
            Matcher matcher = padrao.matcher(livre);
            while (matcher.find()) {
                for (int i = matcher.start(); i < matcher.end(); i++) {
                    livre.setCharAt(i, BLOQUEIO);
                }
            }
        }

        List<Trecho> achados = new ArrayList<>();
        Matcher telefone = TELEFONE.matcher(livre);
        while (telefone.find()) {
            if (digitos(telefone.group()) >= MINIMO_DE_DIGITOS) {
                achados.add(new Trecho(telefone.start(), telefone.end()));
            }
        }
        Matcher extenso = NUMERO_POR_EXTENSO.matcher(livre);
        while (extenso.find()) {
            long palavras = PALAVRA.matcher(extenso.group()).results().count();
            if (palavras > 0 && digitos(extenso.group()) + palavras >= MINIMO_DE_DIGITOS) {
                achados.add(new Trecho(extenso.start(), extenso.end()));
            }
        }
        for (Pattern padrao : PADROES) {
            Matcher matcher = padrao.matcher(livre);
            while (matcher.find()) {
                achados.add(new Trecho(matcher.start(), matcher.end()));
            }
        }
        return copia.noOriginal(unir(achados));
    }

    private static long digitos(String trecho) {
        return trecho.chars().filter(Character::isDigit).count();
    }

    private static List<Trecho> unir(List<Trecho> achados) {
        List<Trecho> ordenados = new ArrayList<>(achados);
        ordenados.sort(Comparator.comparingInt(Trecho::inicio));
        List<Trecho> unidos = new ArrayList<>();
        for (Trecho trecho : ordenados) {
            Trecho ultimo = unidos.isEmpty() ? null : unidos.get(unidos.size() - 1);
            if (ultimo != null && trecho.inicio() <= ultimo.fim()) {
                unidos.set(unidos.size() - 1, new Trecho(ultimo.inicio(), Math.max(ultimo.fim(), trecho.fim())));
            } else {
                unidos.add(trecho);
            }
        }
        return unidos;
    }

    /** O texto só para detectar, com a origem de cada caractere: um do original pode virar vários ou nenhum. */
    private record CopiaNormalizada(String texto, int[] inicios, int tamanhoOriginal) {

        static CopiaNormalizada de(String original) {
            StringBuilder normalizado = new StringBuilder();
            int[] inicios = new int[original.length() * 4 + 1];
            int i = 0;
            while (i < original.length()) {
                int caractere = original.codePointAt(i);
                int proximo = i + Character.charCount(caractere);
                for (char c : normalizar(caractere).toCharArray()) {
                    if (normalizado.length() == inicios.length) {
                        inicios = Arrays.copyOf(inicios, inicios.length * 2);
                    }
                    inicios[normalizado.length()] = i;
                    normalizado.append(c);
                }
                i = proximo;
            }
            return new CopiaNormalizada(normalizado.toString(), inicios, original.length());
        }

        /** NFKC (largura total, números circulados), sem acento, em minúsculas; marcas e invisíveis somem. */
        private static String normalizar(int caractere) {
            int tipo = Character.getType(caractere);
            // U+FE0F e U+20E3 do emoji de número são marcas: sobra o dígito
            if (tipo == Character.NON_SPACING_MARK || tipo == Character.ENCLOSING_MARK
                    || tipo == Character.COMBINING_SPACING_MARK || tipo == Character.FORMAT) {
                return "";
            }
            int digito = Character.digit(caractere, 10);
            if (digito >= 0 && caractere > 0x7F && tipo == Character.DECIMAL_DIGIT_NUMBER) {
                return String.valueOf(digito);
            }
            String compativel = Normalizer.normalize(Character.toString(caractere), Normalizer.Form.NFKC);
            StringBuilder semAcento = new StringBuilder();
            Normalizer.normalize(compativel, Normalizer.Form.NFD).codePoints()
                    .filter(c -> Character.getType(c) != Character.NON_SPACING_MARK)
                    .forEach(semAcento::appendCodePoint);
            return semAcento.toString().toLowerCase(Locale.ROOT);
        }

        List<Trecho> noOriginal(List<Trecho> trechos) {
            List<Trecho> original = new ArrayList<>();
            for (Trecho trecho : trechos) {
                // leva junto o que sumiu da cópia logo depois do trecho (marcas do emoji de número, por exemplo)
                int fim = trecho.fim() < texto.length() ? inicios[trecho.fim()] : tamanhoOriginal;
                original.add(new Trecho(inicios[trecho.inicio()], fim));
            }
            return original;
        }
    }
}
