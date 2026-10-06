package com.catcheck.shared.application.spi;

/**
 * Dem gioi han cho hai endpoint <b>cong khai</b> nhan du lieu tu trinh duyet — K1
 * {@code POST /csp-report} (60/phut/IP, p8 §8.4.11) va L73 {@code POST /client-errors}
 * (10/phut/IP, p8 §8.4.12).
 *
 * <p><b>Vi sao khong dung lai {@code identity.domain.port.RateLimiter}:</b> cong do thuoc
 * module {@code identity} va danh muc quy tac cua no ({@code RateLimitRule}) la ban chep bang
 * p11 §11.7.2 — mot bang khong he chua hai endpoint nay. Module {@code shared} khai
 * {@code allowedDependencies = {}} nen khong duoc import {@code identity}, va chieu nguoc lai
 * ({@code identity} them hai quy tac ho {@code shared}) se dat han muc cua endpoint
 * {@code shared} vao mot enum ma {@code shared} khong doc duoc. Hai cai dat deu la Bucket4j +
 * Caffeine in-memory theo dung p11 §11.7.1, nen khong co su that nao bi nhan doi — chi co hai
 * danh muc quy tac cho hai mien.</p>
 *
 * <p><b>Khoa theo IP la ngoai le co can cu.</b> p11 §11.7.1 noi chung uu tien
 * {@code email}/{@code userId} va coi IP la lop phu, vi CGNAT o Viet Nam khien hang nghin thue
 * bao dung chung mot IPv4. Nhung hai endpoint nay <b>khong co nguoi dung</b>: p8 ghi ro L73
 * phai cong khai vi "loi tai chunk xay ra TRUOC khi co phien", va bao cao CSP do trinh duyet
 * gui truoc moi thao tac dang nhap. IP la khoa duy nhat ton tai — va dung chinh han muc ma p8
 * da chot cho tung endpoint.</p>
 */
public interface PublicIngressRateLimiter {

    /** Han muc da chot o p8 cho tung endpoint cong khai. */
    enum Limit {

        /** p8 K1 — 60/phut/IP. Vuot han muc <b>van tra 204</b>, chi khong xu ly tiep. */
        CSP_REPORT(60),

        /** p8 L73 — 10/phut/IP. */
        CLIENT_ERROR(10);

        private final int perMinute;

        Limit(int perMinute) {
            this.perMinute = perMinute;
        }

        public int perMinute() {
            return perMinute;
        }
    }

    /**
     * Tieu mot don vi cua {@code limit} cho {@code clientIp}.
     *
     * @param clientIp IP client; {@code null}/rong thi <b>cho qua</b> — giong hop dong cua
     *                 {@code identity.domain.port.RateLimiter}: khong co khoa thi khong dem,
     *                 chu khong chan oan mot request khong xac dinh duoc nguon
     * @return {@code true} neu duoc phep xu ly
     */
    boolean tryConsume(Limit limit, String clientIp);

    /**
     * So giay client phai cho truoc khi thu lai — dung cho header {@code Retry-After}, ma
     * p8 §8.3.5(d) bat buoc 100% khi tra {@code 429} (p16 NFR-40).
     *
     * @return {@code 0} neu khong bi chan
     */
    long secondsUntilRefill(Limit limit, String clientIp);
}
