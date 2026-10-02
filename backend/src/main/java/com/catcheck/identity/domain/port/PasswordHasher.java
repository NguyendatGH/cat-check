package com.catcheck.identity.domain.port;

/**
 * Bam va kiem tra mat khau (p11 S4: BCrypt).
 *
 * <p>La CONG chu: {@code spring-security-crypto} nam o {@code ..infrastructure..} nen
 * {@code ..application..} khong duoc import no (R2).</p>
 */
public interface PasswordHasher {

    /**
     * Bam mat khau de luu. Ket qua luon co prefix {@code {bcrypt}} de
     * {@code DelegatingPasswordEncoder} doi thuat toan duoc sau ma khong phai migrate
     * lai toan bo hang.
     */
    String hash(String rawPassword);

    /**
     * Kiem tra mat khau co khop hash da luu khong.
     *
     * <p><b>Phai chay du thoi gian BCrypt du hay la</b>: p11 §11.1.5 yeu cau ca hai nhanh
     * (tim thay tai khoan / khong tim thay) ton tai cung thoi gian phan hoi de khong lo
     * doi kiem chung tai khoan ton tai. Do do {@link #matches} tren tai khoan khong ton tai
     * phai duoc goi voi <b>hash gia</b>, xem {@link #dummyHash()}.</p>
     */
    boolean matches(String rawPassword, String storedHash);

    /**
     * Hash gia dung khi khong tim thay tai khoan, de nhanh do phan hoi cua
     * {@code POST /auth/login} khong lo bat duoc.
     */
    String dummyHash();
}
