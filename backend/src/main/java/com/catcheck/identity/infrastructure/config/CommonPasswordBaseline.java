package com.catcheck.identity.infrastructure.config;

import java.util.Set;

final class CommonPasswordBaseline {

    private CommonPasswordBaseline() {
    }

    static Set<String> words() {
        return Set.of(
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

                "111111", "000000", "123123", "121212", "112233", "654321", "7654321",
                "aaaaaa", "abcdefg", "abcdefgh", "qwertyui", "asdfghjk", "zxcvbnm",

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
