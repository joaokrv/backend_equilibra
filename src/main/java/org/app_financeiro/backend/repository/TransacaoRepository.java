package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.ContaEntity;
import org.app_financeiro.backend.entity.CartaoEntity;
import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.entity.TransacaoEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransacaoRepository extends JpaRepository<TransacaoEntity, Long> {
    List<TransacaoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);
    List<TransacaoEntity> findByUsuarioIdAndDataBetweenAndAtivoTrue(Long usuarioId, LocalDate start, LocalDate end);
    List<TransacaoEntity> findByUsuarioIdAndTipoAndAtivoTrue(Long usuarioId, TipoTransacao tipo);
    List<TransacaoEntity> findByUsuarioIdAndCategoriaAndDataBetweenAndAtivoTrue(Long usuarioId, CategoriaEntity categoria, LocalDate start, LocalDate end);
    List<TransacaoEntity> findByUsuarioIdAndContaAndDataBetweenAndAtivoTrue(Long usuarioId, ContaEntity conta, LocalDate start, LocalDate end);
    List<TransacaoEntity> findByUsuarioIdAndCartaoAndDataBetweenAndAtivoTrue(Long usuarioId, CartaoEntity cartao, LocalDate start, LocalDate end);
    Page<TransacaoEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId, Pageable pageable);
}
