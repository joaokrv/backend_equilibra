package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper para conversão entre InvestimentoEntity e InvestimentoResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface InvestimentoMapper {

    @Mapping(source = "tipoInvestimento", target = "tipoInvestimento")
    @Mapping(source = "tipoPersonalizado", target = "tipoPersonalizado")
    @Mapping(source = "contaOrigem.nome", target = "nomeContaOrigem")
    @Mapping(source = "contaDestino.nome", target = "nomeContaDestino")
    InvestimentoResponseDTO toResponse(InvestimentoEntity entity);
}
