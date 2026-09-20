package com.fnbx.archtest;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Rules that keep the cost of splitting the repo at about two DAYS instead of two
 * MONTHS. ADR-0003 section 12.11.
 *
 * <h2>Why this matters more than it looks</h2>
 * The monorepo is a deliberate choice resting on one argument: "splitting later is
 * still cheap". That argument only holds while there are no cross-service
 * dependencies. If {@code cashclose} imports a {@code workforce} class, splitting
 * means untangling that first - a real refactor measured in months. This rule
 * catches it at the pull request instead.
 *
 * <h2>What is allowed</h2>
 * <ul>
 *   <li>Importing another domain's <b>entity</b>
 *       ({@code com.fnbx.identity.entity.Branch}) - that is the federated entity
 *       library, and the reason Service-Based architecture permits cross-schema
 *       joins.</li>
 *   <li>Importing another domain's <b>service, controller or repository</b> is
 *       not - that is a dependency on BEHAVIOUR, not on data.</li>
 * </ul>
 */
public final class ServiceBoundaryRules {

    /** The nine domains fixed in ADR-0003 section 14.2. */
    public static final List<String> DOMAINS = List.of(
            "identity", "platform", "files", "notify", "integration",
            "cashclose", "workforce", "inventory", "reporting");

    private ServiceBoundaryRules() {}

    /**
     * A service must not import another service's BEHAVIOUR layer.
     * Entities are fine - those are the shared library.
     */
    public static void serviceMustNotImportAnotherServiceBehaviour(String ownDomain) {
        JavaClasses classes = importProductionClasses(ownDomain);

        for (String other : DOMAINS) {
            if (other.equals(ownDomain)) continue;
            noClasses().that().resideInAPackage("com.fnbx." + ownDomain + "..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.fnbx." + other + ".service..",
                        "com.fnbx." + other + ".controller..",
                        "com.fnbx." + other + ".mapper..",
                        "com.fnbx." + other + ".dto..",
                        "com.fnbx." + other + ".repository..",
                        "com.fnbx." + other + ".config..")
                .because(ownDomain + " may READ " + other
                       + " entities (federated library, cross-schema joins) but must not "
                       + "depend on its behaviour. Cross dependencies raise the cost of "
                       + "splitting the repo from 2 days to 2 months. ADR-0003 s12.11.")
                .check(classes);
        }
    }

    /**
     * No service may WRITE into another service's schema.
     *
     * <p>This is the second layer. The first is the PostgreSQL {@code GRANT} - the
     * database itself returns {@code permission denied}. This test fails EARLIER,
     * at the pull request, and explains why.
     */
    public static void mustNotWriteToForeignSchema(String ownDomain) {
        JavaClasses classes = importProductionClasses(ownDomain);

        String repositoryPackage = "com.fnbx." + ownDomain + ".repository";
        if (!hasClassesInPackageTree(classes, repositoryPackage)) return;

        for (String other : DOMAINS) {
            if (other.equals(ownDomain) || other.equals("reporting")) continue;
            noClasses().that().resideInAPackage(repositoryPackage + "..")
                .should().dependOnClassesThat().haveNameMatching(
                        "com\\.fnbx\\." + other + "\\.repository\\..*Repository")
                .because("Split services by WRITE ownership. " + ownDomain + " reads "
                       + other + " with a SQL join, not through its repository. "
                       + "ADR-0002 s5.0.")
                .check(classes);
        }
    }

    private static JavaClasses importProductionClasses(String ownDomain) {
        String basePackage = "com.fnbx." + ownDomain;
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
        if (classes.isEmpty()) {
            throw new IllegalArgumentException("No production classes found in " + basePackage);
        }
        return classes;
    }

    private static boolean hasClassesInPackageTree(JavaClasses classes, String packageName) {
        for (JavaClass javaClass : classes) {
            String actualPackage = javaClass.getPackageName();
            if (actualPackage.equals(packageName) || actualPackage.startsWith(packageName + ".")) {
                return true;
            }
        }
        return false;
    }
}
