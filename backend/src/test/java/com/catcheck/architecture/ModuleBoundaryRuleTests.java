package com.catcheck.architecture;

import com.catcheck.privacy.spi.ErasureParticipant;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.catcheck", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryRuleTests {

    @ArchTest
    static final ArchRule r1_domainDoesNotDependOnOuterLayers = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..")
            .because("R1: class trong ..domain.. không phụ thuộc ..api.., ..application.., ..infrastructure.. của bất kỳ module nào");

    @ArchTest
    static final ArchRule r2_applicationDoesNotDependOnOwnModuleInfrastructure = classes()
            .that().resideInAPackage("..application..")
            .should(notDependOnOwnModuleInfrastructure())
            .because("R2: class trong ..application.. không phụ thuộc ..infrastructure.. của chính module mình");

    @ArchTest
    static final ArchRule r3_apiDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..")
            .because("R3: class trong ..api.. không phụ thuộc ..infrastructure..");

    @ArchTest
    static final ArchRule r6_jpaAssociationsDoNotCrossModuleBoundary = classes()
            .should(notHaveJpaAssociationCrossingModuleBoundary())
            .because("R6: không entity nào mang @ManyToOne/@OneToMany/@ManyToMany trỏ tới type ngoài module của chính nó "
                    + "(chưa có entity nào ở M0 - rule rỗng PASS là đúng thiết kế)");

    @ArchTest
    static final ArchRule r7_onlyPersistenceInfrastructureUsesSpringDataOrCriteria = noClasses()
            .that().resideOutsideOfPackage("..infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data..", "jakarta.persistence.criteria..")
            .because("R7: chỉ class trong ..infrastructure.persistence.. được phụ thuộc org.springframework.data.. và jakarta.persistence.criteria..");

    @ArchTest
    static final ArchRule r9_colorDomainStaysPureJava = noClasses()
            .that().resideInAPackage("com.catcheck.scan.domain.color..")
            .and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage("org.bytedeco..", "org.springframework..", "jakarta..")
            .because("R9: com.catcheck.scan.domain.color không phụ thuộc org.bytedeco.., org.springframework.., jakarta.. "
                    + "(package chưa tồn tại ở M0, áp dụng thật từ M3)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule r10_erasureParticipantsLiveInApplicationPrivacy = classes()
            .that().implement(ErasureParticipant.class)
            .should().resideInAPackage("..application.privacy..")
            .because("R10: mọi class implement privacy.spi.ErasureParticipant phải nằm trong ..application.privacy.. "
                    + "(chưa có instance nào ở M0)")
            .allowEmptyShould(true);

    private static ArchCondition<JavaClass> notDependOnOwnModuleInfrastructure() {
        return new ArchCondition<>("not depend on ..infrastructure.. of its own module") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownModule = ArchUnitSupport.moduleOf(item);
                for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    boolean isInfrastructure = PackageMatcher.of("..infrastructure..").matches(target.getPackageName());
                    boolean sameModule = !ownModule.isEmpty() && ownModule.equals(ArchUnitSupport.moduleOf(target));
                    if (isInfrastructure && sameModule) {
                        events.add(SimpleConditionEvent.violated(dependency,
                                dependency.getDescription() + " -> vi phạm R2 (application phụ thuộc infrastructure cùng module '" + ownModule + "')"));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notHaveJpaAssociationCrossingModuleBoundary() {
        return new ArchCondition<JavaClass>("not have a JPA association crossing its module boundary") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String ownModule = ArchUnitSupport.moduleOf(item);
                if (ownModule.isEmpty()) {
                    return;
                }
                for (JavaField field : item.getFields()) {
                    boolean isAssociation = field.isAnnotatedWith(ManyToOne.class)
                            || field.isAnnotatedWith(OneToMany.class)
                            || field.isAnnotatedWith(ManyToMany.class);
                    if (!isAssociation) {
                        continue;
                    }
                    for (JavaClass involved : field.getAllInvolvedRawTypes()) {
                        String targetModule = ArchUnitSupport.moduleOf(involved);
                        if (!targetModule.isEmpty() && !targetModule.equals(ownModule)) {
                            events.add(SimpleConditionEvent.violated(field,
                                    field.getFullName() + " -> vi phạm R6 (JPA association trỏ sang module '"
                                            + targetModule + "', khác module sở hữu '" + ownModule + "')"));
                        }
                    }
                }
            }
        };
    }
}
