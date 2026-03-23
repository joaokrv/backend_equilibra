package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.InvestimentoResponseDTO;
import org.app_financeiro.backend.entity.InvestimentoEntity;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper para conversão entre InvestimentoEntity e InvestimentoResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface InvestimentoMapper {

    InvestimentoResponseDTO toResponse(InvestimentoEntity entity);
}
