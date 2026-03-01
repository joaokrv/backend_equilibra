package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para operações de persistência de CategoriaEntity.
 */
@Repository
public interface CategoriaRepository extends JpaRepository<CategoriaEntity, Long> {

    /**
     * Retorna todas as categorias ativas de um usuário.
     *
     * @param usuarioId ID do usuário
     * @return Lista de categorias ativas
     */
    List<CategoriaEntity> findByUsuarioIdAndAtivoTrue(Long usuarioId);

    /**
     * Retorna todas as categorias ativas de um usuário filtradas por tipo.
     *
     * @param usuarioId ID do usuário
     * @param tipo      Tipo de transação (RECEITA ou DESPESA)
     * @return Lista de categorias ativas do tipo informado
     */
    List<CategoriaEntity> findByUsuarioIdAndTipoAndAtivoTrue(Long usuarioId, TipoTransacao tipo);

    /**
     * Retorna categorias ativas de um usuário filtradas por tipo e com nome contendo o texto informado (case-insensitive).
     * Usado para validação de duplicidade de nome no CategoriaService.
     *
     * @param usuarioId ID do usuário
     * @param tipo      Tipo de transação
     * @param nome      Texto a buscar no nome da categoria
     * @return Lista de categorias correspondentes
     */
    List<CategoriaEntity> findByUsuarioIdAndTipoAndNomeContainingIgnoreCaseAndAtivoTrue(Long usuarioId, TipoTransacao tipo, String nome);
}
