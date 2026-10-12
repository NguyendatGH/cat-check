package com.catcheck.scan.domain.color;

import java.util.List;

public record CardLayout(String code, int version, int canonicalWidth, int canonicalHeight, List<Patch> patches) {

    public CardLayout {
        if (patches == null || patches.isEmpty()) {
            throw new IllegalArgumentException("Layout the phai co it nhat mot patch");
        }
        patches = List.copyOf(patches);
    }

    public enum Role {
        NEUTRAL,
        COLOR,
        PH_LEVEL
    }

    public record Patch(String id, double x, double y, double w, double h, Role role, Lab refLab) {
    }

    public List<Patch> neutralPatches() {
        return patches.stream().filter(p -> p.role() == Role.NEUTRAL).toList();
    }
}
