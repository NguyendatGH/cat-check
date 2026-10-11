package com.catcheck.place.domain.port;

import com.catcheck.place.domain.ExternalPlace;

import java.util.List;

/** Nguồn cơ sở bên ngoài (hiện là OpenStreetMap) quanh một toạ độ — hiện thực nằm ở infrastructure. */
public interface NearbyPlaceSource {
    /** Giới hạn bán kính tra cứu (mét); API từ chối vượt trần thay vì kẹp âm thầm. */
    int MIN_RADIUS_METERS = 500;
    int MAX_RADIUS_METERS = 25_000;

    /** @throws RuntimeException khi nguồn ngoài không trả lời được (và không có bản cache dùng lại). */
    List<ExternalPlace> nearby(double latitude, double longitude, long radiusMeters);
}
