package com.donutmarket.mod;

public record Position(
        String id,
        String item,
        double entry,
        double qty,
        String mode,
        long createdAt,
        double price,
        double filled,
        double profit,
        boolean priced) {

    public boolean perItem() {
        return "x1".equals(mode);
    }
}
