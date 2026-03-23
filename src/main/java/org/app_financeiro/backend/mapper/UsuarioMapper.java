package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper para conversão entre UsuarioEntity e UsuarioResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    UsuarioResponseDTO toResponse(UsuarioEntity entity);
}
