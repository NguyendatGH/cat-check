package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ProductLine;

public class ColorChartUnavailableException extends RuntimeException {

    private final ProductLine productLine;
    private final String productionBatch;

    public ColorChartUnavailableException(ProductLine productLine, String productionBatch) {
        super("Khong co bang mau ACTIVE cho productLine=" + productLine + ", batch=" + productionBatch);
        this.productLine = productLine;
        this.productionBatch = productionBatch;
    }

    public ProductLine getProductLine() {
        return productLine;
    }

    public String getProductionBatch() {
        return productionBatch;
    }
}
