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

/**
 * Chặn khởi động ở prod khi bất kỳ bảng màu đang {@code ACTIVE} là placeholder
 * (docs/scan-production-plan.md GĐ7 — "kiểm tra khởi động cấm bảng màu placeholder ở prod").
 *
 * <p>Chỉ chạy ở profile {@code prod} ({@code @Profile("prod")}) — không ảnh hưởng local/staging/
 * test. {@link ApplicationRunner} chạy SAU khi context đã khởi tạo xong (DB sẵn sàng), và ném
 * ngoại lệ ở đây khiến {@code SpringApplication.run(...)} fail-fast, đúng yêu cầu "không để app
 * chạy production với bảng giả".</p>
 *
 * <p>Dùng lại {@link ColorChartRepository#findAllByStatus} (đã có sẵn, phục vụ trang quản trị)
 * thay vì viết truy vấn mới — duyệt TOÀN BỘ bảng {@code status = ACTIVE} (mọi dòng sản phẩm/lô,
 * không chỉ bản mặc định): một placeholder còn ACTIVE ở bất kỳ lô nào cũng phải chặn khởi động.</p>
 *
 * <p>Không log PII — thông điệp chỉ nêu {@code code}/{@code version} của bảng màu, đây là tài
 * sản hiệu chuẩn nội bộ (p4 D5), không phải dữ liệu người dùng.</p>
 */
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
