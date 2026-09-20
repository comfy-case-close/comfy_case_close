package com.fnbx.archtest;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The three hard rules of the layered architecture inside each service.
 * ADR-0003 decision 9 and section 11.5.
 *
 * <p>Layering is only worth anything if NOBODY SKIPS A LAYER. These are not
 * conventions - they are tests that run on every build.
 *
 * <h2>How to use</h2>
 * Each service adds one test class:
 * <pre>{@code
 * class ArchitectureTest {
 *     private final LayeredArchitectureRules rules =
 *             LayeredArchitectureRules.forService("com.fnbx.cashclose");
 *
 *     @Test void controllerKhongGoiThangRepository() { rules.controllerMustNotTouchRepository(); }
 *     @Test void repositoryKhongGoiNguocLenService() { rules.repositoryMustNotDependOnService(); }
 *     @Test void controllerChiNoiChuyenBangDto()     { rules.controllerMustNotExposeEntity(); }
 * }
 * }</pre>
 *
 * <h2>The one exception</h2>
 * {@code reporting-service} has no service layer because it is almost entirely
 * pass-through - the Architecture Sinkhole anti-pattern (the 80/20 rule). Use
 * {@link #forReadOnlyService(String)} there.
 */
public final class LayeredArchitectureRules {

    private final JavaClasses classes;
    private final String basePackage;
    private final boolean hasServiceLayer;

    private LayeredArchitectureRules(String basePackage, boolean hasServiceLayer) {
        this.basePackage = basePackage;
        this.hasServiceLayer = hasServiceLayer;
        this.classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("No production classes found in " + basePackage);
        }
    }

    public static LayeredArchitectureRules forService(String basePackage) {
        return new LayeredArchitectureRules(basePackage, true);
    }

    /** For reporting-service: no service layer, a documented exception. */
    public static LayeredArchitectureRules forReadOnlyService(String basePackage) {
        return new LayeredArchitectureRules(basePackage, false);
    }

    /** controller -> service -> repository. A controller must not reach past the service. */
    public void controllerMustNotTouchRepository() {
        if (!hasServiceLayer) return;   // reporting: allowed on purpose
        if (!hasClassesInPackageTree(basePackage + ".controller")) return;
        noClasses().that().resideInAPackage(basePackage + ".controller..")
            .should().dependOnClassesThat().resideInAPackage(basePackage + ".repository..")
            .because("Layered: controller calls service, service calls repository. Skipping "
                   + "a layer leaks business logic into controllers. ADR-0003 s11.5.")
            .check(classes);
    }

    /** Dependencies point one way only: downwards. */
    public void repositoryMustNotDependOnService() {
        if (!hasClassesInPackageTree(basePackage + ".repository")) return;
        noClasses().that().resideInAPackage(basePackage + ".repository..")
            .should().dependOnClassesThat().resideInAPackage(basePackage + ".service..")
            .because("Layered dependencies are one-directional. A repository calling back "
                   + "up into a service creates a cycle and makes the lower layer "
                   + "impossible to test on its own.")
            .check(classes);
    }

    /**
     * Controllers speak DTOs, never entities.
     *
     * <p><b>Both package strings are load-bearing.</b> Controllers live in
     * {@code .controller..} (was {@code .api..}) and entities in {@code .entity..}
     * (was {@code .domain..}). If either stops matching after a refactor, this rule
     * silently passes and protects nothing - the two early-return guards below make
     * that failure mode more likely, so keep the strings in sync with the packages.
     */
    public void controllerMustNotExposeEntity() {
        if (!hasControllerClasses()) return;
        noClasses().that().resideInAPackage(basePackage + ".controller..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().resideInAPackage(basePackage + ".entity..")
            .because("Exposing entities pins the HTTP contract to the table layout. "
                   + "Renaming one column then breaks every client. Use DTOs.")
            .check(classes);
    }

    public void checkAll() {
        controllerMustNotTouchRepository();
        repositoryMustNotDependOnService();
        controllerMustNotExposeEntity();
    }

    private boolean hasClassesInPackageTree(String packageName) {
        for (JavaClass javaClass : classes) {
            if (residesInPackageTree(javaClass, packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasControllerClasses() {
        String controllerPackage = basePackage + ".controller";
        for (JavaClass javaClass : classes) {
            if (residesInPackageTree(javaClass, controllerPackage)
                    && javaClass.getSimpleName().endsWith("Controller")) {
                return true;
            }
        }
        return false;
    }

    private static boolean residesInPackageTree(JavaClass javaClass, String packageName) {
        String actualPackage = javaClass.getPackageName();
        return actualPackage.equals(packageName) || actualPackage.startsWith(packageName + ".");
    }
}
