package org.app_financeiro.backend.dto.projections;

import java.math.BigDecimal;

public record DividaCartaoProjection(
    Long cartaoId,
    BigDecimal totalDivida
) {}
