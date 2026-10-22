package com.couto.chefe_api.service;

import com.couto.chefe_api.config.infra.ViaCepClient;
import com.couto.chefe_api.dto.EnderecoResponse;
import com.couto.chefe_api.dto.ViaCepDto;
import com.couto.chefe_api.exception.RecursoNaoEncontradoException;
import com.couto.chefe_api.exception.RegraDeNegocioException;
import com.couto.chefe_api.mapper.ViaCepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ViaCepService {

    private static final Pattern CEP = Pattern.compile("\\d{5}-?\\d{3}");

    private final ViaCepClient viaCepClient;
    private final ViaCepMapper viaCepMapper;

    /** Rota pública: só 8 dígitos (com ou sem hífen) chegam ao ViaCep. */
    public EnderecoResponse buscarPorCep(String cep) {
        if (cep == null || !CEP.matcher(cep).matches()) {
            throw new RegraDeNegocioException("CEP inválido: use 8 dígitos, com ou sem hífen");
        }
        ViaCepDto endereco = viaCepClient.buscarPorCep(cep.replace("-", ""));
        if (endereco == null || endereco.naoEncontrado()) {
            throw new RecursoNaoEncontradoException("CEP não encontrado");
        }
        return viaCepMapper.toResponse(endereco);
    }
}
