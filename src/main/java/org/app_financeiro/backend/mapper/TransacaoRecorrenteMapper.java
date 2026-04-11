package org.app_financeiro.backend.mapper;

import org.app_financeiro.backend.dto.response.TransacaoRecorrenteResponseDTO;
import org.app_financeiro.backend.entity.TransacaoRecorrenteEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransacaoRecorrenteMapper {

    @Mapping(target = "nomeConta", source = "conta.nome")
    @Mapping(target = "nomeCartao", source = "cartao.nome")
    @Mapping(target = "nomeCategoria", source = "categoria.nome")
    TransacaoRecorrenteResponseDTO toResponse(TransacaoRecorrenteEntity entity);
}
