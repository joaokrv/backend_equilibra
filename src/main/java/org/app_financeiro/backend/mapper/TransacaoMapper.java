package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.TransacaoResponseDTO;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper para conversão entre TransacaoEntity e TransacaoResponseDTO.
 * Navega nos relacionamentos para extrair os nomes de categoria, conta e cartão.
 */
@Mapper(componentModel = "spring")
public interface TransacaoMapper {

    @Mapping(source = "categoria.nome", target = "nomeCategoria")
    @Mapping(source = "conta.nome", target = "nomeConta")
    @Mapping(source = "cartao.nome", target = "nomeCartao")
    TransacaoResponseDTO toResponse(TransacaoEntity entity);
}
