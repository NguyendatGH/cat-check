package com.catcheck.shared.application;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Loc chuoi truoc khi ghi log cho hai endpoint nhan du lieu tu trinh duyet — K1
 * {@code POST /csp-report} va L73 {@code POST /client-errors}.
 *
 * <p><b>Day la lop tuan thu, khong phai tien ich dinh dang.</b> p16 §16.6.7 doi
 * {@code beforeSend} "xoa moi gia tri input, URL query, va bat ky truong nao khop bang
 * §16.6.3"; p16 §16.6.3 goi bang do la "hop dong tuan thu, khong phai khuyen nghi" va noi ro
 * vi pham la <b>su co du lieu ca nhan</b> theo p15. CLAUDE.md nhac lai o muc "Hard rules":
 * khong log PII.</p>
 *
 * <p><b>Vi sao loc o SERVER du p9 da loc o client:</b> than request den tu trinh duyet cua
 * nguoi dung — mot bundle cu chua kip deploy, mot ban dich nguoc, hay don gian la mot ke goi
 * thang bang curl (hai endpoint nay cong khai) deu gui duoc bat ky gi. Tin vao
 * {@code beforeSend} cua frontend nghia la bien kenh telemetry thanh "kenh ro du lieu lon nhat
 * cua san pham" dung nhu p16 canh bao. Loc hai lan la co y.</p>
 *
 * <p><b>Nam o {@code ..application..} chu khong o {@code ..domain..}</b> vi no la quy tac xu ly
 * dau vao cua mot use case, khong phai mot khai niem nghiep vu; va <b>khong</b> o
 * {@code ..infrastructure..} vi R3 cam {@code ..api..} (noi hai controller nam) phu thuoc
 * {@code ..infrastructure..}.</p>
 *
 * <p><b>Luu y R16:</b> class nay co y <b>khong co logger</b> va <b>khong co field</b> nao ten
 * khop regex PII cua rule R16 ({@code password|otp|token|secret|activationCode|fid|apiKey}).
 * Viec ghi log la cua controller; o day chi bien doi chuoi.</p>
 */
public final class TelemetryScrubber {

    /** Gia tri thay the. Giu dau hieu "co gi da bi loc" de nguoi doc log khong tuong la mat du lieu. */
    public static final String REDACTED = "[redacted]";

    /**
     * Email day du — §16.6.3 dong 1. Khong che thanh {@code n***@gmail.com} nhu bang goi y:
     * o day khong co ngu canh nao can biet dinh dang email, va mot ban che vung ve van la mot
     * phan email that trong log dai han.
     */
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");

    /**
     * Day 6 chu so dung rieng — ma OTP/TOTP (§16.6.3 dong 3). Bien gioi {@code (?<!\d)} /
     * {@code (?!\d)} de khong cat mot so dai hon (vi du so dong trong stack trace, hay epoch
     * millis) thanh hai nua vo nghia.
     */
    private static final Pattern SIX_DIGIT_CODE = Pattern.compile("(?<!\\d)\\d{6}(?!\\d)");

    /**
     * So dien thoai Viet Nam (§16.6.3 dong 9). Bat ca dang {@code 0xxxxxxxxx} va
     * {@code +84xxxxxxxxx}, cho phep dau cach/gach noi o giua — do la cach nguoi dung that go.
     */
    private static final Pattern PHONE_VN = Pattern.compile(
            "(?<![\\dA-Za-z])(?:\\+?84|0)[\\s.\\-]?\\d{2}[\\s.\\-]?\\d{3,4}[\\s.\\-]?\\d{3,4}(?![\\dA-Za-z])");

    /**
     * Chuoi base64/hex dai — anh base64, session id, JWT, FCM registration id (§16.6.3 dong 5,
     * 7). Nguong 40 ky tu: dai hon moi ten ham/lop thuc te trong mot stack trace, nhung ngan
     * hon mot payload that nen khong bo sot.
     */
    private static final Pattern LONG_OPAQUE_BLOB = Pattern.compile("[A-Za-z0-9+/_=\\-]{40,}");

    /**
     * JWT — ba doan base64url ngan cach bang dau cham, luon bat dau bang {@code eyJ}
     * (base64 cua <code>{"</code>).
     *
     * <p><b>Can mot pattern rieng chu khong noi {@link #LONG_OPAQUE_BLOB}:</b> dau cham chia JWT
     * thanh ba doan, va tung doan thuong NGAN hon nguong 40 ky tu (header mac dinh
     * {@code {"alg":"HS256","typ":"JWT"}} chi ra 36 ky tu base64) nen ca token thoat duoc. Con
     * them {@code '.'} vao tap ky tu cua {@code LONG_OPAQUE_BLOB} thi moi ten lop day du trong
     * stack trace ({@code com.catcheck.scan.application.SubmitScanService} — 46 ky tu) cung bi
     * che, tuc la pha huy chinh thu khien stack trace co ich. Neo vao {@code eyJ} chinh xac hon
     * ca hai cach.</p>
     */
    private static final Pattern JWT_LIKE = Pattern.compile(
            "eyJ[A-Za-z0-9+/_=\\-]{8,}\\.[A-Za-z0-9+/_=\\-]{8,}(?:\\.[A-Za-z0-9+/_=\\-]+)?");

    /**
     * {@code Authorization: Bearer <...>} va cac dang khong co dau {@code =}.
     *
     * <p>{@link #SENSITIVE_PAIR} chi bat {@code key=value}/{@code key:value}; mot header that
     * viet la {@code bearer <token>} — co dau cach, khong co dau bang — nen no lot qua.</p>
     */
    private static final Pattern BEARER_VALUE = Pattern.compile(
            "(?i)\\b(bearer|basic|token)\\s+[A-Za-z0-9+/_=.\\-]{8,}");

