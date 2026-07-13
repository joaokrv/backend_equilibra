package org.app_financeiro.backend.util;

import java.text.Normalizer;

/** Normalização de texto para comparações tolerantes a acentos/caixa (classificação e matching da importação). */
public final class TextoUtil {

    private TextoUtil() {}

    /** Minúsculas sem acentos — "Aplicação" e "aplicacao" comparam iguais. */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .strip();
    }
}
