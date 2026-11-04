package com.couto.chefe_api.service;

import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.NotaCliente;
import com.couto.chefe_api.domain.NotaClienteId;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.dto.ClienteDoChefDto;
import com.couto.chefe_api.dto.HistoricoClienteDto;
import com.couto.chefe_api.dto.NotaClienteDto;
import com.couto.chefe_api.enums.TipoRestricao;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.mapper.AgendamentoMapper;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.NotaClienteRepository;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Os clientes do chef: lista, histórico e nota privada. Tudo vale só para os agendamentos do próprio chef, e o cliente
 * só existe para quem o atendeu (ou tem um pedido dele que não recusou); para os demais é 404.
 */
@Service
@RequiredArgsConstructor
public class ClienteDoChefService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;
    static final int AGENDAMENTOS_NO_HISTORICO = 20;

    private final AgendamentoRepository agendamentoRepository;
    private final ChefeRepository chefeRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotaClienteRepository notaRepository;
    private final AvaliacaoService avaliacaoService;
    private final ModeracaoConteudoService moderacaoConteudo;
    private final AgendamentoMapper agendamentoMapper;
    private final Clock clock;

    /** A ordem é sempre a da consulta (agendamento mais recente primeiro); a vinda do cliente é ignorada. */
    @Transactional(readOnly = true)
    public Page<ClienteDoChefDto> listar(UsuarioAutenticado chef, Pageable pageable) {
        exigirChefAtivo(chef);
        PageRequest pagina = PageRequest.of(pageable.getPageNumber(),
                Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA)));
        return agendamentoRepository.clientesDoChef(chef.id(), LocalDateTime.now(clock), pagina)
                .map(c -> new ClienteDoChefDto(c.id(), ClienteDoChefDto.primeiroNome(c.nome()), c.imageUrl(), c.total(),
                        c.ultimo(), c.proximo()));
    }

    @Transactional(readOnly = true)
    public HistoricoClienteDto historico(UUID clienteId, UsuarioAutenticado chef) {
        // a mesma regra de acesso da reputação (chef ativo, cliente conhecido, senão 404)
        var reputacao = avaliacaoService.reputacaoDoCliente(clienteId, chef);
        UserModel cliente = clienteDe(clienteId);
        List<HistoricoClienteDto.Item> agendamentos = agendamentoRepository
                .historicoComOCliente(chef.id(), clienteId, PageRequest.of(0, AGENDAMENTOS_NO_HISTORICO)).stream()
                .map(a -> new HistoricoClienteDto.Item(a.getId(), a.getDataHoraInicial(), a.getStatus(),
                        agendamentoMapper.menuDoAgendamento(a), a.getQuantidadePessoas(), a.getOcasiao(),
                        // cancelado ou expirado: o chef fica com o registro, sem o que o cliente pediu
                        a.detalhesDoClienteVisiveisAoChef() ? agendamentoMapper.restricoes(a.getRestricoes()) : List.of()))
                .toList();
        List<HistoricoClienteDto.RestricaoFrequente> frequentes = agendamentoRepository
                .restricoesFrequentes(chef.id(), clienteId).stream()
                .map(l -> new HistoricoClienteDto.RestricaoFrequente((TipoRestricao) l[0], (Long) l[1]))
                .toList();
        NotaClienteDto nota = notaRepository.findById(new NotaClienteId(chef.id(), clienteId))
                .map(n -> new NotaClienteDto(n.getTexto(), n.getAtualizadaEm())).orElse(null);
        return new HistoricoClienteDto(
                new HistoricoClienteDto.Cliente(cliente.getId(), ClienteDoChefDto.primeiroNome(cliente.getNome()),
                        cliente.getImageUrl()),
                reputacao, frequentes, nota, agendamentos);
    }

    /**
     * Cria ou substitui a nota; texto nulo ou em branco apaga (vazio), sem checar o acesso. Para gravar: acesso
     * primeiro (404 não passa pelo filtro, que pode bloquear a conta), moderação depois, e só então a gravação.
     */
    @Transactional
    public Optional<NotaClienteDto> gravarNota(UUID clienteId, String texto, UsuarioAutenticado chef) {
        ChefeModel chefe = exigirChefAtivo(chef);
        NotaClienteId id = new NotaClienteId(chef.id(), clienteId);
        if (texto == null || texto.isBlank()) {
            // o chef sempre apaga a própria nota, mesmo sem acesso (pedido recusado): o 204 não revela nada
            notaRepository.findById(id).ifPresent(notaRepository::delete);
            return Optional.empty();
        }
        if (!agendamentoRepository.chefConheceOCliente(clienteId, chef.id())) {
            throw new RecursoNaoEncontradoException("cliente não encontrado");
        }
        UserModel cliente = clienteDe(clienteId);
        String original = texto.strip();
        String exibido = moderacaoConteudo.verificar(original, chef).texto();
        NotaCliente nota = notaRepository.findById(id).orElseGet(() -> {
            NotaCliente nova = new NotaCliente();
            nova.setId(id);
            nova.setChefe(chefe);
            nova.setCliente(cliente);
            return nova;
        });
        nota.setTexto(exibido);
        nota.setTextoOriginal(exibido.equals(original) ? null : original);
        nota.setAtualizadaEm(LocalDateTime.now(clock));
        notaRepository.save(nota);
        return Optional.of(new NotaClienteDto(nota.getTexto(), nota.getAtualizadaEm()));
    }

    private UserModel clienteDe(UUID clienteId) {
        return usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("cliente não encontrado"));
    }

    /** Chef apagado ou banido (ativo=false) não opera, mesmo com o token ainda válido. */
    private ChefeModel exigirChefAtivo(UsuarioAutenticado chef) {
        ChefeModel chefe = chefeRepository.findById(chef.id()).orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();
        return chefe;
    }
}
