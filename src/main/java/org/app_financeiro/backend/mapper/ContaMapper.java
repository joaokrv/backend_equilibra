package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.ContaResponseDTO;
import org.app_financeiro.backend.entity.ContaEntity;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper para conversão entre ContaEntity e ContaResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface ContaMapper {

    ContaResponseDTO toResponse(ContaEntity entity);
}
