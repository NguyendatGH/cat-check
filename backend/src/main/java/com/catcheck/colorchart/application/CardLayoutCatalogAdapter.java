package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ReferenceCardLayout;
import com.catcheck.colorchart.domain.port.ReferenceCardLayoutRepository;
import com.catcheck.scan.domain.color.CardLayout;
import com.catcheck.scan.domain.color.CardLayoutCatalog;
import com.catcheck.scan.domain.color.Lab;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CardLayoutCatalogAdapter implements CardLayoutCatalog {

    private final ReferenceCardLayoutRepository repository;

    public CardLayoutCatalogAdapter(ReferenceCardLayoutRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CardLayout> findActiveLayout(String code) {
        return repository.findActiveByCode(code).map(this::toCardLayout);
    }

    @SuppressWarnings("unchecked")
    private CardLayout toCardLayout(ReferenceCardLayout entity) {
        Map<String, Object> layout = entity.getLayout();
        Map<String, Object> canonicalPx = (Map<String, Object>) layout.get("canonical_px");
        int width = ((Number) canonicalPx.get("w")).intValue();
        int height = ((Number) canonicalPx.get("h")).intValue();

        List<Map<String, Object>> rawPatches = (List<Map<String, Object>>) layout.get("patches");
        List<CardLayout.Patch> patches = new ArrayList<>(rawPatches.size());
        for (Map<String, Object> raw : rawPatches) {
            patches.add(toPatch(raw));
        }
        return new CardLayout(entity.getCode(), entity.getVersion(), width, height, patches);
    }

    @SuppressWarnings("unchecked")
    private CardLayout.Patch toPatch(Map<String, Object> raw) {
        String id = (String) raw.get("id");
        List<Number> rect = (List<Number>) raw.get("rect");
        CardLayout.Role role = CardLayout.Role.valueOf((String) raw.get("role"));
        List<Number> refLab = (List<Number>) raw.get("ref_lab");
        Lab lab = new Lab(refLab.get(0).doubleValue(), refLab.get(1).doubleValue(), refLab.get(2).doubleValue());
        return new CardLayout.Patch(id,
                rect.get(0).doubleValue(), rect.get(1).doubleValue(),
                rect.get(2).doubleValue(), rect.get(3).doubleValue(),
                role, lab);
    }
}
