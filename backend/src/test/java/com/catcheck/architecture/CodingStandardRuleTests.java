package com.catcheck.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * R8, R11, R12, R13, R15, R16 — quy tắc coding standard chung, không gắn với biên module. Chỉ
 * quét main sources (test class được phép field-injection kiểu Mockito @Mock/@InjectMocks,
 * @Autowired trong @SpringBootTest...).
 */
@AnalyzeClasses(packages = "com.catcheck", importOptions = ImportOption.DoNotIncludeTests.class)
class CodingStandardRuleTests {

    private static final Pattern PII_NAME_PATTERN =
            Pattern.compile("(?i).*(password|otp|token|secret|activationCode|fid|apiKey).*");

    @ArchTest
    static final ArchRule r8_domainStaysFrameworkFree = classes()
            .that().resideInAPackage("..domain..")
            .should(notUseSpringAnnotations())
            .because("R8: class trong ..domain.. không mang annotation org.springframework.. "
                    + "(cho phép jakarta.persistence, jakarta.validation)");

    @ArchTest
    static final ArchRule r11_noAutowiredFieldInjection = noFields()
            .should().beAnnotatedWith(Autowired.class)
            .because("R11: cấm field injection - không field nào mang @Autowired (bắt buộc constructor injection)");

    @ArchTest
    static final ArchRule r11_noInjectFieldInjection = noFields()
            .should().beAnnotatedWith(Inject.class)
            .because("R11: cấm field injection - không field nào mang @Inject (bắt buộc constructor injection)");

    @ArchTest
    static final ArchRule r12_transactionalClassesOnlyInApplicationOrInfrastructure = noClasses()
            .that().resideOutsideOfPackages("..application..", "..infrastructure..")
            .should().beAnnotatedWith(Transactional.class)
            .because("R12: @Transactional chỉ xuất hiện trên class trong ..application.. (và ..infrastructure.. cho listener outbox)");

    @ArchTest
    static final ArchRule r12_transactionalMethodsOnlyInApplicationOrInfrastructure = noMethods()
            .that().areDeclaredInClassesThat().resideOutsideOfPackages("..application..", "..infrastructure..")
            .should().beAnnotatedWith(Transactional.class)
            .because("R12: @Transactional chỉ xuất hiện trên method trong ..application.. (và ..infrastructure.. cho listener outbox)");

    @ArchTest
    static final ArchRule r13_noLegacyDateTimeTypes = noClasses()
            .should().dependOnClassesThat().belongToAnyOf(Date.class, Timestamp.class, Calendar.class)
            .because("R13: cấm java.util.Date, java.sql.Timestamp, Calendar");

    @ArchTest
    static final ArchRule r13_noDirectNowCalls = classes()
            .should(notCallNowDirectly())
            .because("R13: cấm gọi LocalDateTime.now()/Instant.now() trực tiếp (phải qua Clock đã inject)");

    @ArchTest
    static final ArchRule r15_noStandardStreamsOrPrintStackTrace = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule r16_noLoggingOfPiiNamedFields = classes()
            .should(notLogPiiNamedFieldsInSameMethodAsLoggerCall())
            .because("R16: cấm gọi logger với biến/tham số tên khớp regex "
                    + "(?i).*(password|otp|token|secret|activationCode|fid|apiKey).* "
                    + "(best-effort: kiểm tra field cùng tên được đọc trong method có gọi logger - "
                    + "ArchUnit không expose được tên biến/tham số tại điểm gọi cụ thể; guard chính xác "
                    + "hơn là test CI quét nội dung log ở p17 §17.10b)");

    private static ArchCondition<JavaClass> notUseSpringAnnotations() {
        return new ArchCondition<JavaClass>("not be annotated with, and have no member annotated with, org.springframework..") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                reportSpringAnnotations(item.getAnnotations(), item.getFullName(), events);
                for (JavaField field : item.getFields()) {
                    reportSpringAnnotations(field.getAnnotations(), field.getFullName(), events);
                }
                for (JavaMethod method : item.getMethods()) {
                    reportSpringAnnotations(method.getAnnotations(), method.getFullName(), events);
                }
            }

            private void reportSpringAnnotations(
                    Iterable<? extends JavaAnnotation<?>> annotations, String location, ConditionEvents events) {
                for (JavaAnnotation<?> annotation : annotations) {
                    if (annotation.getRawType().getPackageName().startsWith("org.springframework")) {
                        events.add(SimpleConditionEvent.violated(annotation,
                                location + " -> vi phạm R8 (mang annotation " + annotation.getRawType().getName() + ")"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notCallNowDirectly() {
        return new ArchCondition<JavaClass>("not call Instant.now()/LocalDateTime.now() with no arguments") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    JavaClass owner = call.getTargetOwner();
                    boolean isClockLikeOwner = owner.isEquivalentTo(java.time.Instant.class)
                            || owner.isEquivalentTo(java.time.LocalDateTime.class);
                    boolean isNoArgNow = "now".equals(call.getTarget().getName())
                            && call.getTarget().getRawParameterTypes().isEmpty();
                    if (isClockLikeOwner && isNoArgNow) {
                        events.add(SimpleConditionEvent.violated(call,
                                call.getOrigin().getFullName() + " -> vi phạm R13 (gọi " + owner.getSimpleName()
                                        + ".now() trực tiếp thay vì qua Clock)"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notLogPiiNamedFieldsInSameMethodAsLoggerCall() {
        return new ArchCondition<JavaClass>("not access a PII-named field in the same method as a logger call") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    boolean callsLogger = method.getMethodCallsFromSelf().stream()
                            .anyMatch(call -> call.getTargetOwner().isAssignableTo(Logger.class));
                    if (!callsLogger) {
                        continue;
                    }
                    for (JavaFieldAccess access : method.getFieldAccesses()) {
                        String fieldName = access.getTarget().getName();
                        if (PII_NAME_PATTERN.matcher(fieldName).matches()) {
                            events.add(SimpleConditionEvent.violated(access,
                                    method.getFullName() + " -> vi phạm R16 (đọc field '" + fieldName
                                            + "' - tên khớp regex PII - trong method có gọi logger)"));
                        }
                    }
                }
            }
        };
    }
}
