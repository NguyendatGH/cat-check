package com.catcheck.scan.infrastructure.vision;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_java;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class OpenCvNativeLoader {

    private static final Logger log = LoggerFactory.getLogger(OpenCvNativeLoader.class);

    private volatile boolean available;

    @PostConstruct
    void load() {
        try {
            Loader.load(opencv_java.class);
            opencv_core.setNumThreads(1);
            available = true;
            log.info("OpenCV native da nap thanh cong");
        } catch (Throwable ex) {
            available = false;
            log.error("Khong nap duoc OpenCV native — pipeline se su dung nhanh S4b (khong the). "
                    + "Kiem tra glibc va classifier linux-x86_64.", ex);
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
