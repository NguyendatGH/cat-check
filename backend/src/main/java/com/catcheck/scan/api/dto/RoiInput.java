package com.catcheck.scan.api.dto;

/** Vùng quan tâm chuẩn hoá {@code [0,1]} client gửi kèm (p6 §6.3.5). */
public record RoiInput(double x, double y, double w, double h) {
}
