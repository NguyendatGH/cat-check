package com.catcheck.place.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.FutureOrPresent;

import java.time.LocalDate;

public record PlaceBookingRequest(@NotBlank @Size(max = 48) String serviceCode,
                                  @NotNull @FutureOrPresent LocalDate date,
                                  @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String timeSlot,
                                  @Size(max = 1000) String note) { }
