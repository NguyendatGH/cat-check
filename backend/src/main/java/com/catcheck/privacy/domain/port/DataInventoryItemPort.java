package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.DataInventoryItem;

import java.util.List;

public interface DataInventoryItemPort {

    List<DataInventoryItem> findAllActive();
}
