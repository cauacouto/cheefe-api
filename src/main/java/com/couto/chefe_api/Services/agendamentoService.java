package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Dtos.AgendendamenResponseDto;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Excepitons.disponivelExcepiton;
import com.couto.chefe_api.Mapper.AgendamentoMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class agendamentoService {

private final UsuarioRepository usuarioRepository;

private final ChefeRepository chefeRepository;

private final AgendamentoMapper agendamentoMapper;

private final AgendamentoRepository agendamentoRepository;

    public agendamentoService(UsuarioRepository usuarioRepository, ChefeRepository chefeRepository, AgendamentoMapper agendamentoMapper, AgendamentoRepository agendamentoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.chefeRepository = chefeRepository;
        this.agendamentoMapper = agendamentoMapper;
        this.agendamentoRepository = agendamentoRepository;
    }


    public AgendendamenResponseDto criarAgendamento(AgendamentoResquestDto dto,String email){

       var user = usuarioRepository.findByEmail(email).orElseThrow(UsuarioException::new);

        List<ChefeModel> chefes = chefeRepository.findAllById(dto.getChefeIds());



        boolean todosValidos = chefes.stream()
                .allMatch(ChefeModel-> ChefeModel.isDisponivel() && ChefeModel.getAtivo());
        if (!todosValidos){
            throw new disponivelExcepiton();
        }

        Agendamento agendamento = agendamentoMapper.toModel(dto);
        Agendamento salvo = agendamentoRepository.save(agendamento);
        return agendamentoMapper.toDto(salvo);



    }
}
