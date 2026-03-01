package org.app_financeiro.backend.repository;

import org.app_financeiro.backend.entity.UsuarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para operações de persistência de UsuarioEntity.
 */
@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

    /**
     * Busca um usuário ativo pelo e-mail.
     *
     * @param email E-mail do usuário
     * @return Optional com o usuário ativo, ou vazio se não encontrado
     */
    Optional<UsuarioEntity> findByEmailAndAtivoTrue(String email);

    /**
     * Busca um usuário pelo e-mail independentemente do status.
     * Usado para validar duplicidade de e-mail no registro.
     *
     * @param email E-mail do usuário
     * @return Optional com o usuário, ou vazio se não encontrado
     */
    Optional<UsuarioEntity> findByEmail(String email);

    /**
     * Busca usuários por ID e status ativo.
     *
     * @param id    ID do usuário
     * @param ativo true para ativos, false para inativos
     * @return Lista de usuários correspondentes
     */
    List<UsuarioEntity> findByIdAndAtivo(Long id, boolean ativo);
}
