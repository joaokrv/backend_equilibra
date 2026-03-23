package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.CategoriaResponseDTO;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper para conversão entre CategoriaEntity e CategoriaResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface CategoriaMapper {

    CategoriaResponseDTO toResponse(CategoriaEntity entity);
}
