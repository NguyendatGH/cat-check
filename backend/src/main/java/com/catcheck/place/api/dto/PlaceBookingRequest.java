package com.catcheck.place.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PlaceBookingRequest(@NotBlank String serviceCode, @NotNull LocalDate date,
                                  @NotBlank String timeSlot, @Size(max = 1000) String note) { }
