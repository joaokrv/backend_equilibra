package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaRepository extends JpaRepository<CategoriaEntity, Long> {
    List<CategoriaEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
    List<CategoriaEntity> findByUsuarioIdAndTipoAndAtivoTrue(Long usuarioId, TipoTransacao tipo);
    List<CategoriaEntity> findByUsuarioIdAndTipoAndNomeContainingIgnoreCaseAndAtivoTrue(Long usuarioId, TipoTransacao tipo, String nome);
}
