package com.couto.chefe_api.Dtos;

/** Resposta do ViaCep; CEP inexistente vem com 200 e "erro" preenchido (texto ou booleano, conforme a versão). */
public record ViaCepDto(
        String cep,
        String logradouro,
        String bairro,
        String localidade,
        String uf,
        String erro
) {

    public boolean naoEncontrado() {
        return "true".equalsIgnoreCase(erro);
    }
}
