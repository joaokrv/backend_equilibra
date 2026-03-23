package org.app_financeiro.backend.config;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Decorator para PasswordEncoder que adiciona um pepper à senha bruta
 * antes de realizar as operações de encode ou matches.
 * Isso garante que a lógica de pepper seja aplicada uniformemente
 * tanto no registro (Service) quanto na autenticação (AuthenticationManager).
 */
public class PepperedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final String pepper;

    public PepperedPasswordEncoder(PasswordEncoder delegate, String pepper) {
        this.delegate = delegate;
        this.pepper = pepper != null ? pepper : "";
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(pepper + rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return delegate.matches(pepper + rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }
}
