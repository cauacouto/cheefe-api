package com.couto.chefe_api.Services;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.couto.chefe_api.Dtos.CadastraPratosDtoRequest;
import com.couto.chefe_api.Dtos.CadastratPratosDtoResponse;
import com.couto.chefe_api.Dtos.ListarPratosChefes;
import com.couto.chefe_api.Excepitons.ChefeEception;
import com.couto.chefe_api.Mapper.PratosMapper;
import com.couto.chefe_api.domin.ChefeModel;
import com.couto.chefe_api.domin.Pratos;
import com.couto.chefe_api.repositorys.ChefeRepository;
import com.couto.chefe_api.repositorys.PatrosRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PratosService {


    private final String uploadDir = "uploads/pratos/";
    private final List<String> tiposPermitidos = List.of("image/jpeg","image/png","image/webp");
    private final long tamanhoMaximo = 5 * 1024 * 1024; // 5MB


    private final PatrosRepository patrosRepository;
    private final PratosMapper pratosMapper;
    private final ChefeRepository reposioty;

    @Autowired
    private Cloudinary cloudinary;


    public CadastratPratosDtoResponse cadastrarPratos(CadastraPratosDtoRequest dto, UUID id){
        ChefeModel chefe = reposioty.findById(id)
                .orElseThrow(ChefeEception::new);
        Pratos pratos = pratosMapper.toModel(dto);
        pratos.setChefeModel(chefe);
        Pratos salvo = patrosRepository.save(pratos);
        return  pratosMapper.toDto(salvo);

    }


    public List<ListarPratosChefes> listaPratos(UUID chefeId) {
        List<Pratos> pratos = chefeId != null
                ? patrosRepository.findByChefeModelId(chefeId)
                : patrosRepository.findAll();
        return pratos
                .stream()
                .map(pratosMapper::toDtoLista)
                .toList();
    }

    public CadastratPratosDtoResponse updatePratos (CadastraPratosDtoRequest dto , Integer id){
        Pratos pratos = patrosRepository.findById(id).orElseThrow(()-> new RuntimeException("prato não encontrado"));
        pratosMapper.updatePrato(dto,pratos);
        Pratos salvar = patrosRepository.save(pratos);
        return pratosMapper.toDto(salvar);
    }




    public  CadastratPratosDtoResponse uploadImagem(Integer id, MultipartFile file) {

        if (!tiposPermitidos.contains(file.getContentType())) {
            throw new RuntimeException("Apenas imagens são permitidas");
        }

        if (file.getSize() > tamanhoMaximo) {
            throw new RuntimeException("Imagem muito grande");
        }

        Pratos pratos = patrosRepository.findById(id).orElseThrow(()-> new RuntimeException("prato não encontrado"));


        try {

            Map uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "chefe-api/chefe"
                    )
            );

            String imageUrl = uploadResult.get("secure_url").toString();

            pratos.setImageUrl(imageUrl);

            patrosRepository.save(pratos);
            return new CadastratPratosDtoResponse(pratos);

        } catch (Exception e) {
            throw new RuntimeException("Erro ao enviar imagem para Cloudinary", e);
        }
    }


}
