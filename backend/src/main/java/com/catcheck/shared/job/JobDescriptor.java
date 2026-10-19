package com.catcheck.shared.job;

import java.time.ZoneId;

public record JobDescriptor(String name, String cron, ZoneId zone, boolean enabled) {

    public JobDescriptor {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("jobDescriptor.name phải có giá trị");
        }
        if (name.length() > 64) {
            throw new IllegalArgumentException("jobDescriptor.name dài hơn 64 ký tự: " + name);
        }
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("jobDescriptor.cron phải có giá trị: " + name);
        }
        if (zone == null) {
            throw new IllegalArgumentException("jobDescriptor.zone phải có giá trị: " + name);
        }
    }
}
