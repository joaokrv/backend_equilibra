package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper para conversão entre TransacaoEntity e TransacaoResponseDTO.
 * Navega nos relacionamentos para extrair os nomes de categoria, conta e cartão.
 */
@Mapper(componentModel = "spring")
public interface TransacaoMapper {

    @Mapping(source = "categoria.nome", target = "nomeCategoria")
    @Mapping(source = "categoria.id", target = "categoriaId")
    @Mapping(source = "conta.nome", target = "nomeConta")
    @Mapping(source = "conta.id", target = "contaId")
    @Mapping(source = "cartao.nome", target = "nomeCartao")
    @Mapping(source = "cartao.id", target = "cartaoId")
    @Mapping(target = "isRecorrente", source = ".", qualifiedByName = "mapRecorrente")
    TransacaoResponseDTO toResponse(TransacaoEntity entity);

    @Named("mapRecorrente")
    default boolean mapRecorrente(TransacaoEntity entity) {
        return entity.getRecorrente() != null;
    }
}
