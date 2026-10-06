package com.catcheck.shared.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/**
 * Than cua L73 {@code POST /api/v1/client-errors} — p8 §8.4.12(f) chot <b>dung nam truong</b>:
 * {@code {message, stack, appVersion, traceId, route}}.
 *
 * <p><b>{@code ignoreUnknown = true} la cach thuc thi cau "tuyet doi khong nhan gia tri input,
 * query string hay noi dung form" cua p8.</b> Mot {@code Map<String, Object>} hay mot truong
 * {@code extra} se bien endpoint nay thanh dung cai kenh ro du lieu ma p16 §16.6.7 canh bao
 * ("neu khong, telemetry tro thanh kenh ro du lieu lon nhat cua san pham"). Bo truong la ngay o
 * tang parse manh hon moi bo loc phia sau: thu khong ton tai thi khong the log.</p>
 *
 * <p><b>Khong co {@code @NotNull} tren truong nao</b> va <b>khong co kieu nguyen thuy</b>
 * (H15.99): p8 chot L73 tra {@code 202} khong body, nen mot bao cao thieu truong phai duoc
 * nhan va ghi phan doc duoc, chu khong tra {@code 400} — lan deploy lam vo bundle la dung luc
 * frontend it co kha nang gui du truong nhat.</p>
 *
 * <p>Tran do dai tung truong o day la <b>lop bao ve thu hai</b> sau tran 8 KB cho ca than
 * request: mot {@code stack} dai 1 MB nam trong mot request 7 KB thi khong the, nhung mot
 * {@code message} 8 KB thi co — va mot dong log 8 KB lam vo moi cong cu doc log.</p>
 *
 * @param traceId   p16 §16.6.7 bat buoc gan, lay tu header response gan nhat — day la thu noi
 *                  stack trace cua frontend voi dong log cua backend
 * @param route     route logic cua p9 §9.4, <b>khong</b> ke query string; controller cat query
 *                  neu client van gui
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientErrorRequest(
        @Size(max = 2_000) String message,
        @Size(max = 6_000) String stack,
        @Size(max = 64) String appVersion,
        @Size(max = 64) String traceId,
        @Size(max = 512) String route) {
}
