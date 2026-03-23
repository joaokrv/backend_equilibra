package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.CategoriaEntity;
import org.app_financeiro.backend.enums.TipoTransacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositório JPA para operações de persistência de CategoriaEntity.
 *
 * NOTA: A entidade CategoriaEntity possui @SQLRestriction("ativo = true"),
 * portanto todas as queries derivadas filtram automaticamente por ativo = true.
 */
@Repository
public interface CategoriaRepository extends JpaRepository<CategoriaEntity, Long> {

    /**
     * Retorna todas as categorias (ativas) de um usuário.
     * Filtro ativo = true aplicado automaticamente via @SQLRestriction.
     *
     * @param usuarioId ID do usuário
     * @return Lista de categorias ativas
     */
    List<CategoriaEntity> findByUsuarioId(Long usuarioId);

    /**
     * Retorna categorias (ativas) de um usuário filtradas por tipo.
     * Filtro ativo = true aplicado automaticamente via @SQLRestriction.
     *
     * @param usuarioId ID do usuário
     * @param tipo      Tipo de transação (RECEITA ou DESPESA)
     * @return Lista de categorias do tipo informado
     */
    List<CategoriaEntity> findByUsuarioIdAndTipo(Long usuarioId, TipoTransacao tipo);

    /**
     * Retorna categorias (ativas) de um usuário filtradas por tipo e nome (case-insensitive).
     * Usado para validação de duplicidade de nome no CategoriaService.
     * Filtro ativo = true aplicado automaticamente via @SQLRestriction.
     *
     * @param usuarioId ID do usuário
     * @param tipo      Tipo de transação
     * @param nome      Texto a buscar no nome da categoria
     * @return Lista de categorias correspondentes
     */
    List<CategoriaEntity> findByUsuarioIdAndTipoAndNomeContainingIgnoreCase(Long usuarioId, TipoTransacao tipo, String nome);
}
