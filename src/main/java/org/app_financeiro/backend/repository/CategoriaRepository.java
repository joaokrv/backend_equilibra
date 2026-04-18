package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** @SQLRestriction("ativo = true") filtra automaticamente em todas as queries derivadas. */
@Repository
public interface CategoriaRepository extends JpaRepository<CategoriaEntity, Long> {

    List<CategoriaEntity> findByUsuarioId(Long usuarioId);

    List<CategoriaEntity> findByUsuarioIdAndTipo(Long usuarioId, TipoTransacao tipo);

    List<CategoriaEntity> findByUsuarioIdAndTipoAndNomeContainingIgnoreCase(Long usuarioId, TipoTransacao tipo, String nome);
}
