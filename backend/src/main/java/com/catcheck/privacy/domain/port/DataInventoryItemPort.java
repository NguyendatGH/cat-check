package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.DataInventoryItem;

import java.util.List;

/**
 * Cổng đọc {@code data_inventory_item} (p4 B10) — khối "Dữ liệu CatCheck đang giữ về bạn"
 * render ĐỘNG từ bảng này, không phải văn bản chép tay (p15 §15.4.3).
 */
public interface DataInventoryItemPort {

    /** Mọi mục {@code active} — sắp theo code (D1…D21 là thứ tự trong văn bản pháp lý). */
    List<DataInventoryItem> findAllActive();
}
