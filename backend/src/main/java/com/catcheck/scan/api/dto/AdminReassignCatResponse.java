package com.catcheck.scan.api.dto;

public record AdminReassignCatResponse(
        String scanId,
        String fromCatId,
        String toCatId,
        String toAssignment,
        short reassignCount
) {
}
