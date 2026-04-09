package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.AgendamentoResquestDto;
import com.couto.chefe_api.Dtos.AgendamentoResponseDto;
import com.couto.chefe_api.Excepitons.EnderecoException;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Excepitons.disponivelExcepiton;
import com.couto.chefe_api.Mapper.AgendamentoMapper;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.Agendamento;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.repositorys.AgendamentoRepository;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.EndercoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

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


    public AgendamentoResponseDto criarAgendamento(AgendamentoResquestDto dto, String email){

        var user = usuarioRepository.findByEmail(email).orElseThrow(UsuarioException::new);

        List<ChefeModel> chefes = chefeRepository.findAllById(dto.getChefeIds());


        EnderecoModel endereco = endercoRepository.findById(dto.getEnderecoId()).orElseThrow(EnderecoException::new);


        boolean todosValidos = chefes.stream()
                .allMatch(ChefeModel-> ChefeModel.isDisponivel() && ChefeModel.getAtivo());
        if (!todosValidos){
            throw new disponivelExcepiton();
        }




        Agendamento agendamento = new Agendamento();

        agendamento.setUsuario(user);
        agendamento.setEndereco(endereco);
        agendamento.setChefes(chefes);
        agendamento.setDatahora(dto.getDataHora());
        return agendamentoMapper.toDto(agendamentoRepository.save(agendamento));



    }
}
