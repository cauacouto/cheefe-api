package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Excepitons.ChefeEception;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChefeService {

    private final ChefeRepository reposioty;

    private final ChefeMapper mapper;


    public  ChefeResponseDto atualizarDados(ChefeRequestDto dto, UUID id){
        ChefeModel chefe  = reposioty.findById(id).orElseThrow(ChefeEception::new);
        mapper.updateModelFromDto(dto,chefe);
        ChefeModel salvar = reposioty.save(chefe);
        return mapper.toDto(salvar);
    }

    public Page<ChefeResponseDto> mostrarChefes (Pageable pageable){
        return reposioty.findAll(pageable).map(ChefeResponseDto::new);
    }

    public void deletar(UUID id ){
        reposioty.deleteById(id);
    }

    public void inativar(UUID id){
        ChefeModel chefe = reposioty.findById(id).orElseThrow(ChefeEception::new);
        chefe.setAtivo(false);
        reposioty.save(chefe);
    }
}
