package com.catcheck.identity.infrastructure.config;

import java.util.Set;

/**
 * <b>DUONG CO SO</b> cho danh sach mat khau pho bien (p11 §11.3.5).
 *
 * <p>p11 OQ-3 <b>chua duoc chot</b>: nguon danh sach 10.000 mat khau co giay phep dung
 * la gi, ai bo sung ~200 mau tieng Viet Nam, quy trinh cap nhat ra sao. Vi vay A1
 * <b>khong bia 10.000 muc</b> — bia dan duoc se tao cam giac an toan gia ma khong co.</p>
 *
 * <p>Danh sach duoi day chi gom nhung mau ma p11 §11.3.5 <b>noi ro</b> la da nam trong
 * top 10k, cong mau tieng Viet Nam ma p11 yeu cau. Phan lon cac mau do duoc
 * {@link com.catcheck.identity.domain.PasswordPolicy} chan bang quy tac cau truc
 * (chuoi lap, chuoi tu/toan) nen khong can liet ke.</p>
 *
 * <p><b>Duong len danh sach day du hon</b> (khong doi code): dat file
 * {@code /common-passwords.txt} vao classpath, mot gia tri moi dong, {@code #} la
 * chu thich. {@link CommonPasswordListLoader} uu tien file do, chi dung class nay
 * khi khong co file. Khi OQ-3 chot, dua danh sach cua nguon duoc chot vao file va
 * xoa lop nay.</p>
 */
final class CommonPasswordBaseline {

    private CommonPasswordBaseline() {
    }

    static Set<String> words() {
        return Set.of(
                // --- Top-dien-bien (p11 §11.3.5 noi ro "da nam trong top 10k") ---
                "123456", "12345678", "123456789", "1234567890", "1234567", "12345", "1234",
                "password", "password1", "passw0rd",
                "iloveyou", "qwerty", "qwerty123", "abc123", "abcd1234", "letmein", "welcome",
                "monkey", "dragon", "sunshine", "princess", "football", "baseball", "master",
                "shadow", "superman", "trustno1", "starwars", "whatever", "freedom", "computer",
                "internet", "samsung", "michael", "jennifer", "jordan", "hunter", "thomas",
                "charlie", "daniel", "andrew", "joshua", "killer", "soccer", "hockey", "ranger",
                "buster", "thomas1", "robert", "batman", "test", "test123", "guest", "root",
                "admin", "admin123", "user", "login", "hello", "hello123", "default", "changeme",
                "secret", "summer", "winter", "spring", "autumn", "flower", "purple", "orange",
                "chocolate", "coffee", "cookie", "banana", "michael1", "jessica", "pepper",
                "killer1", "soccer1", "ashley", "nicole", "daniel1", "lovely", "lovely1",
                "tigger", "george", "access", "mustang", "matrix", "corvette", "camaro",

                // --- Chuoi lap / tuan tu ---
                "111111", "000000", "123123", "121212", "112233", "654321", "7654321",
                "aaaaaa", "abcdefg", "abcdefgh", "qwertyui", "asdfghjk", "zxcvbnm",

                // --- Mau tieng Viet Nam (p11 §11.3.5: "cac mau pho bien kieu Viet") ---
                "matkhau", "matkhau@123", "matkhau123", "matkhau1", "nhandeptrai", "nhatdeptrai",
                "nhatdepmeo", "anhdep", "emdep", "yeume", "yeuanh", "yeuem", "toilettroi",
                "dolaoyen", "dolao123", "aiden123", "vanhkhong", "muadien", "muadienro",
                "cuthonvai", "buonngu", "dolaodung", "tinhyeu", "xinhdep", "nhanvai", "quyen111",
                "phuongnam", "thaibinh", "hanoi1", "hcmc", "saigon1", "vietnam1", "vietnam123",
                "abcd@123", "123@456", "pass1234", "qwe123", "qwe1234", "asd123", "zxc123",
                "hoangminh", "nguyenvan", "tranthi", "lethanh", "phamthu", "dangquang",
                "baotran", "doanbich", "vuvanxuan", "dangthuy", "hoangmai");
    }
}
