package com.couto.chefe_api.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import com.couto.chefe_api.dto.CadastraPratosDtoRequest;
import com.couto.chefe_api.dto.CadastratPratosDtoResponse;
import com.couto.chefe_api.dto.ListarPratosChefes;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.PratoNaoEncontradoException;
import com.couto.chefe_api.mapper.PratosMapper;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.enums.PastaImagem;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.PatrosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PratosService {

    static final int TAMANHO_MAXIMO_PAGINA = 50;

    private final PatrosRepository patrosRepository;
    private final PratosMapper pratosMapper;
    private final ChefeRepository chefeRepository;
    private final ImagemStorageService imagemStorageService;

    @Transactional
    public CadastratPratosDtoResponse cadastrarPratos(CadastraPratosDtoRequest dto, MultipartFile file, UUID id) {
        ChefeModel chefe = chefeRepository.findById(id)
                .orElseThrow(ChefeNaoEncontradoException::new);
        chefe.exigirAtivo();

        Pratos pratos = pratosMapper.toModel(dto);
        pratos.setChefeModel(chefe);
        pratos.setImageUrl(imagemStorageService.enviar(file, PastaImagem.PRATOS));

        Pratos salvo = patrosRepository.save(pratos);
        return pratosMapper.toDto(salvo);
    }

    /** Vitrine: só pratos de chefs visíveis (pausado, suspenso, banido ou ocultado por denúncias não aparece). */
    @Transactional(readOnly = true)
    public Page<ListarPratosChefes> listarVisiveis(Pageable pageable) {
        PageRequest pagina = PageRequest.of(pageable.getPageNumber(),
                Math.max(1, Math.min(pageable.getPageSize(), TAMANHO_MAXIMO_PAGINA)));
        return patrosRepository.findVisiveis(pagina).map(pratosMapper::toDtoLista);
    }

    /** Cardápio de um chef: vazio se ele não está visível, igual a um chef que não existe. */
    @Transactional(readOnly = true)
    public List<ListarPratosChefes> listarDoChefe(UUID chefeId) {
        return patrosRepository.findVisiveisDoChefe(chefeId).stream().map(pratosMapper::toDtoLista).toList();
    }

    @Transactional(readOnly = true)
    public List<ListarPratosChefes> listarMeus(UUID chefeId) {
        return patrosRepository.findByChefeModelId(chefeId).stream().map(pratosMapper::toDtoLista).toList();
    }

    @Transactional
    public CadastratPratosDtoResponse updatePratos(CadastraPratosDtoRequest dto, Integer pratoId, UUID chefeId) {
        // prato de outro chef é tratado como inexistente
        Pratos pratos = patrosRepository.findByIdAndChefeModel_Id(pratoId, chefeId).orElseThrow(PratoNaoEncontradoException::new);
        pratos.getChefeModel().exigirAtivo();
        pratosMapper.updatePrato(dto, pratos);
        Pratos salvar = patrosRepository.save(pratos);
        return pratosMapper.toDto(salvar);
    }
}
