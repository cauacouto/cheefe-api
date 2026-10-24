package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.dto.TelefoneVerificadoDto;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.CodigoInvalidoException;
import com.couto.chefe_api.exception.PedidoDeCodigoRepetidoException;
import com.couto.chefe_api.exception.TelefoneBanidoException;
import com.couto.chefe_api.exception.TelefoneEmUsoException;
import com.couto.chefe_api.exception.UsuarioException;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.TelefoneBanidoRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Verificação do telefone por SMS: um telefone por conta (cliente ou chef), nunca o de uma conta banida.
 * As chamadas ao Twilio ficam fora de transação (nenhuma conexão do banco presa esperando a rede).
 */
@Service
@RequiredArgsConstructor
public class TelefoneService {

    static final Duration INTERVALO_ENTRE_PEDIDOS = Duration.ofSeconds(60);
    /** Acima disso o mapa de pedidos é varrido e perde as entradas vencidas (a memória não cresce sem fim). */
    static final int LIMPAR_ACIMA_DE = 10_000;
    private static final Pattern FORMATO_CODIGO = Pattern.compile("\\d{4,10}");

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final TelefoneBanidoRepository telefoneBanidoRepository;
    private final VerificadorTelefone verificador;
    private final HashTelefone hashTelefone;
    private final TransactionTemplate transacao;
    private final Clock clock;

    /** Último SMS pedido por conta: limite simples em memória, por instância (o Twilio também limita). */
    private final Map<UUID, Instant> ultimoPedido = new ConcurrentHashMap<>();

    /** Pede o SMS. Só um pedido aceito conta para o intervalo: formato, banido ou telefone de outra conta não. */
    public void pedirCodigo(UsuarioAutenticado conta, String telefone) {
        String e164 = TelefoneBrasil.normalizar(telefone);
        exigirContaAtiva(conta);
        exigirDisponivel(conta, e164);
        Instant reservado = reservarPedido(conta.id());
        try {
            verificador.enviarCodigo(e164);
        } catch (RuntimeException e) {
            // o SMS não saiu: a pessoa pode tentar de novo sem esperar o intervalo
            ultimoPedido.remove(conta.id(), reservado);
            throw e;
        }
    }

    /**
     * Confere o código e grava o telefone na conta (substitui o anterior). As checagens de banido e de outra
     * conta rodam de novo na gravação: entre o pedido e a verificação outra conta pode ter ficado com o número.
     */
    public TelefoneVerificadoDto verificar(UsuarioAutenticado conta, String telefone, String codigo) {
        String e164 = TelefoneBrasil.normalizar(telefone);
        exigirContaAtiva(conta);
        exigirDisponivel(conta, e164);
        if (codigo == null || !FORMATO_CODIGO.matcher(codigo).matches() || !verificador.conferir(e164, codigo)) {
            throw new CodigoInvalidoException();
        }
        try {
            return transacao.execute(status -> gravar(conta, e164));
        } catch (DataIntegrityViolationException corrida) {
            // índice único da tabela: outra conta do mesmo tipo gravou o número no mesmo instante
            throw new TelefoneEmUsoException();
        }
    }

    private TelefoneVerificadoDto gravar(UsuarioAutenticado conta, String e164) {
        exigirDisponivel(conta, e164);
        LocalDateTime agora = LocalDateTime.now(clock);
        switch (conta.tipo()) {
            case CLIENTE -> {
                UserModel cliente = usuarioRepository.findById(conta.id()).orElseThrow(UsuarioException::new);
                cliente.setTelefoneVerificado(e164);
                cliente.setTelefoneVerificadoEm(agora);
                usuarioRepository.saveAndFlush(cliente);
            }
            case CHEFE -> {
                ChefeModel chefe = chefeRepository.findById(conta.id()).orElseThrow(ChefeNaoEncontradoException::new);
                chefe.exigirAtivo();
                chefe.setTelefoneVerificado(e164);
                chefe.setTelefoneVerificadoEm(agora);
                chefeRepository.saveAndFlush(chefe);
            }
            case MODERADOR -> throw new AccessDeniedException("moderação não verifica telefone");
        }
        return new TelefoneVerificadoDto(e164, agora);
    }

    /** Chef apagado ou banido não verifica telefone (a rota já barra moderador). */
    private void exigirContaAtiva(UsuarioAutenticado conta) {
        switch (conta.tipo()) {
            case CLIENTE -> {
                if (!usuarioRepository.existsById(conta.id())) {
                    throw new UsuarioException();
                }
            }
            case CHEFE -> chefeRepository.findById(conta.id()).orElseThrow(ChefeNaoEncontradoException::new).exigirAtivo();
            case MODERADOR -> throw new AccessDeniedException("moderação não verifica telefone");
        }
    }

    /**
     * Banido primeiro: o número de uma conta banida costuma continuar nela, e a resposta certa é "não pode ser
     * usado". Entre clientes e chefs a unicidade é só esta checagem (o índice único vale dentro de cada tabela):
     * duas contas de tipos diferentes verificando o mesmo número no mesmo instante é a corrida aceita.
     */
    private void exigirDisponivel(UsuarioAutenticado conta, String e164) {
        if (telefoneBanidoRepository.existsById(hashTelefone.de(e164))) {
            throw new TelefoneBanidoException();
        }
        if (usuarioRepository.existsByTelefoneVerificadoAndIdNot(e164, conta.id())
                || chefeRepository.existsByTelefoneVerificadoAndIdNot(e164, conta.id())) {
            throw new TelefoneEmUsoException();
        }
    }

    /** Reserva o envio de forma atômica: dois pedidos simultâneos da mesma conta mandam um SMS só. */
    private Instant reservarPedido(UUID contaId) {
        Instant agora = clock.instant();
        Instant[] reservado = new Instant[1];
        ultimoPedido.compute(contaId, (id, anterior) -> {
            if (anterior != null && anterior.plus(INTERVALO_ENTRE_PEDIDOS).isAfter(agora)) {
                return anterior;
            }
            reservado[0] = agora;
            return agora;
        });
        if (reservado[0] == null) {
            throw new PedidoDeCodigoRepetidoException();
        }
        if (ultimoPedido.size() > LIMPAR_ACIMA_DE) {
            ultimoPedido.values().removeIf(quando -> !quando.plus(INTERVALO_ENTRE_PEDIDOS).isAfter(agora));
        }
        return reservado[0];
    }
}
