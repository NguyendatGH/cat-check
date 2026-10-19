package com.catcheck.colorchart.infrastructure.config;

import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("prod")
public class ActiveChartPlaceholderGuard implements ApplicationRunner {

    private static final int PAGE_SIZE = 100;

    private final ColorChartRepository colorChartRepository;

    public ActiveChartPlaceholderGuard(ColorChartRepository colorChartRepository) {
        this.colorChartRepository = colorChartRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> placeholders = findActivePlaceholders();
        if (!placeholders.isEmpty()) {
            throw new IllegalStateException(
                    "Khong the khoi dong o profile prod: bang mau dang ACTIVE la PLACEHOLDER ("
                            + String.join(", ", placeholders)
                            + "). Phai publish bang mau that (is_placeholder=false) truoc khi chay production.");
        }
    }

    private List<String> findActivePlaceholders() {
        List<String> found = new ArrayList<>();
        int page = 0;
        PageResult<ColorChart> result;
        do {
            result = colorChartRepository.findAllByStatus(ChartStatus.ACTIVE, page, PAGE_SIZE);
            for (ColorChart chart : result.items()) {
                if (chart.isPlaceholder()) {
                    found.add(chart.getCode() + " v" + chart.getVersion());
                }
            }
            page++;
        } while (result.hasMore());
        return found;
    }
}
