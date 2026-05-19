package org.app_financeiro.backend.util;

public final class EmailMasker {

    private EmailMasker() {
    }

    public static String mascarar(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
