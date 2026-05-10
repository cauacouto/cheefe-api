package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.Excepitons.ChefeEception;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.Mapper.PratosMapper;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Pratos;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.PatrosRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChefeService {

    private final ChefeRepository reposioty;

    private final ChefeMapper mapper;

    private final PatrosRepository patrosRepository;

    private final PratosMapper pratosMapper;

    public ChefeService(ChefeRepository reposioty, ChefeMapper mapper, PatrosRepository patrosRepository, PratosMapper pratosMapper) {
        this.reposioty = reposioty;
        this.mapper = mapper;
        this.patrosRepository = patrosRepository;
        this.pratosMapper = pratosMapper;
    }

    public ChefeResponseDto salvarChefe(ChefeRequestDto dto){
        ChefeModel chefe = mapper.toModel(dto);
        ChefeModel salvo = reposioty.save(chefe);
        return mapper.toDto(salvo);

    }

    public CadastratPratosDtoResponse cadastrarPratos(CadastraPratosDtoRequest dto, UUID id){
        ChefeModel chefe = reposioty.findById(id)
                .orElseThrow(ChefeEception::new);
            Pratos pratos = pratosMapper.toModel(dto);
            pratos.setChefeModel(chefe);
            Pratos salvo = patrosRepository.save(pratos);
             return  pratosMapper.toDto(salvo);

    }

    public List<ListarPratosChefes> listaPratos(UUID chefeId){
        List<Pratos> pratos = chefeId != null
                ? patrosRepository.findByChefeModelId(chefeId)
                :patrosRepository.findAll();

        return pratos
                .stream()
                .map(this::toDto)
                .toList();
    }



    public List<ListarPratosChefes> listaPratosPorChefe(UUID id){ //usar UserDetails
        return patrosRepository.findByChefeModelId(id)
                .stream()
                .map(p -> new ListarPratosChefes(
                        p.getChefeModel().getNome(),
                        p.getNome(),
                        p.getDescricao(),
                        p.getImageUrl()
                ))
                .toList();
    }

    private ListarPratosChefes toDto(Pratos p){
        return new ListarPratosChefes(
                p.getChefeModel().getNome(),
                p.getNome(),
                p.getDescricao(),
                p.getImageUrl()
        );
    }

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
