package com.donutmarket.mod;

public class Alert {
    public String item = "";
    public boolean auction = true;
    public boolean below = true;
    public double price;
    public long hitAt;
    public double hitPrice;

    public Alert() {
    }

    public Alert(String item, boolean auction, boolean below, double price) {
        this.item = item;
        this.auction = auction;
        this.below = below;
        this.price = price;
    }

    public boolean hit() {
        return hitAt > 0;
    }

    public String describe() {
        return (auction ? "AH" : "Order") + " " + (below ? "≤" : "≥") + " " + Money.format(price);
    }
}
