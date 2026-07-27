package com.couto.chefe_api.Services;

import com.couto.chefe_api.Chefe.ChefeSpecification;
import com.couto.chefe_api.Dtos.ChefeRequestDto;
import com.couto.chefe_api.Dtos.ChefeResponseDto;
import com.couto.chefe_api.Excepitons.ChefeEception;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChefeService {

    private final ChefeRepository chefeRepository;

    private final ChefeMapper mapper;
    private final ChefeMapper chefeMapper;


    public  ChefeResponseDto atualizarDados(ChefeRequestDto dto, UUID id){
        ChefeModel chefe  = chefeRepository.findById(id).orElseThrow(ChefeEception::new);
        mapper.updateModelFromDto(dto,chefe);
        ChefeModel salvar = chefeRepository.save(chefe);
        return mapper.toDto(salvar);
    }

    public Page<ChefeResponseDto> mostrarChefes (Pageable pageable, BigDecimal valorMnimmo, BigDecimal valorMaximo){
        Specification<ChefeModel> spec = Specification
                .where(ChefeSpecification.valorMinimoMaior(valorMnimmo))
                .and(ChefeSpecification.valorMaximoMneor(valorMaximo));

        return chefeRepository.findAll(spec,pageable)
                .map(chefeMapper::toDto);
    }


    public Page<ChefeResponseDto> chefesMaisAgendados(Pageable pageable) {
        return chefeRepository.findMaisAgendados(pageable)
                .map(chefeMapper::toDto);
    }

    public void deletar(UUID id ){
        chefeRepository.deleteById(id);
    }

    public void inativar(UUID id){
        ChefeModel chefe = chefeRepository.findById(id).orElseThrow(ChefeEception::new);
        chefe.setAtivo(false);
        chefeRepository.save(chefe);
    }


}
