package br.jovemtech.itaqui.calculo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Operações com valores que podem ser nulos (nulo = "não se aplica", ex.: realizado de mês aberto). */
final class Valores {

    static final MathContext MC = MathContext.DECIMAL128;

    private Valores() {
    }

    static BigDecimal soma(BigDecimal a, BigDecimal b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.add(b, MC);
    }

    static BigDecimal subtrai(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? BigDecimal.ZERO : a).subtract(b == null ? BigDecimal.ZERO : b, MC);
    }

    /** Arredonda para 2 casas como o ARRED do Excel; só na saída. */
    static BigDecimal r2(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    /** a / b × 100, ou nulo se b for zero/nulo. */
    static BigDecimal percentual(BigDecimal a, BigDecimal b) {
        if (a == null || b == null || b.signum() == 0) {
            return null;
        }
        return a.multiply(BigDecimal.valueOf(100), MC).divide(b, MC);
    }
}
