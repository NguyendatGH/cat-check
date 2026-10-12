package com.catcheck.content.application;

import com.catcheck.content.api.ContentErrorCode;
import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.port.CareTipRepository;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CareTipQueryService {

    private final CareTipRepository careTipRepository;

    public CareTipQueryService(CareTipRepository careTipRepository) {
        this.careTipRepository = careTipRepository;
    }

    public List<CareTip> listPublished(String locale, CareTipCategory category, CareTipKind kind, int limit) {
        return careTipRepository.findPublished(locale, category, kind, limit);
    }

    public CareTip getPublishedBySlug(String slug, String locale) {
        return careTipRepository.findBySlugAndLocale(slug, locale)
                .filter(CareTip::isPublished)
                .orElseThrow(() -> new NotFoundException(ContentErrorCode.CONTENT_NOT_FOUND));
    }
}
