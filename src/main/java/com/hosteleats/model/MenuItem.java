package com.hosteleats.model;

public class MenuItem {
    private String id;
    private String name;
    private String category;
    private int price;
    private int satisfaction;
    private String emoji;
    private boolean popular;

    public MenuItem() {}

    public MenuItem(String id, String name, String category, int price, int satisfaction, String emoji, boolean popular) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.satisfaction = satisfaction;
        this.emoji = emoji;
        this.popular = popular;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getSatisfaction() {
        return satisfaction;
    }

    public void setSatisfaction(int satisfaction) {
        this.satisfaction = satisfaction;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public boolean isPopular() {
        return popular;
    }

    public void setPopular(boolean popular) {
        this.popular = popular;
    }
}
