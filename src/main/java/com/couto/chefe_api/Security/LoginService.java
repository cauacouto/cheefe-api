package com.couto.chefe_api.Security;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.Excepitons.UsuarioException;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginService {


    private final UsuarioRepository repository;
    private  final UsuarioMapper UserMapper;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final ChefeMapper chefeMapper;
    private final ChefeRepository chefeRepository;

    @Autowired
    private final Cloudinary cloudinary;


    private final String uploadDir = "uploads/register/";
    private final List<String> tiposPermitidos = List.of("image/jpeg","image/png","image/webp");
    private final long tamanhoMaximo = 5 * 1024 * 1024; // 5MB



    public UserResponseDto RegisterUsuario(RegisterRequestDto dto){
        var senhaCriptografada = passwordEncoder.encode(dto.getPassword());
        UserModel usuario = UserMapper.toModel(dto, senhaCriptografada);

        List<EnderecoModel> enderecos = dto.getEndereco().stream()
                .map(UserMapper::toModel)
                .toList();
        enderecos.forEach(e -> {
            e.getTipoDeResidencia().validar(e);
            e.setUsuario(usuario);
        });
        usuario.setEndereco(enderecos);

        return UserMapper.toDto(repository.save(usuario));
    }



    public ChefeResponseDto registerChefe(RegisterRequestDto dto,MultipartFile file) {
        var senhaCriptografada = passwordEncoder.encode(dto.getPassword());
        ChefeModel chefe = chefeMapper.toModel(dto, senhaCriptografada);

        String imageUrl = uploadImg(file);
        chefe.setImageUrl(imageUrl);
        return chefeMapper.toDto(chefeRepository.save(chefe));
    }



    public String login(DadosLoginDto dados){
        var usuario = repository.findByEmail(dados.email())
                .orElseThrow(UsuarioException::new);


        if (!passwordEncoder.matches(dados.password(), usuario.getPassword())){
            throw new RuntimeException("senha invalida");
        }
        return tokenService.gerarToken(usuario);
    }


    private String uploadImg(MultipartFile file) {
        if (!tiposPermitidos.contains(file.getContentType())) {
            throw new RuntimeException("Apenas imagens são permitidas");
        }
        if (file.getSize() > tamanhoMaximo) {
            throw new RuntimeException("Imagem muito grande");
        }
        try {
            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "chefe-api/pratos")
            );
            return uploadResult.get("secure_url").toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao enviar imagem para Cloudinary", e);
        }
    }


}
