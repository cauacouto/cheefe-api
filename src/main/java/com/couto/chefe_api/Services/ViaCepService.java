package com.couto.chefe_api.Services;

import com.couto.chefe_api.Config.Infra.ViaCepClient;
import com.couto.chefe_api.Dtos.EnderecoResponse;
import com.couto.chefe_api.Dtos.ViaCepDto;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.Excepitons.RegraDeNegocioException;
import com.couto.chefe_api.Mapper.ViaCepMapper;
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
