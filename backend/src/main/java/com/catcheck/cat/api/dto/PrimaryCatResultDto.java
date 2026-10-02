package com.catcheck.cat.api.dto;

public record PrimaryCatResultDto(
        String catId,
        String previousPrimaryCatId
) {
}
