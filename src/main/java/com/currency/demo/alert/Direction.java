package com.currency.demo.alert;

import java.math.BigDecimal;

/** Which side of the threshold fires the alert. Equal to the threshold never fires. */
public enum Direction {
    ABOVE {
        @Override
        public boolean isMet(BigDecimal price, BigDecimal threshold) {
            return price.compareTo(threshold) > 0;
        }
    },
    BELOW {
        @Override
        public boolean isMet(BigDecimal price, BigDecimal threshold) {
            return price.compareTo(threshold) < 0;
        }
    };

    public abstract boolean isMet(BigDecimal price, BigDecimal threshold);
}
