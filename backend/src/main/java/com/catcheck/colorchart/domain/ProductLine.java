package com.catcheck.colorchart.domain;

/**
 * Dòng sản phẩm cát vệ sinh (p4 D5 {@code product_line}, §4.4.3 VARCHAR + CHECK).
 *
 * <p>Tỉ lệ hạt chỉ thị khác nhau giữa các gói nên bảng màu phải gắn theo dòng. Giá trị trên dây
 * giống hệt giá trị trong DB (p8 §8.1.2).
 */
public enum ProductLine {
    MINI,
    STANDARD,
    PLUS
}
