package com.catcheck.identity.application;

/**
 * Nguon cua thong tin request cho tang {@code application}.
 *
 * <p>Mot {@code application} service <b>khong duoc</b> nhan {@code HttpServletRequest}
 * (R2/R3: tang web va tang nghiep vu tach nhau), nhung audit lai can {@code ip_address},
 * {@code user_agent} va {@code request_id}. Record nay la cach dua ba thu tren xuong
 * nghiep vu ma van de su dung — controller tao no tu request, truyen vao command.</p>
 *
 * <p>Khong phai PII theo dinh nghia p17: IP la du lieu ca nhan, da duoc
 * {@code PiiRedactor} cua module {@code audit} che khi ghi vao log.</p>
 */
public record AuthRequestContext(String requestId, String ipAddress, String userAgent) {

    public static final AuthRequestContext UNKNOWN = new AuthRequestContext(null, null, null);

    public static AuthRequestContext of(String requestId, String ipAddress, String userAgent) {
        String requestIdValue = blankToNull(requestId);
        return new AuthRequestContext(
                requestIdValue,
                blankToNull(ipAddress),
                truncate(blankToNull(userAgent), 512));
    }

    /**
     * {@code user_agent} bi cat ngan 512 ky tu: client gui UA vo han khong nen
     * {@code user_device_session.user_agent} (VARCHAR 512) se khong ghi duoc. Cat o
     * day hon la de repository nem loi DB.
     */
    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
