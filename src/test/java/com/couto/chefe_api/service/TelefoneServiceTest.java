package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.exception.PedidoDeCodigoRepetidoException;
import com.couto.chefe_api.exception.SmsIndisponivelException;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.TelefoneBanidoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O intervalo de 60 s entre pedidos de SMS, com relógio controlado. */
class TelefoneServiceTest {

    static final String TELEFONE = "+5511987654321";

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    private final TelefoneBanidoRepository banidoRepository = mock(TelefoneBanidoRepository.class);
    private final VerificadorTelefone verificador = mock(VerificadorTelefone.class);
    private final RelogioAjustavel relogio = new RelogioAjustavel(Instant.parse("2026-10-23T12:00:00Z"));
    private final TelefoneService service = new TelefoneService(usuarioRepository, chefeRepository, banidoRepository,
            verificador, new HashTelefone("teste"), mock(TransactionTemplate.class), relogio);

    private final UsuarioAutenticado ana = new UsuarioAutenticado(UUID.randomUUID(), "ana@x.com", TipoUsuario.CLIENTE);

    @BeforeEach
    void contasExistem() {
        when(usuarioRepository.existsById(any())).thenReturn(true);
        ChefeModel chefe = new ChefeModel();
        when(chefeRepository.findById(any())).thenReturn(Optional.of(chefe));
    }

    @Test
    void segundoPedidoSoDepoisDe60Segundos() {
        service.pedirCodigo(ana, TELEFONE);

        relogio.avancar(Duration.ofSeconds(59));
        assertThatThrownBy(() -> service.pedirCodigo(ana, TELEFONE)).isInstanceOf(PedidoDeCodigoRepetidoException.class);

        relogio.avancar(Duration.ofSeconds(1));
        assertThatCode(() -> service.pedirCodigo(ana, TELEFONE)).doesNotThrowAnyException();
        verify(verificador, times(2)).enviarCodigo(TELEFONE);
    }

    @Test
    void smsQueNaoSaiuNaoContaParaOIntervalo() {
        doThrow(new SmsIndisponivelException(SmsIndisponivelException.AO_ENVIAR)).doNothing()
                .when(verificador).enviarCodigo(anyString());
        assertThatThrownBy(() -> service.pedirCodigo(ana, TELEFONE)).isInstanceOf(SmsIndisponivelException.class);

        // no mesmo segundo: o Twilio voltou e a pessoa tenta de novo sem esperar
        assertThatCode(() -> service.pedirCodigo(ana, TELEFONE)).doesNotThrowAnyException();
        verify(verificador, times(2)).enviarCodigo(TELEFONE);
    }

    @Test
    void intervaloEhPorConta() {
        UsuarioAutenticado bia = new UsuarioAutenticado(UUID.randomUUID(), "bia@x.com", TipoUsuario.CHEFE);
        service.pedirCodigo(ana, TELEFONE);

        assertThatCode(() -> service.pedirCodigo(bia, "+5511987654322")).doesNotThrowAnyException();
    }

    /** Relógio que o teste avança. */
    static final class RelogioAjustavel extends Clock {

        private Instant agora;

        RelogioAjustavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
