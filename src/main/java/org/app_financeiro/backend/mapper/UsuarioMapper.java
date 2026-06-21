package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper para conversão entre UsuarioEntity e UsuarioResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "isEmailVerificado", source = "emailVerificado")
    UsuarioResponseDTO toResponse(UsuarioEntity entity);
}
