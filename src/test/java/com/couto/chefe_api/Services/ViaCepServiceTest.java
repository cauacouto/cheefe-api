package com.couto.chefe_api.Services;

import com.couto.chefe_api.Config.Infra.ViaCepClient;
import com.couto.chefe_api.Dtos.EnderecoResponse;
import com.couto.chefe_api.Excepitons.RecursoNaoEncontradoException;
import com.couto.chefe_api.Excepitons.RegraDeNegocioException;
import com.couto.chefe_api.Excepitons.ServicoExternoIndisponivelException;
import com.couto.chefe_api.Mapper.ViaCepMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ViaCepServiceTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://viacep.com.br");
    private final MockRestServiceServer viaCep = MockRestServiceServer.bindTo(builder).build();
    private final ViaCepService service = new ViaCepService(new ViaCepClient(builder.build()), new ViaCepMapper());

    @Test
    void cepComOuSemHifenDevolveOEndereco() {
        viaCep.expect(requestTo("https://viacep.com.br/ws/01001000/json/")).andRespond(withSuccess("""
                {"cep":"01001-000","logradouro":"Praça da Sé","bairro":"Sé","localidade":"São Paulo","uf":"SP"}
                """, MediaType.APPLICATION_JSON));

        EnderecoResponse endereco = service.buscarPorCep("01001-000");

        assertThat(endereco).isEqualTo(new EnderecoResponse("01001-000", "Praça da Sé", "Sé", "São Paulo", "SP"));
        viaCep.verify();
    }

    @Test
    void cepInexistenteDa404() {
        // o ViaCep responde 200 com "erro" (texto nas versões novas, booleano nas antigas)
        viaCep.expect(requestTo("https://viacep.com.br/ws/99999999/json/"))
                .andRespond(withSuccess("{\"erro\":\"true\"}", MediaType.APPLICATION_JSON));
        viaCep.expect(requestTo("https://viacep.com.br/ws/99999998/json/"))
                .andRespond(withSuccess("{\"erro\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.buscarPorCep("99999999")).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> service.buscarPorCep("99999998")).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void cepMalformadoDa400SemChamarOViaCep() {
        assertThatThrownBy(() -> service.buscarPorCep("123")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.buscarPorCep("0100100a")).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> service.buscarPorCep("01001-000/../x")).isInstanceOf(RegraDeNegocioException.class);
        viaCep.verify();
    }

    @Test
    void viaCepForaDoArDa502() {
        viaCep.expect(requestTo("https://viacep.com.br/ws/01001000/json/")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> service.buscarPorCep("01001000"))
                .isInstanceOfSatisfying(ServicoExternoIndisponivelException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
