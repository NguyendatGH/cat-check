package com.catcheck.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * R4, R14, R18 — quy tắc tầng web/api. Quét chỉ main sources (bỏ qua test) vì đây là quy tắc
 * cho code sản xuất.
 */
@AnalyzeClasses(packages = "com.catcheck", importOptions = ImportOption.DoNotIncludeTests.class)
class WebLayerRuleTests {

    @ArchTest
    static final ArchRule r4_restControllersDoNotExposeDomainTypes = classes()
            .that().areAnnotatedWith(RestController.class)
            .should(notExposeDomainTypesInSignature())
            .because("R4: không @RestController nào có tham số/kiểu trả về nằm trong package ..domain..");

    @ArchTest
    static final ArchRule r14_apiDtosAreRecordsWithoutJpaAnnotations = classes()
            .that().resideInAPackage("..api.dto..")
            .should(beARecordWithoutJpaAnnotations())
            .because("R14: mọi DTO trong ..api.dto.. phải là record và không mang annotation JPA");

    @ArchTest
    static final ArchRule r18_restControllerPublicMethodsHaveOperationId = classes()
            .that().areAnnotatedWith(RestController.class)
            .should(haveOperationIdOnEveryPublicMethod())
            .because("R18: mọi @RestController method public phải có @Operation(operationId = …)");

    private static ArchCondition<JavaClass> notExposeDomainTypesInSignature() {
        return new ArchCondition<JavaClass>("not expose ..domain.. types as method parameter/return type") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    for (JavaClass paramType : method.getRawParameterTypes()) {
                        if (PackageMatcher.of("..domain..").matches(paramType.getPackageName())) {
                            events.add(SimpleConditionEvent.violated(method,
                                    method.getFullName() + " -> vi phạm R4 (tham số kiểu " + paramType.getName() + " nằm trong domain)"));
                        }
                    }
                    JavaClass returnType = method.getRawReturnType();
                    if (PackageMatcher.of("..domain..").matches(returnType.getPackageName())) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " -> vi phạm R4 (kiểu trả về " + returnType.getName() + " nằm trong domain)"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beARecordWithoutJpaAnnotations() {
        return new ArchCondition<JavaClass>("be a record and not be annotated with a JPA annotation") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!item.isRecord()) {
                    events.add(SimpleConditionEvent.violated(item, item.getFullName() + " -> vi phạm R14 (DTO phải là record)"));
                }
                boolean hasJpaAnnotation = item.getAnnotations().stream()
                        .anyMatch(annotation -> annotation.getRawType().getPackageName().startsWith("jakarta.persistence"));
                if (hasJpaAnnotation) {
                    events.add(SimpleConditionEvent.violated(item,
                            item.getFullName() + " -> vi phạm R14 (DTO không được mang annotation jakarta.persistence.*)"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveOperationIdOnEveryPublicMethod() {
        return new ArchCondition<JavaClass>("have @Operation(operationId = ...) on every public method") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethod method : item.getMethods()) {
                    if (!method.getModifiers().contains(JavaModifier.PUBLIC)) {
                        continue;
                    }
                    Optional<Operation> operation = method.tryGetAnnotationOfType(Operation.class);
                    boolean hasOperationId = operation.isPresent()
                            && operation.get().operationId() != null
                            && !operation.get().operationId().isBlank();
                    if (!hasOperationId) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " -> vi phạm R18 (thiếu @Operation(operationId = ...))"));
                    }
                }
            }
        };
    }
}
