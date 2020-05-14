package de.metaphisto.buoy.shop;

import java.io.Serializable;

/**
 *
 */
public class OrderPosition implements Serializable{

    private String offer;

    private double price;

    public OrderPosition(String offer, double price) {
        this.offer = offer;
        this.price = price;
    }

    public String getOffer() {
        return offer;
    }

    public void setOffer(String offer) {
        this.offer = offer;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
