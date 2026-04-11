package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.UsuarioResponseDTO;
import org.app_financeiro.backend.entity.UsuarioEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Base64;

/**
 * MapStruct mapper para conversão entre UsuarioEntity e UsuarioResponseDTO.
 */
@Mapper(componentModel = "spring")
public interface UsuarioMapper {

    @Mapping(target = "fotoBase64", source = "foto", qualifiedByName = "toBase64")
    @Mapping(target = "isEmailVerificado", source = "emailVerificado")
    UsuarioResponseDTO toResponse(UsuarioEntity entity);

    @Named("toBase64")
    default String toBase64(byte[] foto) {
        if (foto == null) return null;
        return Base64.getEncoder().encodeToString(foto);
    }
}