    /** {@code key=value} voi key nhay cam, trong query string hoac trong text. */
    private static final Pattern SENSITIVE_PAIR = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|otp|code|credential|authorization|auth|jwt|bearer"
                    + "|session|sessionid|csrf|xsrf|activation|activationcode|apikey|api_key"
                    + "|accesstoken|access_token|refreshtoken|refresh_token|secret|token|fid"
                    + "|email|phone|fullname|full_name)\\b\\s*[=:]\\s*[^\\s&;,)\\]}\"']+");

    /** Du lieu nhung truc tiep trong URL — {@code data:}/{@code blob:} chua noi dung anh. */
    private static final Pattern INLINE_DATA_URI = Pattern.compile("(?i)\\b(data|blob):[^\\s\"')]+");

    private static final int MAX_URI_LENGTH = 512;

    private TelemetryScrubber() {
    }

    /**
     * Loc mot doan van ban tu do (message loi, stack trace, {@code sourceFile}...).
     *
     * @param raw     gia tri nhu client gui; {@code null}/rong tra {@code null}
     * @param maxChars tran do dai sau khi loc; {@code 0} hoac am = khong cat
     * @return chuoi da loc, hoac {@code null} neu dau vao rong
     */
    public static String scrubText(String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip();
        // Thu tu CO Y: cat cap key=value truoc, de phan "value" khong con co hoi
        // khop mot pattern long hon va chi bi che mot phan.
        out = SENSITIVE_PAIR.matcher(out).replaceAll(match -> match.group(1) + "=" + REDACTED);
        out = BEARER_VALUE.matcher(out).replaceAll(match -> match.group(1) + " " + REDACTED);
        out = JWT_LIKE.matcher(out).replaceAll(REDACTED);
        out = INLINE_DATA_URI.matcher(out).replaceAll(match -> match.group(1) + ":" + REDACTED);
        out = EMAIL.matcher(out).replaceAll(REDACTED);
        out = PHONE_VN.matcher(out).replaceAll(REDACTED);
        out = LONG_OPAQUE_BLOB.matcher(out).replaceAll(REDACTED);
        out = SIX_DIGIT_CODE.matcher(out).replaceAll(REDACTED);
        // Ky tu dieu khien (xuong dong, tab, ESC) bi thay bang dau cach: mot
        // dong log JSON phai la MOT dong, va chuoi ESC co the gia mao dau vet log.
        out = out.replaceAll("[\\p{Cntrl}]+", " ").replaceAll(" {2,}", " ").strip();
        if (maxChars > 0 && out.length() > maxChars) {
            out = out.substring(0, maxChars) + "…";
        }
        return out.isBlank() ? null : out;
    }

    /**
     * Loc mot URL/route: <b>bo hoan toan query string va fragment</b>, chi giu path.
     *
     * <p>p16 §16.6.7 liet URL query vao dung mot nhom voi "gia tri input" va "breadcrumb chua
     * noi dung form". Day khong phai lo xa: {@code /auth/reset-password?token=...},
     * {@code /scan?catId=...} va bat ky form GET nao deu dat du lieu that vao query. Cat ca
     * query thay vi loc tung tham so la lua chon co y — danh sach tham so an toan se loi thoi
     * ngay lan them man hinh ke tiep, con "khong co query" thi khong loi thoi bao gio.</p>
     */
    public static String scrubUri(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip();
        int cut = indexOfFirst(out, '?', '#', ';');
        if (cut >= 0) {
            out = out.substring(0, cut);
        }
        // Van chay scrubText: path cung chua PII duoc (vi du /users/a@b.com), va mot
        // "path" do client tu dat co the khong phai path that.
        return scrubText(out, MAX_URI_LENGTH);
    }

    /**
     * Chuan hoa ten enum/ma ky thuat: chi giu chu, so, dau gach, dau cham. Dung cho
     * {@code violatedDirective}, {@code disposition}, {@code appVersion} — nhung truong dang le
     * la hang so, nen moi ky tu ngoai tap nay la dau hieu client dang nhoi du lieu khac vao.
     */
    public static String scrubToken(String raw, int maxChars) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip().replaceAll("[^A-Za-z0-9._\\-+:/ ]", "");
        if (maxChars > 0 && out.length() > maxChars) {
            out = out.substring(0, maxChars);
        }
        return out.isBlank() ? null : out;
    }

    /**
     * Rut goc cua mot URI ve dang {@code scheme://host} — dung cho {@code blockedUri} cua bao
     * cao CSP, noi thong tin can cho nguoi van hanh la "domain nao bi chan", khong phai duong
     * dan day du (duong dan day du cua mot resource bi chan co the chua id nguoi dung).
     */
    public static String scrubOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String out = raw.strip();
        // Gia tri tu khoa cua CSP: "inline", "eval", "self", "data", "blob"...
        if (!out.contains("//")) {
            return scrubToken(out, 64);
        }
        int schemeEnd = out.indexOf("//") + 2;
        int pathStart = indexOfFrom(out, schemeEnd, '/', '?', '#');
        String origin = pathStart < 0 ? out : out.substring(0, pathStart);
        return scrubToken(origin.toLowerCase(Locale.ROOT), 160);
    }

    private static int indexOfFirst(String value, char... chars) {
        return indexOfFrom(value, 0, chars);
    }

    private static int indexOfFrom(String value, int from, char... chars) {
        int best = -1;
        for (char candidate : chars) {
            int at = value.indexOf(candidate, from);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return best;
    }
}
