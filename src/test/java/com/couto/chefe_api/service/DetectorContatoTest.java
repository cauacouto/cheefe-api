package com.couto.chefe_api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** O que é contato (spec contato protegido), o que parece mas não é, e como a máscara fica no texto. */
class DetectorContatoTest {

    private final DetectorContato detector = new DetectorContato();

    @ParameterizedTest
    @ValueSource(strings = {
            // telefone e números longos
            "21 98888-7777", "(24) 9 9999 8888", "+5521988887777", "219.888.877.77", "21988887777",
            "123.456.789-09", "12345678",
            // número por extenso ou misturado
            "nove nove oito oito sete sete seis seis", "21 nove 9888 7777", "dois um nove oito oito oito sete sete",
            // e-mail
            "chef@gmail.com", "chef arroba gmail ponto com", "chef (at) gmail.com", "chef [at] gmail.com",
            "chef arroba gmail.com",
            // @ de rede social
            "@chef.ana", "arroba chefana",
            // link ou domínio
            "wa.me/5521988887777", "instagram.com/chefana", "linktr.ee/ana", "meusite.com.br", "t.me/ana",
            "https://qualquercoisa.xyz/a", "www.qualquercoisa.xyz", "api.whatsapp.com/send",
            // disfarces
            "２１９８８８８７７７７", "①②③④⑤⑥⑦⑧", "2️⃣1️⃣9️⃣8️⃣8️⃣8️⃣8️⃣7️⃣7️⃣", "9​8​8​8​8​7​7​7",
            "ＣＨＥＦ＠ＧＭＡＩＬ．ＣＯＭ", "NOVE NOVE OITO OITO SETE SETE SEIS SEIS", "três três três três três três três três"
    })
    void contato(String texto) {
        assertThat(detector.contem(texto)).as(texto).isTrue();
        assertThat(detector.contem("me chama em " + texto + " amanhã")).as(texto).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "5/1/26", "25/12/2026", "25-12-2026", "dia 25/12/2026 às 20:30", "25/12/2026 20h30", "25/12/2026 8 pessoas",
            "20:30", "20h30", "20h", "R$ 1.500,00", "r$ 12.000.000", "1500 reais", "1.500,00 reais",
            "01001-000", "para 10 pessoas", "menu de 4 tempos", "um jantar para dois", "uma ou duas entradas",
            "meu email é chef", "gmail", "obrigado. me liga", "peso de uma arroba de carne", "1234567",
            "Moqueca capixaba com banana-da-terra", "***", "a chef *** chegou"
    })
    void naoEhContato(String texto) {
        assertThat(detector.contem(texto)).as(texto).isFalse();
        assertThat(detector.mascarar(texto).texto()).isEqualTo(texto);
        assertThat(detector.mascarar(texto).contatoOculto()).isFalse();
    }

    @Test
    void foraDoContextoDeDataOuValorOsDigitosContinuamSendoTelefone() {
        // parecem data, CEP ou valor, mas emendam com mais dígitos ou têm dia e mês impossíveis
        assertThat(detector.contem("21 98888-777")).isTrue();
        assertThat(detector.contem("21/98/8887")).isTrue();
        assertThat(detector.contem("21-11-9888-7777")).isTrue();
        assertThat(detector.contem("21 98888 7777 reais")).isTrue();
    }

    @Test
    void mascaraSoOTrechoEPreservaORestoDoOriginal() {
        DetectorContato.Resultado resultado = detector.mascarar("Olá, São Paulo! Meu nº é (21) 98888-7777, combinado às 20h.");

        assertThat(resultado.texto()).isEqualTo("Olá, São Paulo! Meu nº é ***, combinado às 20h.");
        assertThat(resultado.contatoOculto()).isTrue();
    }

    @Test
    void variosContatosNaMesmaFrase() {
        assertThat(detector.mascarar("zap 21988887777, mail ana@x.com.br e insta @chef.ana. Até!").texto())
                .isEqualTo("zap ***, mail *** e insta ***. Até!");
    }

    @Test
    void pontuacaoDaFraseFicaForaDaMascara() {
        assertThat(detector.mascarar("veja instagram.com/chefana. Ou https://x.com/ana, ok?").texto())
                .isEqualTo("veja ***. Ou ***, ok?");
    }

    @Test
    void posicoesCertasComAcentoEmojiEDisfarceAntes() {
        assertThat(detector.mascarar("Ação 😀 ２１９８８８８７７７７ fim").texto()).isEqualTo("Ação 😀 *** fim");
        assertThat(detector.mascarar("Café 2️⃣1️⃣9️⃣8️⃣8️⃣8️⃣8️⃣7️⃣7️⃣ ok").texto()).isEqualTo("Café *** ok");
    }

    @Test
    void textoExibidoNuncaFicaMaiorQueOEnviado() {
        // ligatura: um caractere vira três na cópia normalizada, então "@" + ela tem só dois no original
        String texto = "oi @ﬃ tchau";

        assertThat(detector.mascarar(texto).texto().length()).isLessThanOrEqualTo(texto.length());
        for (String t : new String[]{"@abc", "a.io", "a@b.co", "12345678", "①②③④⑤⑥⑦⑧"}) {
            assertThat(detector.mascarar(t).texto().length()).as(t).isLessThanOrEqualTo(t.length());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void textoNuloOuEmBrancoVoltaComoVeio(String texto) {
        DetectorContato.Resultado resultado = detector.mascarar(texto);

        assertThat(resultado.texto()).isEqualTo(texto);
        assertThat(resultado.contatoOculto()).isFalse();
        assertThat(detector.contem(texto)).isFalse();
    }
}
