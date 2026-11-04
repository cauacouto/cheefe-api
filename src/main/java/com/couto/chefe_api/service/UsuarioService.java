package com.couto.chefe_api.service;

import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.UserModel;
import com.couto.chefe_api.dto.UserRequestDto;
import com.couto.chefe_api.dto.UserResponseDto;
import com.couto.chefe_api.exception.UsuarioException;
import com.couto.chefe_api.mapper.UsuarioMapper;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Conversa;
import com.couto.chefe_api.service.NotificacaoService;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.AvaliacaoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.DenunciaAvaliacaoRepository;
import com.couto.chefe_api.repository.ConversaRepository;
import com.couto.chefe_api.repository.DenunciaMensagemRepository;
import com.couto.chefe_api.repository.DenunciaRepository;
import com.couto.chefe_api.repository.MensagemRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final UsuarioMapper usuarioMapper;
    private final EnderecoRepository enderecoRepository;
    private final DenunciaRepository denunciaRepository;
    private final DenunciaMensagemRepository denunciaMensagemRepository;
    private final MensagemRepository mensagemRepository;
    private final ConversaRepository conversaRepository;
    private final NotificacaoService notificacaoService;
    private final com.couto.chefe_api.repository.BloqueioContaRepository bloqueioContaRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final DenunciaAvaliacaoRepository denunciaAvaliacaoRepository;
    private final ChefeRepository chefeRepository;

    /**
     * Substitui os dados e a lista de endereços do usuário.
     *
     * <p>A lista gerenciada pelo Hibernate é mantida (clear + addAll) em vez de
     * ser trocada por uma lista nova. Como {@code UserModel.endereco} ainda não
     * tem {@code orphanRemoval}, os endereços antigos são apagados aqui.</p>
     */
    @Transactional
    public UserResponseDto atualizar(UserRequestDto dto, UUID usuarioId) {
        UserModel user = repository.findById(usuarioId).orElseThrow(UsuarioException::new);

        List<EnderecoModel> novos = dto.endereco().stream()
                .map(usuarioMapper::toModel)
                .toList();
        novos.forEach(endereco -> {
            endereco.getTipoDeResidencia().validar(endereco);
            endereco.setUsuario(user);
        });

        user.setNome(dto.nome());
        user.setTelefone(dto.telefone());

        if (user.getEndereco() == null) {
            user.setEndereco(new ArrayList<>());
        }
        List<EnderecoModel> antigos = new ArrayList<>(user.getEndereco());
        user.getEndereco().clear();
        user.getEndereco().addAll(novos);
        enderecoRepository.deleteAll(antigos);

        return usuarioMapper.toDto(repository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponseDto buscar(UUID usuarioId) {
        UserModel user = repository.findById(usuarioId)
                .orElseThrow(UsuarioException::new);
        return usuarioMapper.toDto(user);
    }

    @Transactional
    public void deletar(UUID usuarioId) {
        UserModel user = repository.findById(usuarioId)
                .orElseThrow(UsuarioException::new);
        // os agendamentos primeiro, em ordem de id: uma rotina que grava notificação deles termina antes (ou, depois,
        // relê sob a trava e não encontra mais nada), então nenhuma notificação fica órfã
        agendamentoRepository.travarDoCliente(usuarioId);
        // antes de juntar ids: um envio que estava no meio entra na conta e é apagado junto
        List<Long> conversas = conversaRepository.travarDoCliente(usuarioId).stream().map(Conversa::getId).toList();
        // apagar a conta apaga os dados do cliente, inclusive as denúncias que ele fez (LGPD)
        apagarNotificacoes(usuarioId, conversas);
        // as notas que ele deu saem da média dos chefs; as avaliações vão na cascata dos agendamentos
        avaliacaoRepository.findByAutorIdAndAvaliadoTipo(usuarioId, TipoUsuario.CHEFE)
                .forEach(a -> chefeRepository.descontarAvaliacao(a.getAvaliadoId(), a.getNota()));
        bloqueioContaRepository.deleteByContaId(usuarioId);
        denunciaRepository.deleteByCliente_Id(usuarioId);
        // o chat do cliente vai junto (ordem das FKs): denúncias de mensagens, mensagens e conversas
        denunciaMensagemRepository.deleteByMensagem_Conversa_Cliente_Id(usuarioId);
        mensagemRepository.deleteByConversa_Cliente_Id(usuarioId);
        conversaRepository.deleteByCliente_Id(usuarioId);
        repository.delete(user);
    }

    /**
     * As do cliente e as dos outros que apontam para dados dele (e muitas vezes citam o nome dele):
     * pedidos de agendamento recebidos pelo chef, mensagens recebidas e denúncias que chegaram à moderação.
     * Filtra pelo evento porque referenciaId de tipos diferentes pode coincidir (agendamento 5 x conversa 5).
     */
    private void apagarNotificacoes(UUID clienteId, List<Long> conversas) {
        notificacaoService.apagarDoDestinatario(clienteId);
        apagarDasReferencias(EnumSet.of(EventoNotificacao.AGENDAMENTO_CRIADO, EventoNotificacao.AGENDAMENTO_EDITADO,
                EventoNotificacao.AGENDAMENTO_CONFIRMADO, EventoNotificacao.AGENDAMENTO_CANCELADO,
                EventoNotificacao.RESUMO_DO_SERVICO, EventoNotificacao.DETALHES_ALTERADOS,
                EventoNotificacao.DETALHES_CONFIRMADOS), agendamentoRepository.idsDoCliente(clienteId));
        // a referência do aviso de um ano é o próprio cliente (e não um agendamento)
        notificacaoService.apagarDasReferencias(EnumSet.of(EventoNotificacao.UM_ANO_DE_CLIENTE), List.of(clienteId.toString()));
        apagarDasReferencias(EnumSet.of(EventoNotificacao.MENSAGEM_RECEBIDA), conversas);
        apagarDasReferencias(EnumSet.of(EventoNotificacao.MENSAGEM_DENUNCIADA),
                denunciaMensagemRepository.idsDasDenunciasNasConversasDoCliente(clienteId));
        apagarDasReferencias(EnumSet.of(EventoNotificacao.DENUNCIA_CRIADA), denunciaRepository.idsDoCliente(clienteId));
        apagarDasReferencias(EnumSet.of(EventoNotificacao.AVALIACAO_RESPONDIDA),
                avaliacaoRepository.idsDosAgendamentosDoCliente(clienteId));
        apagarDasReferencias(EnumSet.of(EventoNotificacao.AVALIACAO_DENUNCIADA),
                denunciaAvaliacaoRepository.idsDosAgendamentosDoCliente(clienteId));
        // os bloqueios do cliente somem; os moderadores não ficam com aviso apontando para nada
        apagarDasReferencias(EnumSet.of(EventoNotificacao.CONTA_BLOQUEADA), bloqueioContaRepository.idsDaConta(clienteId));
    }

    private void apagarDasReferencias(Set<EventoNotificacao> eventos, List<Long> ids) {
        notificacaoService.apagarDasReferencias(eventos, ids.stream().map(String::valueOf).toList());
    }
}
