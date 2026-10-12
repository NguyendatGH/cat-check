package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.Size;

public record DisputeRequest(@Size(max = 500) String note) {
}
