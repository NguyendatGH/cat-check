package com.catcheck.colorchart.api.dto;

public record BackfillAcceptedResponse(
        String chartId,
        String window,
        String statusUrl
) {
}
