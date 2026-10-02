package com.catcheck.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * R5 — cần quét CẢ test sources (khác với các rule khác trong package này chỉ quét main) vì
 * quy tắc cho phép ngoại lệ "chính test của controller đó" (ví dụ
 * {@code SystemStatusControllerTest} được phép phụ thuộc {@code SystemStatusController}, còn
 * lớp khác thì không) — ngoại lệ này chỉ có ý nghĩa khi lớp test thật sự nằm trong tập được
 * phân tích. Ở M0 chưa có class nào (kể cả test) phụ thuộc *Controller nên rule PASS.
 */
@AnalyzeClasses(packages = "com.catcheck")
class ControllerDependencyRuleTests {

    @ArchTest
    static final ArchRule r5_onlyOwnTestMayDependOnAController = classes()
            .should(notDependOnAControllerExceptItsOwnTest())
            .because("R5: không class nào phụ thuộc một class tên kết thúc 'Controller', trừ chính test của nó");

    private static ArchCondition<JavaClass> notDependOnAControllerExceptItsOwnTest() {
        return new ArchCondition<JavaClass>("not depend on a *Controller class other than its own test") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    // Chỉ tính *Controller do CHÍNH CatCheck viết (package com.catcheck..) — nếu chỉ
                    // so tên, `@RestController`/`@Controller` của Spring (org.springframework..) cũng
                    // khớp ".*Controller" và mọi lớp mang annotation đó (kể cả chính nó qua bytecode
                    // annotation reference) sẽ bị báo vi phạm R5 dù không hề phụ thuộc controller nào
                    // khác — phát hiện thật bằng mvn test (build FAILURE trên SystemStatusController).
                    if (item.equals(target)
                            || !target.getSimpleName().endsWith("Controller")
                            || !target.getPackageName().startsWith("com.catcheck")) {
                        continue;
                    }
                    boolean isOwnTest = item.getSimpleName().equals(target.getSimpleName() + "Test")
                            || item.getSimpleName().equals(target.getSimpleName() + "Tests")
                            || item.getSimpleName().equals(target.getSimpleName() + "IT");
                    if (!isOwnTest) {
                        events.add(SimpleConditionEvent.violated(dependency,
                                dependency.getDescription() + " -> vi phạm R5 (chỉ test riêng của controller được phụ thuộc nó)"));
                    }
                }
            }
        };
    }
}
