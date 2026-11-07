package net.onelitefeather.menura.server.prototype;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Half-up rounding for the numbers the API shows. */
final class Rounding {

    private Rounding() {
    }

    static double to(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }
}
