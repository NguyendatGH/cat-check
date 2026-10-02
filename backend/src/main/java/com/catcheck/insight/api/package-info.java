/**
 * Bề mặt công khai (named interface "api") của module insight — phần duy nhất module khác
 * được phép phụ thuộc vào. Ở M0 chưa có lớp nào trong đây (chưa có nghiệp vụ thật), package
 * tồn tại chỉ để named interface "api" được Spring Modulith nhận diện.
 */
@org.springframework.modulith.NamedInterface("api")
package com.catcheck.insight.api;
