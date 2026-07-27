package com.couto.chefe_api.Security;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.couto.chefe_api.Config.Infra.ResendService;
import com.couto.chefe_api.Dtos.*;
import com.couto.chefe_api.Mapper.ChefeMapper;
import com.couto.chefe_api.Mapper.UsuarioMapper;
import com.couto.chefe_api.Security.otp.OtpService;
import com.couto.chefe_api.User.UserModel;
import com.couto.chefe_api.User.UsuarioRepository;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.EnderecoModel;
import com.couto.chefe_api.repositorys.ChefeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginService {


    private final UsuarioRepository repository;
    private  final UsuarioMapper UserMapper;
    private final TokenService tokenService;
    private final ChefeMapper chefeMapper;
    private final ChefeRepository chefeRepository;
    private final OtpService otpService;
    private final ResendService resendService;


    private final Cloudinary cloudinary;


    private final String uploadDir = "uploads/register/";
    private final List<String> tiposPermitidos = List.of("image/jpeg","image/png","image/webp");
    private final long tamanhoMaximo = 5 * 1024 * 1024; // 5MB



    public UserResponseDto RegisterUsuario(RegisterRequestDto dto,MultipartFile file){
        //verificar se email ja cadastrado
        UserModel usuario = UserMapper.toEntity(dto);
        String imageUrl = uploadImg(file);
        usuario.setImageUrl(imageUrl);

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
        //verificar se email ja cadastrado
        ChefeModel chefe = chefeMapper.toModel(dto);
        String imageUrl = uploadImg(file);
        chefe.setImageUrl(imageUrl);
        return chefeMapper.toDto(chefeRepository.save(chefe));
    }



    public String solicitarOtp(String email){

        String otpId = UUID.randomUUID().toString();
        String codigo = otpService.gerarCodigo();
        otpService.salvarCodigo(otpId,codigo,email);
       String reponse = resendService.enviarOtp(email,codigo);
        System.out.println(reponse);
        return otpId;
    }


    public LoginResponseDto login(DadosLoginDto dados){


       otpData data = otpService.validarCodigo(
                dados.otpId(),
                dados.codigo()

        );

        var usuario = repository.findByEmail(data.email());


        if (usuario.isEmpty()){
            return new LoginResponseDto(
                    true,
                    data.email(),
                    null
            );
        }


        String token = tokenService.gerarToken(usuario.get());

        return new LoginResponseDto(
                false,
                null,
                token
        );
    }


    private String uploadImg(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return null; // ou uma URL de imagem padrão
        }

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
