package ar.com.padelnec.gym;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.BaseEntity;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.TenantScopedEntity;
import ar.com.padelnec.domain.enums.ThemeMode;
import ar.com.padelnec.security.ClubUserPrincipal;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.service.TenantIconService;
import ar.com.padelnec.support.TokenHash;
import ar.com.padelnec.support.Tokens;
import ar.com.padelnec.ui.MainLayout;
import ar.com.padelnec.web.BusinessRuleException;
import ar.com.padelnec.web.ClientIp;
import ar.com.padelnec.web.ResourceNotFoundException;
import ar.com.padelnec.web.UnauthorizedSessionException;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * El modulo de gimnasio tiene que poder sacarse (o mudarse a otra aplicacion)
 * sin arrastrar nada de padel. Este test es lo que lo garantiza: si alguien
 * importa una clase de padel desde {@code gym}, o de padel a {@code gym}, falla.
 *
 * <p>La lista de abajo es TODO lo que el modulo comparte con la aplicacion. Si
 * cambia, es una decision: agregar algo aca es aceptar una dependencia mas de
 * cara a una extraccion futura.
 */
@AnalyzeClasses(packages = "ar.com.padelnec", importOptions = ImportOption.DoNotIncludeTests.class)
class GymArchitectureTest {

    @ArchTest
    static final ArchRule nothingOutsideGymUsesGym = noClasses()
            .that().resideOutsideOfPackage("ar.com.padelnec.gym..")
            // El unico enganche: el menu lateral del panel agrega el grupo "Gimnasio".
            .and().doNotHaveFullyQualifiedName(MainLayout.class.getName())
            .should().dependOnClassesThat().resideInAPackage("ar.com.padelnec.gym..")
            .because("el modulo no debe ser conocido por el resto de la aplicacion");

    @ArchTest
    static final ArchRule gymOnlyUsesTheSharedCore = classes()
            .that().resideInAPackage("ar.com.padelnec.gym..")
            .should().onlyDependOnClassesThat(
                    resideOutsideOfPackage("ar.com.padelnec..")
                            .or(resideInAPackage("ar.com.padelnec.gym.."))
                            .or(belongToAnyOf(
                                    // Multi-tenancy: el club en contexto y las entidades filtradas por club.
                                    TenantContext.class, BaseEntity.class, TenantScopedEntity.class,
                                    Tenant.class, TenantService.class,
                                    // Portada compartida: conversion a icono sin conocer su almacenamiento.
                                    TenantIconService.class,
                                    // Marca del club (tema claro/oscuro) que el manifest y la app reflejan.
                                    ThemeMode.class,
                                    // Sesiones: mismo esquema de tokens que el resto.
                                    Tokens.class, TokenHash.class,
                                    // Origen del pedido para los topes de intentos: detras de un
                                    // proxy no se puede leer de getRemoteAddr().
                                    ClientIp.class,
                                    // Errores de negocio compartidos.
                                    BusinessRuleException.class, ResourceNotFoundException.class,
                                    UnauthorizedSessionException.class,
                                    // Panel: el marco comun y el usuario autenticado.
                                    MainLayout.class, ClubUserPrincipal.class)))
            .because("el modulo solo puede depender del nucleo compartido, no del dominio de padel");
}
