package com.couto.chefe_api.User;

import com.couto.chefe_api.Dtos.UserRequestDto;
import com.couto.chefe_api.Dtos.UserResponseDto;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.repositorys.EndercoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository repository;
    private final UsuarioMapper usuarioMapper;
    private final EndercoRepository enderecoRepository;

    /**
     * Substitui os dados e a lista de endereços do usuário.
     *
     * <p>A lista gerenciada pelo Hibernate é mantida (clear + addAll) em vez de
     * ser trocada por uma lista nova. Como {@code UserModel.endereco} ainda não
     * tem {@code orphanRemoval}, os endereços antigos são apagados aqui.</p>
     */
    @Transactional
    public UserResponseDto atualizarUsuario(UserRequestDto dto, UUID id) {
        UserModel user = repository.findById(id).orElseThrow(UsuarioException::new);

        List<EnderecoModel> novos = dto.endereco().stream()
                .map(usuarioMapper::toModel)
                .toList();
        novos.forEach(endereco -> {
            endereco.getTipoDeResidencia().validar(endereco);
            endereco.setUsuario(user);
        });

        user.setNome(dto.nome());
        user.setEmail(dto.email());
        user.setTelefone(dto.telefone());

        if (user.getEndereco() == null) {
            user.setEndereco(new ArrayList<>());
        }
        List<EnderecoModel> antigos = new ArrayList<>(user.getEndereco());
        user.getEndereco().clear();
        user.getEndereco().addAll(novos);
        enderecoRepository.deleteAll(antigos);

        return usuarioMapper.toDto(repository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> listarUsuarios(Pageable pageable) {
        return repository.findAll(pageable)
                .map(usuarioMapper::toDto);
    }

    @Transactional(readOnly = true)
    public UserResponseDto buscarPorId(UUID id) {
        UserModel user = repository.findById(id)
                .orElseThrow(UsuarioException::new);
        return usuarioMapper.toDto(user);
    }

    @Transactional
    public void deletar(UUID id) {
        repository.deleteById(id);
    }
}
