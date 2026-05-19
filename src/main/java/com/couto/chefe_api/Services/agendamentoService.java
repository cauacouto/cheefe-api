package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoChefeDto;
import com.couto.chefe_api.Dtos.ListaAgendamentoUsuarioDto;
import com.couto.chefe_api.Excepitons.AgendamentoException;
import com.couto.chefe_api.Excepitons.EnderecoException;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Excepitons.disponivelExcepiton;
import com.couto.chefe_api.Mapper.AgendamentoMapper;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.enums.StatusAgendamento;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class agendamentoService {

    private final UsuarioRepository usuarioRepository;

    private final ChefeRepository chefeRepository;

    private final AgendamentoMapper agendamentoMapper;
    private final EndercoRepository endercoRepository;

    private final AgendamentoRepository agendamentoRepository;

    public agendamentoService(UsuarioRepository usuarioRepository, ChefeRepository chefeRepository, AgendamentoMapper agendamentoMapper, EndercoRepository endercoRepository, AgendamentoRepository agendamentoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.chefeRepository = chefeRepository;
        this.agendamentoMapper = agendamentoMapper;
        this.endercoRepository = endercoRepository;
        this.agendamentoRepository = agendamentoRepository;
    }

@Transactional
    public AgendamentoResponseDto criarAgendamento(AgendamentoResquestDto dto, String email){

        var user = usuarioRepository.findByEmail(email).orElseThrow(UsuarioException::new);

        List<ChefeModel> chefes = chefeRepository.findAllById(dto.getChefeIds());


        EnderecoModel endereco = endercoRepository.findById(dto.getEnderecoId()).orElseThrow(EnderecoException::new);


        boolean todosValidos = chefes.stream()
                .allMatch(ChefeModel-> ChefeModel.isDisponivel() && ChefeModel.getAtivo());
        if (!todosValidos){
            throw new disponivelExcepiton();
        }

        if (dto.getDataHoraFinal().isAfter(dto.getDataHoraInicial())){
            throw new IllegalArgumentException("data/hora final deve ser maior que a inicial");
        }

       BigDecimal horas = BigDecimal.valueOf(
            ChronoUnit.MINUTES.between(dto.getDataHoraInicial(),dto.getDataHoraFinal()))
            .divide(BigDecimal.valueOf(60),2, RoundingMode.HALF_UP);


        BigDecimal valorTotal = chefes.stream()
                .map(ChefeModel::getValorHora)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO,BigDecimal::add)
                .multiply(horas);

        Agendamento agendamento = new Agendamento();

        agendamento.setUsuario(user);
        agendamento.setEndereco(endereco);
        agendamento.setChefes(chefes);
        agendamento.setDataHoraInicial(dto.getDataHoraInicial());
        agendamento.setDataHoraFinal(dto.getDataHoraFinal());
        agendamento.setQuantidadePessoas(dto.getQuantidadePessoas());
        agendamento.setValorTotal(valorTotal);
        return agendamentoMapper.toDto(agendamentoRepository.save(agendamento));



    }


    public List<ListaAgendamentoChefeDto> listarAgendamentosChefe(UUID chefeId){
     return agendamentoMapper.toListChefe(
             agendamentoRepository.findAllByChefe(chefeId)
     );
    }

    public List<ListaAgendamentoUsuarioDto> listarAgendamentosUsuario(UUID UsuarioId){
        return agendamentoMapper.toListUser(
                agendamentoRepository.findAllByUsuario_Id(UsuarioId)
        );
    }



    @Transactional
    public AgendamentoResponseDto atualizarAgedamento(AgendamentoResquestDto dto,Long AgedamentoId){
        Agendamento agenda = agendamentoRepository.findById(AgedamentoId)
                .orElseThrow(AgendamentoException::new);

        EnderecoModel endereco = endercoRepository.findById(dto.getEnderecoId())
                .orElseThrow(EnderecoException::new);
        if (dto.getEnderecoId() != null){
            agenda.setEndereco(endereco);
        }

        if (dto.getChefeIds() != null && dto.getChefeIds().isEmpty()){
            List<ChefeModel> chefes = chefeRepository.findAllById(dto.getChefeIds());

            boolean todosValidos = chefes.stream().allMatch(ChefeModel->
                    ChefeModel.isDisponivel() && ChefeModel.getAtivo());
            if (!todosValidos){
                throw  new disponivelExcepiton();

            }
            agenda.setChefes(chefes);
        }

        if (dto.getDataHoraInicial() != null){
            dto.setDataHoraInicial(dto.getDataHoraInicial());
        }
        if (dto.getDataHoraFinal() != null){
            dto.setDataHoraFinal(dto.getDataHoraFinal());
        }

        if (dto.getDataHoraInicial() != null || dto.getDataHoraFinal() != null){
            BigDecimal horas = BigDecimal.valueOf(
                            ChronoUnit.MINUTES.between(
                                    agenda.getDataHoraInicial(),
                                    agenda.getDataHoraFinal()))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);


            BigDecimal valorTotal = agenda.getChefes().stream()
                    .map(ChefeModel::getValorHora)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .multiply(horas);

            agenda.setValorTotal(valorTotal);

        }


        return agendamentoMapper.toDto(agendamentoRepository.save(agenda));


    }


    public void confirmarAgendamento(Long idAgendamento){
        Agendamento agendamento = agendamentoRepository.findById(idAgendamento)
                .orElseThrow(AgendamentoException::new);

            agendamento.setStatus(StatusAgendamento.CONFIRMADO);
            agendamentoRepository.save(agendamento);

    }
}
