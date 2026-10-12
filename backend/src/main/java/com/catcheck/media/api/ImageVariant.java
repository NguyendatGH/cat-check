package com.catcheck.media.api;

public enum ImageVariant {

    THUMBNAIL("thumb"),

    PRINT("print");

    private final String suffix;

    ImageVariant(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }
}
