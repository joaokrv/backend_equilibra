package org.app_financeiro.backend.config;

import org.springframework.security.crypto.password.PasswordEncoder;

/** Decorator que aplica pepper antes de encode/matches — uniforme em registro e auth. */
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
