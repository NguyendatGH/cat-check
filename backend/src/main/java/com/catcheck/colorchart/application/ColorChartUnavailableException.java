package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ProductLine;

/**
 * Không có bảng màu {@code ACTIVE} cho (dòng sản phẩm, lô) — scan phải trả
 * {@code SCAN_CHART_UNAVAILABLE} (p8 §8.2.4(e)).
 */
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
