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
