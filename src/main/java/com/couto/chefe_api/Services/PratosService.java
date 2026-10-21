package com.couto.chefe_api.Services;

import com.couto.chefe_api.Dtos.CadastraPratosDtoRequest;
import com.couto.chefe_api.Dtos.CadastratPratosDtoResponse;
import com.couto.chefe_api.Dtos.ListarPratosChefes;
import com.couto.chefe_api.Excepitons.ChefeNaoEncontradoException;
import com.couto.chefe_api.Excepitons.PratoNaoEncontradoException;
import com.couto.chefe_api.Mapper.PratosMapper;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Pratos;
import com.couto.chefe_api.enums.PastaImagem;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.PatrosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PratosService {

    private final PatrosRepository patrosRepository;
    private final PratosMapper pratosMapper;
    private final ChefeRepository chefeRepository;
    private final ImagemStorageService imagemStorageService;

    @Transactional
    public CadastratPratosDtoResponse cadastrarPratos(CadastraPratosDtoRequest dto, MultipartFile file, UUID id) {
        ChefeModel chefe = chefeRepository.findById(id)
                .orElseThrow(ChefeNaoEncontradoException::new);

        Pratos pratos = pratosMapper.toModel(dto);
        pratos.setChefeModel(chefe);
        pratos.setImageUrl(imagemStorageService.enviar(file, PastaImagem.PRATOS));

        Pratos salvo = patrosRepository.save(pratos);
        return pratosMapper.toDto(salvo);
    }

    @Transactional(readOnly = true)
    public List<ListarPratosChefes> listaPratos(UUID chefeId) {
        List<Pratos> pratos = chefeId != null
                ? patrosRepository.findByChefeModelId(chefeId)
                : patrosRepository.findAll();
        return pratos
                .stream()
                .map(pratosMapper::toDtoLista)
                .toList();
    }

    @Transactional
    public CadastratPratosDtoResponse updatePratos(CadastraPratosDtoRequest dto, Integer pratoId, UUID chefeId) {
        // prato de outro chef é tratado como inexistente
        Pratos pratos = patrosRepository.findByIdAndChefeModel_Id(pratoId, chefeId).orElseThrow(PratoNaoEncontradoException::new);
        pratosMapper.updatePrato(dto, pratos);
        Pratos salvar = patrosRepository.save(pratos);
        return pratosMapper.toDto(salvar);
    }
}
