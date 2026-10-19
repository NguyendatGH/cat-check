package com.catcheck.scan.domain.color;

import java.util.Optional;

public interface CardLayoutCatalog {

    Optional<CardLayout> findActiveLayout(String code);
}
