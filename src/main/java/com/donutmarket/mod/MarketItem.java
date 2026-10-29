package com.donutmarket.mod;

public record MarketItem(
        String key,
        int basis,
        double orders,
        double ahLow,
        double ahMedian,
        double usualOrders,
        double usualAh,
        double volume24,
        double orders7d,
        double[] spark,
        double[] sparkAh,
        long scannedAt) {

    public double ordersEach() {
        return orders / basis;
    }

    public double ahEach() {
        return ahLow / basis;
    }

    public double usualOrdersEach() {
        return usualOrders / basis;
    }

    public double usualAhEach() {
        double usual = usualAh > 0 ? usualAh : ahMedian;
        return usual / basis;
    }

    public double flipEach() {
        return flip() / basis;
    }

    public double flip() {
        if (orders <= 0 || ahLow <= 0) {
            return 0;
        }
        return ahLow - orders;
    }

    public double change7d() {
        if (orders7d <= 0 || orders <= 0) {
            return Double.NaN;
        }
        return (orders - orders7d) / orders7d * 100;
    }
}
