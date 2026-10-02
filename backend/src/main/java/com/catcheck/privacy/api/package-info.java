/**
 * Bề mặt công khai (named interface "api") của module privacy — phần duy nhất module khác
 * được phép phụ thuộc vào (ngoài "spi", xem package privacy.spi). Ở M0 chưa có lớp nào trong
 * đây (chưa có nghiệp vụ thật), package tồn tại chỉ để named interface "api" được Spring
 * Modulith nhận diện.
 */
@org.springframework.modulith.NamedInterface("api")
package com.catcheck.privacy.api;
