package com.couto.chefe_api.service;

import com.couto.chefe_api.dto.CadastraPratosDtoRequest;
import com.couto.chefe_api.exception.ChefeNaoEncontradoException;
import com.couto.chefe_api.exception.PratoNaoEncontradoException;
import com.couto.chefe_api.mapper.PratosMapperImpl;
import com.couto.chefe_api.domain.ChefeModel;
import com.couto.chefe_api.domain.Pratos;
import com.couto.chefe_api.enums.PastaImagem;
import com.couto.chefe_api.repository.ChefeRepository;
import com.couto.chefe_api.repository.PratosRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PratosServiceTest {

    private final PratosRepository pratosRepository = mock(PratosRepository.class);
    private final ChefeRepository chefeRepository = mock(ChefeRepository.class);
    private final ImagemStorageService imagemStorage = mock(ImagemStorageService.class);
    private PratosService service;

    private final ChefeModel chefe = new ChefeModel();
    private final MockMultipartFile foto = new MockMultipartFile("foto", "p.png", "image/png", new byte[]{1, 2});

    @BeforeEach
    void setUp() {
        service = new PratosService(pratosRepository, new PratosMapperImpl(), chefeRepository, imagemStorage);
        chefe.setId(UUID.randomUUID());
        chefe.setNome("Chefe");
        when(chefeRepository.findById(chefe.getId())).thenReturn(Optional.of(chefe));
        when(pratosRepository.save(any(Pratos.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imagemStorage.enviar(foto, PastaImagem.PRATOS)).thenReturn("https://cdn/prato.png");
    }

    @Test
    void cadastraPratoComImagemNaPastaDePratos() {
        service.cadastrarPratos(new CadastraPratosDtoRequest("Moqueca", "Peixe"), foto, chefe.getId());

        ArgumentCaptor<Pratos> salvo = ArgumentCaptor.forClass(Pratos.class);
        verify(pratosRepository).save(salvo.capture());
        assertThat(salvo.getValue().getImageUrl()).isEqualTo("https://cdn/prato.png");
        assertThat(salvo.getValue().getChefeModel()).isSameAs(chefe);
        verify(imagemStorage).enviar(foto, PastaImagem.PRATOS);
    }

    @Test
    void chefeInexistenteNaoFazUpload() {
        assertThatThrownBy(() -> service.cadastrarPratos(
                new CadastraPratosDtoRequest("Moqueca", "Peixe"), foto, UUID.randomUUID()))
                .isInstanceOf(ChefeNaoEncontradoException.class);
        verifyNoInteractions(imagemStorage);
    }

    @Test
    void atualizarPratoInexistenteDa404() {
        when(pratosRepository.findByIdAndChefeModel_Id(99, chefe.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePratos(new CadastraPratosDtoRequest("a", "b"), 99, chefe.getId()))
                .isInstanceOf(PratoNaoEncontradoException.class);
    }

    @Test
    void updatePratoDeOutroChefDa404() {
        UUID outroChef = UUID.randomUUID();
        when(pratosRepository.findByIdAndChefeModel_Id(1, outroChef)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePratos(new CadastraPratosDtoRequest("Hacked", "x"), 1, outroChef))
                .isInstanceOf(PratoNaoEncontradoException.class);
        verify(pratosRepository, never()).save(any());
    }

    @Test
    void updatePratoProprioAtualiza() {
        Pratos prato = new Pratos();
        prato.setId(1);
        prato.setNomePrato("Antigo");
        prato.setChefeModel(chefe);
        when(pratosRepository.findByIdAndChefeModel_Id(1, chefe.getId())).thenReturn(Optional.of(prato));
        when(pratosRepository.save(any(Pratos.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updatePratos(new CadastraPratosDtoRequest("Novo", "desc"), 1, chefe.getId());

        assertThat(prato.getNomePrato()).isEqualTo("Novo");
    }
}
