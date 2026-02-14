package me.gogradually.courseenrollmentsystem;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(
    packages = "me.gogradually.courseenrollmentsystem",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class LayerDependencyTest {

    @ArchTest
    static final ArchRule domainShouldNotDependOnOuterLayers = noClasses()
        .that()
        .resideInAnyPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..application..", "..interfaces..", "..infrastructure..");

    @ArchTest
    static final ArchRule applicationShouldNotDependOnInterfacesAndInfrastructure = noClasses()
        .that()
        .resideInAnyPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..interfaces..", "..infrastructure..");

    @ArchTest
    static final ArchRule interfacesShouldNotDependOnDomainAndInfrastructure = noClasses()
        .that()
        .resideInAnyPackage("..interfaces..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..domain..", "..infrastructure..");
}
