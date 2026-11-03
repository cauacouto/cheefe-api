package com.couto.chefe_api.service;

import com.couto.chefe_api.exception.ForaDaCidadeException;

import com.couto.chefe_api.service.eventos.AgendamentoExpiradoEvent;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import com.couto.chefe_api.security.UsuarioAutenticado;
import com.couto.chefe_api.repository.AgendamentoHistoricoRepository;
import com.couto.chefe_api.enums.MotivoRecusa;
import com.couto.chefe_api.enums.MotivoCancelamento;
import com.couto.chefe_api.dto.HistoricoAgendamentoDto;
import com.couto.chefe_api.domain.AgendamentoHistorico;
import com.couto.chefe_api.service.eventos.ChefSaiuDoAgendamentoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCanceladoEvent;
import com.couto.chefe_api.enums.MotivoAgendamento;
import java.time.Clock;
import java.time.Duration;
import com.couto.chefe_api.enums.TipoUsuario;
import com.couto.chefe_api.enums.AcaoAgendamento;
import com.couto.chefe_api.dto.RecusaRequestDto;
import com.couto.chefe_api.dto.CancelamentoRequestDto;
import com.couto.chefe_api.dto.DetalhesAgendamentoDto;
import com.couto.chefe_api.exception.DetalhesForaDoPrazoException;
import com.couto.chefe_api.service.eventos.DetalhesDoAgendamentoEvent;
import com.couto.chefe_api.exception.HorarioIndisponivelException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import com.couto.chefe_api.dto.AgendamentoResponseDto;
import com.couto.chefe_api.dto.AgendamentoRequestDto;
import com.couto.chefe_api.dto.AtualizarAgendamentoRequestDto;
import com.couto.chefe_api.dto.ListaAgendamentoChefeDto;
import com.couto.chefe_api.dto.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.exception.AgendamentoException;
import com.couto.chefe_api.exception.AgendamentoInvalidoException;
import com.couto.chefe_api.exception.AgendamentoNaoConfirmavelException;
import com.couto.chefe_api.exception.AgendamentoNaoEditavelException;
import com.couto.chefe_api.exception.AgendamentoSemMenuException;
import com.couto.chefe_api.exception.ConvidadosForaDoLimiteException;
import com.couto.chefe_api.exception.EnderecoException;
import com.couto.chefe_api.exception.MenuIndisponivelException;
import com.couto.chefe_api.exception.MenuNaoEncontradoException;
import com.couto.chefe_api.exception.TrocaDeChefException;
import com.couto.chefe_api.exception.UsuarioException;
import com.couto.chefe_api.mapper.AgendamentoMapper;
import com.couto.chefe_api.repository.UsuarioRepository;
import com.couto.chefe_api.domain.Agendamento;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.EnderecoModel;
import com.couto.chefe_api.domain.Menu;
import com.couto.chefe_api.domain.RestricaoAgendamento;
import com.couto.chefe_api.dto.RestricaoDto;
import com.couto.chefe_api.repository.MenuRepository;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.enums.EventoNotificacao;
import com.couto.chefe_api.repository.AgendamentoRepository;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.EnderecoRepository;
import com.couto.chefe_api.service.eventos.AgendamentoConfirmadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoCriadoEvent;
import com.couto.chefe_api.service.eventos.AgendamentoEditadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgendamentoService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final UsuarioRepository usuarioRepository;
    private final ChefeRepository chefeRepository;
    private final AgendamentoMapper agendamentoMapper;
    private final EnderecoRepository enderecoRepository;
    private final AgendamentoRepository agendamentoRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MenuRepository menuRepository;
    private final ModeracaoConteudoService moderacaoConteudo;
    private final HistoricoAgendamentoService historico;
    private final AgendamentoHistoricoRepository historicoRepository;
    private final AgendaService agendaService;
    private final Clock clock;
    private final NotificacaoService notificacaoService;

    @Transactional
    public AgendamentoResponseDto criarAgendamento(AgendamentoRequestDto dto, UsuarioAutenticado cliente) {
        UUID usuarioId = cliente.id();
        // antes de qualquer trava ou gravação: o bloqueio por ódio é commitado em transação própria
        Observacoes observacoes = verificarObservacoes(dto.getObservacoes(), cliente);
        var user = usuarioRepository.findById(usuarioId).orElseThrow(UsuarioException::new);

        Menu menu = menuRepository.findById(dto.getMenuId()).orElseThrow(MenuNaoEncontradoException::new);
        exigirReservavel(menu, dto.getQuantidadePessoas());
        Set<RestricaoAgendamento> restricoes = restricoesDe(dto.getRestricoes(), dto.getQuantidadePessoas());
        EnderecoModel endereco = buscarEndereco(dto.getEnderecoId(), usuarioId);

        ChefeModel chefe = menu.getChefe();
        Agendamento agendamento = new Agendamento();
        agendamento.setUsuario(user);
        agendamento.setEndereco(endereco);
        agendamento.setChefe(chefe);
        agendamento.setDataHoraInicial(dto.getDataHoraInicial());
        agendamento.setCriadoEm(agora());
        agendamento.aplicarMenu(menu, dto.getQuantidadePessoas());
        agendamento.setOcasiao(dto.getOcasiao());
        agendamento.getRestricoes().addAll(restricoes);
        observacoes.aplicarEm(agendamento);
        exigirMesmaCidade(chefe, endereco);
        exigirHorarioLivre(agendamento);
        agendaService.exigirDisponivel(chefe, agendamento.getDataHoraInicial(), agendamento.getDataHoraFinal());
        Agendamento salvo = agendamentoRepository.save(agendamento);
        historico.registrar(salvo, AcaoAgendamento.CRIADO, usuarioId, TipoUsuario.CLIENTE, null, null);
        eventPublisher.publishEvent(new AgendamentoCriadoEvent(salvo.getId()));
        return agendamentoMapper.toDto(salvo);
    }

    @Transactional(readOnly = true)
    public Page<ListaAgendamentoChefeDto> listarAgendamentosChefe(UUID chefeId, Pageable pageable) {
        return agendamentoRepository.findRecebidos(chefeId, pagina(pageable)).map(agendamentoMapper::toChefeDto);
    }

    @Transactional(readOnly = true)
    public Page<ListaAgendamentoUsuarioDto> listarAgendamentosUsuario(UUID usuarioId, Pageable pageable) {
        return agendamentoRepository.findByUsuario_IdOrderByDataHoraInicialDescIdDesc(usuarioId, pagina(pageable))
                .map(agendamentoMapper::toUsuarioDto);
    }

    /** A ordem é sempre a do repositório (data mais distante primeiro); a vinda do cliente é ignorada. */
    private static PageRequest pagina(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA)));
    }

    /**
     * Atualização parcial: só os campos não nulos do DTO são aplicados. Trocar o menu (do mesmo chef) ou a
     * quantidade (valor diferente do atual; mandar o mesmo não conta) recarrega o menu e recalcula a cópia, o fim e o valor, com as validações da criação; trocar
     * só o horário mantém a duração da cópia. Cidade, horário e agenda são sempre revalidados.
     */
    @Transactional
    public AgendamentoResponseDto atualizarAgendamento(AtualizarAgendamentoRequestDto dto, Long agendamentoId,
                                                       UsuarioAutenticado cliente) {
        UUID usuarioId = cliente.id();
        // o dono vem antes da moderação: quem não é dono recebe 404 e não é bloqueado. Só um exists, sem carregar a
        // entidade: carregá-la antes de travarPorId faria a trava devolver uma instância já lida, possivelmente velha
        if (!agendamentoRepository.existsByIdAndUsuario_Id(agendamentoId, usuarioId)) {
            throw new AgendamentoException();
        }
        // antes de travar o agendamento (ver criarAgendamento)
        Observacoes observacoes = dto.observacoes() == null ? null : verificarObservacoes(dto.observacoes(), cliente);
        // agendamento de outro cliente é tratado como inexistente
        Agendamento agenda = agendamentoRepository.travarPorId(agendamentoId)
                .filter(a -> a.getUsuario() != null && a.getUsuario().getId().equals(usuarioId))
                .orElseThrow(AgendamentoException::new);

        if (agenda.getStatus() != StatusAgendamento.PENDENTE) {
            throw new AgendamentoNaoEditavelException(agenda.getStatus());
        }
        agenda.exigirEditavel(agora());
        if (agenda.getMenu() == null) {
            throw new AgendamentoSemMenuException();
        }

        if (dto.enderecoId() != null) {
            agenda.setEndereco(buscarEndereco(dto.enderecoId(), usuarioId));
        }
        if (dto.dataHoraInicial() != null) {
            agenda.mudarInicio(dto.dataHoraInicial());
        }
        // só recopia quando algo mudou: o formulário inteiro volta igual e não pode repreciar nem dar 409 por menu pausado
        boolean trocouMenu = dto.menuId() != null && !dto.menuId().equals(agenda.getMenu().getId());
        boolean trocouQuantidade = dto.quantidadePessoas() != null
                && !dto.quantidadePessoas().equals(agenda.getQuantidadePessoas());
        if (trocouMenu || trocouQuantidade) {
            Menu menu = dto.menuId() != null
                    ? menuRepository.findById(dto.menuId()).orElseThrow(MenuNaoEncontradoException::new)
                    : agenda.getMenu();
            if (!menu.getChefe().getId().equals(agenda.getChefe().getId())) {
                throw new TrocaDeChefException();
            }
            int pessoas = dto.quantidadePessoas() != null ? dto.quantidadePessoas() : agenda.getQuantidadePessoas();
            exigirReservavel(menu, pessoas);
            agenda.aplicarMenu(menu, pessoas);
        }
        if (dto.restricoes() != null) {
            agenda.getRestricoes().clear();
            agenda.getRestricoes().addAll(restricoesDe(dto.restricoes(), agenda.getQuantidadePessoas()));
        } else {
            // a quantidade pode ter diminuído abaixo de uma restrição já gravada
            validarRestricoes(agenda.getRestricoes(), agenda.getQuantidadePessoas());
        }
        if (dto.ocasiao() != null) {
            agenda.setOcasiao(dto.ocasiao());
        }
        if (observacoes != null) {
            observacoes.aplicarEm(agenda);
        }

        exigirMesmaCidade(agenda.getChefe(), agenda.getEndereco());
        exigirHorarioLivre(agenda);
        agendaService.exigirDisponivel(agenda.getChefe(), agenda.getDataHoraInicial(), agenda.getDataHoraFinal());

        Agendamento salvo = agendamentoRepository.save(agenda);
        historico.registrar(salvo, AcaoAgendamento.EDITADO, usuarioId, TipoUsuario.CLIENTE, null, null);
        eventPublisher.publishEvent(new AgendamentoEditadoEvent(salvo.getId()));
        return agendamentoMapper.toDto(salvo);
    }

    /** Substitui restrições e observações de um confirmado, até 48h antes, e avisa o chef. */
    @Transactional
    public AgendamentoResponseDto ajustarDetalhes(Long id, UsuarioAutenticado cliente, DetalhesAgendamentoDto dto) {
        UUID usuarioId = cliente.id();
        // dono primeiro (404 sem moderar), depois o filtro, e só então a trava: ver atualizarAgendamento
        exigirDono(id, usuarioId);
        Observacoes observacoes = verificarObservacoes(dto.observacoes(), cliente);
        Agendamento agenda = detalhesEditaveis(id, usuarioId);

        // sem a lista, o campo vale como vazio
        agenda.getRestricoes().clear();
        agenda.getRestricoes().addAll(restricoesDe(dto.restricoes(), agenda.getQuantidadePessoas()));
        observacoes.aplicarEm(agenda);
        agenda.setDetalhesConfirmadosEm(agora());

        Agendamento salvo = agendamentoRepository.save(agenda);
        historico.registrar(salvo, AcaoAgendamento.DETALHES_ALTERADOS, usuarioId, TipoUsuario.CLIENTE, null, null);
        eventPublisher.publishEvent(new DetalhesDoAgendamentoEvent(salvo.getId(), true));
        return agendamentoMapper.toDto(salvo);
    }

    /** O cliente diz que os detalhes estão certos; repetir não grava nem avisa de novo. */
    @Transactional
    public AgendamentoResponseDto confirmarDetalhes(Long id, UsuarioAutenticado cliente) {
        UUID usuarioId = cliente.id();
        exigirDono(id, usuarioId);
        Agendamento agenda = detalhesEditaveis(id, usuarioId);

        if (agenda.getDetalhesConfirmadosEm() == null) {
            agenda.setDetalhesConfirmadosEm(agora());
            agendamentoRepository.save(agenda);
            historico.registrar(agenda, AcaoAgendamento.DETALHES_CONFIRMADOS, usuarioId, TipoUsuario.CLIENTE, null, null);
            eventPublisher.publishEvent(new DetalhesDoAgendamentoEvent(agenda.getId(), false));
        }
        return agendamentoMapper.toDto(agenda);
    }

    /** Só um exists, sem carregar a entidade (ver atualizarAgendamento); de outro cliente é tratado como inexistente. */
    private void exigirDono(Long id, UUID usuarioId) {
        if (!agendamentoRepository.existsByIdAndUsuario_Id(id, usuarioId)) {
            throw new AgendamentoException();
        }
    }

    /** O agendamento com trava, desde que ainda esteja confirmado e a mais de 48h do início (senão 409). */
    private Agendamento detalhesEditaveis(Long id, UUID usuarioId) {
        Agendamento agenda = agendamentoRepository.travarPorId(id)
                .filter(a -> a.getUsuario() != null && a.getUsuario().getId().equals(usuarioId))
                .orElseThrow(AgendamentoException::new);
        if (!agenda.detalhesEditaveis(agora())) {
            throw new DetalhesForaDoPrazoException();
        }
        return agenda;
    }

    @Transactional
    public void confirmarAgendamento(Long idAgendamento, UUID chefeId) {
        // só o chef do agendamento o enxerga; para os demais ele não existe
        Agendamento agendamento = doChef(idAgendamento, chefeId);
        if (agendamento.getStatus() != StatusAgendamento.PENDENTE) {
            throw new AgendamentoNaoConfirmavelException(agendamento.getStatus());
        }
        // trava o chef (agendamento primeiro, depois chef) e só então lê a conta dele: ainda não carregada, vem do banco já sob a trava
        chefeRepository.travar(List.of(chefeId));
        agendamento.getChefe().exigirAtivo();
        agendamento.confirmar(agora());
        // o chef pode ter mudado de cidade depois do pedido
        exigirMesmaCidade(agendamento.getChefe(), agendamento.getEndereco());
        exigirHorarioLivre(agendamento);
        // a agenda do chef (como o horário livre); depois da trava, para não confirmar dentro de um bloqueio recém-criado
        agendaService.exigirDisponivel(agendamento.getChefe(),
                agendamento.getDataHoraInicial(), agendamento.getDataHoraFinal());
        agendamentoRepository.save(agendamento);
        historico.registrar(agendamento, AcaoAgendamento.CONFIRMADO, chefeId, TipoUsuario.CHEFE, null, null);
        eventPublisher.publishEvent(new AgendamentoConfirmadoEvent(agendamento.getId(), chefeId));
    }

    @Transactional
    public void cancelarAgendamento(Long id, UUID clienteId, CancelamentoRequestDto dto) {
        // com trava, como toda ação que muda o agendamento; de outro cliente é tratado como inexistente
        Agendamento agendamento = agendamentoRepository.travarPorId(id)
                .filter(a -> a.getUsuario() != null && a.getUsuario().getId().equals(clienteId))
                .orElseThrow(AgendamentoException::new);
        agendamento.cancelarPeloCliente(agora());
        MotivoAgendamento motivo = dto == null ? null : dto.motivo();
        String descricao = dto == null ? null : dto.descricao();
        historico.registrar(agendamento, AcaoAgendamento.CANCELADO, clienteId, TipoUsuario.CLIENTE, motivo, descricao);
        eventPublisher.publishEvent(new AgendamentoCanceladoEvent(agendamento.getId(), motivo, descricao));
    }

    @Transactional
    public void recusarAgendamento(Long id, UUID chefeId, RecusaRequestDto dto) {
        Agendamento agendamento = doChef(id, chefeId);
        ChefeModel chefe = chefeAtivo(agendamento);
        agendamento.recusar(agora());
        registrarSaida(agendamento, chefe, AcaoAgendamento.RECUSADO, true, dto);
    }

    @Transactional
    public void desistirDoAgendamento(Long id, UUID chefeId, RecusaRequestDto dto) {
        Agendamento agendamento = doChef(id, chefeId);
        ChefeModel chefe = chefeAtivo(agendamento);
        agendamento.desistir(agora());
        registrarSaida(agendamento, chefe, AcaoAgendamento.DESISTENCIA, false, dto);
    }

    @Transactional
    public void concluirAgendamento(Long id, UUID chefeId) {
        // com trava: a rotina pode estar concluindo o mesmo agendamento agora
        Agendamento agendamento = doChef(id, chefeId);
        chefeAtivo(agendamento);
        agendamento.concluirPeloChef(agora());
        historico.registrar(agendamento, AcaoAgendamento.CONCLUIDO, chefeId, TipoUsuario.CHEFE, null, null);
    }

    /** Rotina: relê com trava e só registra se a conclusão ainda se aplicava (rodar duas vezes não duplica). */
    @Transactional
    public void concluirPeloSistema(Long id) {
        agendamentoRepository.travarPorId(id)
                .filter(agendamento -> agendamento.concluirPeloSistema(agora()))
                .ifPresent(agendamento -> historico.registrar(agendamento, AcaoAgendamento.CONCLUIDO, null, null, null, null));
    }

    @Transactional
    public void expirarPeloSistema(Long id) {
        agendamentoRepository.travarPorId(id)
                .filter(agendamento -> agendamento.expirarPeloSistema(agora()))
                .ifPresent(agendamento -> {
                    historico.registrar(agendamento, AcaoAgendamento.EXPIRADO, null, null, null, null);
                    eventPublisher.publishEvent(new AgendamentoExpiradoEvent(agendamento.getId()));
                });
    }

    /** Rotina: lembra o cliente 3 dias antes; relê com trava e confere tudo de novo (cancelado no meio do caminho não recebe). */
    @Transactional
    public void lembrarCliente(Long id) {
        LocalDateTime agora = agora();
        agendamentoRepository.travarPorId(id)
                .filter(a -> a.getStatus() == StatusAgendamento.CONFIRMADO && a.getLembreteClienteEm() == null
                        && a.getUsuario() != null && a.getDataHoraInicial() != null
                        && !a.getDataHoraInicial().isAfter(agora.plusHours(72))
                        && a.getDataHoraInicial().isAfter(agora.plusHours(48)))
                .ifPresent(a -> {
                    LocalDateTime inicio = a.getDataHoraInicial();
                    LocalDateTime prazo = inicio.minusHours(48);
                    notificacaoService.notificar(a.getUsuario().getId(), TipoUsuario.CLIENTE, EventoNotificacao.LEMBRETE_DETALHES,
                            "Seu agendamento de " + inicio.format(DIA) + " às " + inicio.format(HORA)
                                    + " é em 3 dias: confirme as restrições e observações até "
                                    + prazo.format(DIA) + " às " + prazo.format(HORA),
                            String.valueOf(a.getId()));
                    a.setLembreteClienteEm(agora);
                });
    }

    /** Rotina: resume o serviço ao chef 1 dia antes; mesma releitura com trava e nova checagem. */
    @Transactional
    public void resumirAoChef(Long id) {
        LocalDateTime agora = agora();
        agendamentoRepository.travarPorId(id)
                .filter(a -> a.getStatus() == StatusAgendamento.CONFIRMADO && a.getResumoChefEm() == null
                        && a.getDataHoraInicial() != null
                        && !a.getDataHoraInicial().isAfter(agora.plusHours(24))
                        && a.getDataHoraInicial().isAfter(agora))
                .ifPresent(a -> {
                    LocalDateTime inicio = a.getDataHoraInicial();
                    String quando = inicio.toLocalDate().equals(agora.toLocalDate()) ? "Hoje" : "Amanhã";
                    // agendamentos de antes dos menus não têm menuNome
                    String menu = a.getMenuNome() != null ? a.getMenuNome() : "seu agendamento";
                    int tipos = a.getRestricoes().size();
                    notificacaoService.notificar(a.getChefe().getId(), TipoUsuario.CHEFE, EventoNotificacao.RESUMO_DO_SERVICO,
                            quando + " às " + inicio.format(HORA) + ": " + menu + " para "
                                    + (a.getQuantidadePessoas() != null ? a.getQuantidadePessoas() : 0) + " pessoas, "
                                    + tipos + (tipos == 1 ? " restrição" : " restrições"),
                            String.valueOf(a.getId()));
                    a.setResumoChefEm(agora);
                });
    }

    /** Linha do tempo: só o cliente dono ou o chef do agendamento (mesmo depois de recusar ou desistir). */
    @Transactional(readOnly = true)
    public List<HistoricoAgendamentoDto> historico(Long id, UsuarioAutenticado usuario) {
        Agendamento agendamento = switch (usuario.tipo()) {
            case CLIENTE -> agendamentoRepository.findByIdAndUsuario_Id(id, usuario.id()).orElseThrow(AgendamentoException::new);
            // leitura: sem trava (FOR UPDATE nem é permitido numa transação somente leitura no Postgres)
            case CHEFE -> agendamentoRepository.findById(id)
                    .filter(a -> a.visivelPara(usuario.id()))
                    .orElseThrow(AgendamentoException::new);
            case MODERADOR -> throw new AgendamentoException();
        };
        List<AgendamentoHistorico> linhas = historicoRepository.findByAgendamento_IdOrderByOcorridoEmAscIdAsc(agendamento.getId());
        Map<UUID, String> nomes = nomesDosAutores(linhas);
        return linhas.stream().map(linha -> new HistoricoAgendamentoDto(
                linha.getAcao(),
                linha.getAutorId() == null ? null
                        : new HistoricoAgendamentoDto.Autor(linha.getAutorId(), nomes.get(linha.getAutorId()), linha.getAutorTipo()),
                motivoDe(linha.getMotivo()),
                linha.getDescricao(),
                linha.getOcorridoEm())).toList();
    }

    /** O nome é resolvido na leitura (o histórico guarda só o id), buscando cada tipo de autor de uma vez. */
    private Map<UUID, String> nomesDosAutores(List<AgendamentoHistorico> linhas) {
        Map<UUID, String> nomes = new HashMap<>();
        Set<UUID> clientes = linhas.stream().filter(l -> l.getAutorTipo() == TipoUsuario.CLIENTE)
                .map(AgendamentoHistorico::getAutorId).collect(Collectors.toSet());
        Set<UUID> chefes = linhas.stream().filter(l -> l.getAutorTipo() == TipoUsuario.CHEFE)
                .map(AgendamentoHistorico::getAutorId).collect(Collectors.toSet());
        usuarioRepository.findAllById(clientes).forEach(u -> nomes.put(u.getId(), u.getNome()));
        chefeRepository.findAllById(chefes).forEach(c -> nomes.put(c.getId(), c.getNome()));
        return nomes;
    }

    private static HistoricoAgendamentoDto.Motivo motivoDe(String codigo) {
        if (codigo == null) {
            return null;
        }
        MotivoAgendamento motivo = Arrays.stream(MotivoCancelamento.values()).filter(m -> m.name().equals(codigo))
                .<MotivoAgendamento>map(m -> m).findFirst()
                .orElseGet(() -> MotivoRecusa.valueOf(codigo));
        return new HistoricoAgendamentoDto.Motivo(motivo.name(), motivo.rotulo());
    }

    private void registrarSaida(Agendamento agendamento, ChefeModel chefe, AcaoAgendamento acao, boolean recusa,
                                RecusaRequestDto dto) {
        MotivoAgendamento motivo = dto == null ? null : dto.motivo();
        String descricao = dto == null ? null : dto.descricao();
        historico.registrar(agendamento, acao, chefe.getId(), TipoUsuario.CHEFE, motivo, descricao);
        eventPublisher.publishEvent(new ChefSaiuDoAgendamentoEvent(agendamento.getId(), chefe.getNome(), recusa, motivo, descricao));
    }

    /**
     * Agendamento do chef, com trava (duas ações ao mesmo tempo esperam uma pela outra em vez de
     * sobrescrever); para qualquer outro chef ele "não existe". O chef continua dono depois de recusar ou desistir.
     */
    private Agendamento doChef(Long id, UUID chefeId) {
        return agendamentoRepository.travarPorId(id)
                .filter(a -> a.visivelPara(chefeId))
                .orElseThrow(AgendamentoException::new);
    }

    /** O chef do agendamento, exigindo conta ativa (apagado ou banido não age). */
    private static ChefeModel chefeAtivo(Agendamento agendamento) {
        ChefeModel chefe = agendamento.getChefe();
        chefe.exigirAtivo();
        return chefe;
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(clock);
    }

    /** Só agendamento CONFIRMADO ocupa a agenda do chef; pedidos pendentes podem se sobrepor. */
    private void exigirHorarioLivre(Agendamento agendamento) {
        Long proprio = agendamento.getId() == null ? -1L : agendamento.getId();
        ChefeModel chefe = agendamento.getChefe();
        // a folga do chef alarga o pedido dos dois lados: ele conflita com [início − folga, fim + folga) dos confirmados
        Duration folga = chefe.folga();
        if (agendamentoRepository.existeConfirmadoSobreposto(chefe.getId(),
                agendamento.getDataHoraInicial().minus(folga), agendamento.getDataHoraFinal().plus(folga), proprio)) {
            throw new HorarioIndisponivelException(chefe.getNome());
        }
    }

    /** O chef só atende na cidade de origem dele: contra a cidade e a UF do endereço. */
    private static void exigirMesmaCidade(ChefeModel chefe, EnderecoModel endereco) {
        if (!chefe.atendeEm(endereco.getCidade(), endereco.getEstado())) {
            throw new ForaDaCidadeException(chefe.getNome(), endereco.getCidade());
        }
    }

    private EnderecoModel buscarEndereco(Long enderecoId, UUID usuarioId) {
        if (enderecoId == null) {
            throw new AgendamentoInvalidoException("Informe o endereço do agendamento");
        }
        return enderecoRepository.findByIdAndUsuario_Id(enderecoId, usuarioId).orElseThrow(EnderecoException::new);
    }

    /** Menu ativo de chef visível, e a quantidade de pessoas dentro do que o menu atende (409 nos dois casos). */
    private void exigirReservavel(Menu menu, int pessoas) {
        if (!menu.isAtivo() || !chefeRepository.visivel(menu.getChefe().getId())) {
            throw new MenuIndisponivelException();
        }
        if (pessoas < menu.getMinimoConvidados() || pessoas > menu.getMaximoConvidados()) {
            throw new ConvidadosForaDoLimiteException(menu.getMinimoConvidados(), menu.getMaximoConvidados());
        }
    }

    /** Converte e valida as restrições do pedido (400); sem a lista, não há restrições. */
    private static Set<RestricaoAgendamento> restricoesDe(List<RestricaoDto> dtos, int pessoas) {
        Set<RestricaoAgendamento> restricoes = new LinkedHashSet<>();
        if (dtos != null) {
            if (dtos.stream().map(RestricaoDto::tipo).distinct().count() != dtos.size()) {
                throw new AgendamentoInvalidoException("Restrições repetidas no pedido");
            }
            dtos.forEach(r -> restricoes.add(new RestricaoAgendamento(r.tipo(), r.quantidade())));
        }
        validarRestricoes(restricoes, pessoas);
        return restricoes;
    }

    /** Cada restrição afeta de 1 até o total de pessoas; a soma pode passar do total (uma pessoa pode ter duas). */
    private static void validarRestricoes(Set<RestricaoAgendamento> restricoes, int pessoas) {
        if (restricoes.stream().anyMatch(r -> r.quantidade() < 1 || r.quantidade() > pessoas)) {
            throw new AgendamentoInvalidoException("A quantidade de cada restrição deve ser de 1 até " + pessoas);
        }
    }

    /** Observações já moderadas; em branco viram nulo. */
    private Observacoes verificarObservacoes(String texto, UsuarioAutenticado autor) {
        if (texto == null || texto.isBlank()) {
            return new Observacoes(null, null);
        }
        String enviado = texto.strip();
        String exibido = moderacaoConteudo.verificar(enviado, autor).texto();
        return new Observacoes(exibido, exibido.equals(enviado) ? null : enviado);
    }

    /** O texto exibido e, só quando foi mascarado, o que o cliente escreveu. */
    private record Observacoes(String exibido, String original) {
        void aplicarEm(Agendamento agendamento) {
            agendamento.setObservacoes(exibido);
            agendamento.setObservacoesOriginal(original);
        }
    }
}
