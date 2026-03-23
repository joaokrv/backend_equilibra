package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.FaturaResponseDTO;
import org.app_financeiro.backend.entity.FaturaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

/**
 * MapStruct mapper para conversão entre FaturaEntity e FaturaResponseDTO.
 * Campos especiais:
 * - cartaoId / cartaoNome: navegação no relacionamento cartao
 * - valorRestante: campo calculado (valorTotal - valorPago)
 */
@Mapper(componentModel = "spring")
public interface FaturaMapper {

    @Mapping(source = "cartao.id", target = "cartaoId")
    @Mapping(source = "cartao.nome", target = "cartaoNome")
    @Mapping(source = ".", target = "valorRestante", qualifiedByName = "calcularValorRestante")
    FaturaResponseDTO toResponse(FaturaEntity entity);

    @Named("calcularValorRestante")
    default BigDecimal calcularValorRestante(FaturaEntity entity) {
        if (entity.getValorTotal() == null || entity.getValorPago() == null) {
            return BigDecimal.ZERO;
        }
        return entity.getValorTotal().subtract(entity.getValorPago());
    }
}
