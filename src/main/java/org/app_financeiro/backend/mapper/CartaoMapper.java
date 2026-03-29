package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.CartaoResponseDTO;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

/**
 * MapStruct mapper para conversão entre CartaoEntity e CartaoResponseDTO.
 * O campo limiteDisponivel é calculado externamente (limite - soma das faturas abertas)
 * e passado como parâmetro adicional.
 */
@Mapper(componentModel = "spring")
public interface CartaoMapper {

    @Mapping(source = "entity.id", target = "id")
    @Mapping(source = "entity.nome", target = "nome")
    @Mapping(source = "entity.limite", target = "limite")
    @Mapping(source = "limiteDisponivel", target = "limiteDisponivel")
    @Mapping(source = "entity.diaFechamento", target = "diaFechamento")
    @Mapping(source = "entity.diaVencimento", target = "diaVencimento")
    @Mapping(source = "entity.bandeira", target = "bandeira")
    CartaoResponseDTO toResponse(CartaoEntity entity, BigDecimal limiteDisponivel);
}
