package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.DenunciaCriadaDto;
import com.couto.chefe_api.Dtos.DenunciaRequestDto;
import com.couto.chefe_api.Excepitons.ConflitoException;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.Security.ModeracaoRegistry;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Denuncia;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.StatusDenuncia;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.DenunciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DenunciaService {

    /** Denunciantes distintos com denúncia aberta a partir dos quais o chef some da busca. */
    public static final long LIMITE_OCULTACAO = 3;

    private final AgendamentoRepository agendamentoRepository;
    private final DenunciaRepository denunciaRepository;
    private final NotificacaoService notificacaoService;
    private final ModeracaoRegistry moderacaoRegistry;
    private final Clock clock;

    @Transactional
    public DenunciaCriadaDto criar(DenunciaRequestDto dto, UUID clienteId) {
        // só quem foi atendido denuncia; qualquer outro caso parece "não existe"
        Agendamento agendamento = agendamentoRepository.findByIdAndUsuario_Id(dto.agendamentoId(), clienteId)
                .filter(a -> a.getStatus() == StatusAgendamento.CONFIRMADO)
                .filter(a -> a.getDataHoraInicial() != null && a.getDataHoraInicial().isBefore(LocalDateTime.now(clock)))
                .orElseThrow(() -> new RecursoNaoEncontradoException("agendamento não encontrado"));
        ChefeModel chefe = agendamento.getChefes().stream()
                .filter(c -> c.getId().equals(dto.chefeId()))
                .findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("agendamento não encontrado"));

        if (denunciaRepository.existsByCliente_IdAndChefe_IdAndStatus(clienteId, chefe.getId(), StatusDenuncia.ABERTA)) {
            throw new ConflitoException("já existe uma denúncia sua em análise para este chef");
        }

        Denuncia denuncia = new Denuncia();
        denuncia.setChefe(chefe);
        denuncia.setCliente(agendamento.getUsuario());
        denuncia.setAgendamento(agendamento);
        denuncia.setMotivo(dto.motivo());
        denuncia.setCriadaEm(LocalDateTime.now(clock));
        denuncia.setDescricao(dto.descricao().strip());
        Denuncia salva = denunciaRepository.save(denuncia);

        notificarModeradores(EventoNotificacao.DENUNCIA_CRIADA,
                "Nova denúncia contra o chef " + chefe.getNome() + " (" + dto.motivo() + ")", String.valueOf(salva.getId()));

        // avisa só na transição para o limite, não a cada denúncia depois dele
        long denunciantes = denunciaRepository.countDistinctClienteByChefeIdAndStatus(chefe.getId(), StatusDenuncia.ABERTA);
        if (denunciantes == LIMITE_OCULTACAO) {
            String referencia = chefe.getId().toString();
            notificacaoService.notificar(chefe.getId(), TipoUsuario.CHEFE, EventoNotificacao.CHEF_OCULTO,
                    "Seu perfil foi ocultado da busca enquanto denúncias são analisadas", referencia);
            notificarModeradores(EventoNotificacao.CHEF_OCULTO,
                    "Chef " + chefe.getNome() + " foi ocultado automaticamente (3 denúncias)", referencia);
        }
        return new DenunciaCriadaDto(salva.getId(), salva.getStatus());
    }

    private void notificarModeradores(EventoNotificacao evento, String mensagem, String referenciaId) {
        moderacaoRegistry.ids().forEach(id ->
                notificacaoService.notificar(id, TipoUsuario.MODERADOR, evento, mensagem, referenciaId));
    }
}
