package com.catcheck.place.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.FutureOrPresent;

import java.time.LocalDate;

/**
 * {@code timeSlot} phải đúng dạng {@code HH:mm} (giá trị của {@code <input type="time">}):
 * {@code uq_place_booking_slot} so chuỗi nguyên văn, nên "9:00" và "09:00" sẽ là hai khung khác
 * nhau. Giới hạn độ dài khớp cột {@code VARCHAR} của V19 để lỗi là 400, không phải 500 từ DB.
 */
public record PlaceBookingRequest(@NotBlank @Size(max = 48) String serviceCode,
                                  @NotNull @FutureOrPresent LocalDate date,
                                  @NotNull @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String timeSlot,
                                  @Size(max = 1000) String note) { }
