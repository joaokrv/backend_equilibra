package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para operações de persistência de UsuarioEntity.
 *
 * NOTA: A entidade UsuarioEntity possui @SQLRestriction("ativo = true"),
 * portanto todas as queries derivadas filtram automaticamente por ativo = true.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

    /**
     * Busca um usuário ativo pelo e-mail.
     * Filtra automaticamente por ativo = true via @SQLRestriction.
     *
     * @param email E-mail do usuário
     * @return Optional com o usuário ativo, ou vazio se não encontrado
     */
    Optional<UsuarioEntity> findByEmail(String email);

    /**
     * Busca um usuário ativo pelo número de celular.
     * Filtra automaticamente por ativo = true via @SQLRestriction.
     *
     * @param celular Número do celular (somente dígitos)
     * @return Optional com o usuário ativo, ou vazio se não encontrado
     */
    Optional<UsuarioEntity> findByCelular(String celular);

    /**
     * Busca um usuário pelo e-mail independentemente do status (ativo/inativo).
     * Usa query nativa para contornar o @SQLRestriction e verificar duplicidade no registro.
     *
     * @param email E-mail do usuário
     * @return true se já existe um usuário com este e-mail (ativo ou inativo)
     */
    @Query(value = "SELECT COUNT(*) > 0 FROM usuarios u WHERE u.email = :email", nativeQuery = true)
    boolean existsByEmailIncludingInactive(String email);

    /**
     * Busca usuários por ID e status ativo.
     *
     * @param id    ID do usuário
     * @param isAtivo true para ativos, false para inativos
     * @return Lista de usuários correspondentes
     */
    List<UsuarioEntity> findByIdAndIsAtivo(Long id, boolean isAtivo);

    /**
     * Busca um usuário inativo pelo e-mail, ignorando o @SQLRestriction.
     * Usado exclusivamente para o fluxo de reativação de conta.
     *
     * @param email E-mail do usuário
     * @return Optional com o usuário inativo, ou vazio se não encontrado
     */
    @Query(value = "SELECT * FROM usuarios u WHERE u.email = :email AND u.ativo = false", nativeQuery = true)
    Optional<UsuarioEntity> findInactiveByEmail(String email);
}
