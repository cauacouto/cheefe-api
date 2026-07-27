package com.couto.chefe_api.Chefe;

import com.couto.chefe_api.domin.ChefeModel;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
@Component
public class ChefeSpecification {


    public static Specification<ChefeModel> valorMinimoMaior(BigDecimal valorMinimo){
        return (root, query, cb) ->
                valorMinimo == null ? null : cb.greaterThanOrEqualTo(root.get("valorHora"),valorMinimo);

    }



    public static Specification<ChefeModel> valorMaximoMneor(BigDecimal valorMaximo){
        return (root, query, cb) ->
                valorMaximo == null ? null : cb.lessThanOrEqualTo(root.get("valorHora"),valorMaximo);

    }
}
