package org.app_financeiro.backend.entity;

import jakarta.persistence.*;
import org.app_financeiro.backend.enums.MoedaEnum;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Raiz da hierarquia de dados. Soft delete via ativo=false preserva histórico. */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "usuarios")
@SQLRestriction("ativo = true")
public class UsuarioEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(name = "email_verificado", nullable = false)
    private boolean isEmailVerificado = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime dataCriacao;

    @UpdateTimestamp
    private LocalDateTime dataAtualizacao;

    @Column(name = "ativo", nullable = false)
    private boolean isAtivo = true;

    @Column(name = "celular", unique = true, length = 20)
    private String celular;

    @Column(name = "foto")
    private byte[] foto;

    /**
     * Preferência de moeda do usuário (Real, Dólar, etc).
     * Valor padrão definido como BRL (Real).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "moeda", nullable = false)
    private MoedaEnum moeda = MoedaEnum.BRL;

    @Column(name = "chave_sessao")
    private String chaveSessao;

    @Column(name = "login_attempts", nullable = false)
    private Integer loginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "notificacoes_fatura_ativo", nullable = false)
    private boolean notificacoesFaturaAtivo = true;

    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isAtivo;
    }
}
