import com.adarshr.gradle.testlogger.theme.ThemeType
import com.github.jk1.license.filter.LicenseBundleNormalizer

buildscript { repositories { mavenCentral() } }

plugins {
  id("org.springframework.boot") version "3.0.2"
  id("io.spring.dependency-management") version "1.1.0"
  id("java")
  id("com.diffplug.spotless") version "6.16.0"
  id("jacoco")
  id("org.sonarqube") version "4.0.0.2929"
  id("com.github.jk1.dependency-license-report") version "2.1"
  id("com.adarshr.test-logger") version "3.2.0"
}

group = "de.bund.digitalservice"
version = "0.0.1-SNAPSHOT"

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

configurations { compileOnly { extendsFrom(annotationProcessor.get()) } }

repositories { mavenCentral() }

jacoco { toolVersion = "0.8.8" }

testlogger { theme = ThemeType.MOCHA }

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-webflux")

  compileOnly("org.projectlombok:lombok")

  developmentOnly("org.springframework.boot:spring-boot-devtools")

  annotationProcessor("org.projectlombok:lombok")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.tngtech.archunit:archunit-junit5:1.0.1")
  testImplementation("org.mockito:mockito-junit-jupiter:5.1.1")
}

tasks {
  register<Test>("integrationTest") {
    description = "Runs the integration tests."
    group = "verification"
    useJUnitPlatform {
      includeTags("integration")
    }

    // So that running integration test require running unit tests first,
    // and we won"t even attempt running integration tests when there are
    // failing unit tests.
    dependsOn(test)
    finalizedBy(jacocoTestReport)
  }

  check {
    dependsOn("integrationTest")
  }

  bootBuildImage {
    val containerRegistry = System.getenv("CONTAINER_REGISTRY") ?: "ghcr.io"
    val containerImageName = System.getenv("CONTAINER_IMAGE_NAME") ?: "digitalservicebund/${rootProject.name}"
    val containerImageVersion = System.getenv("CONTAINER_IMAGE_VERSION") ?: "latest"

    imageName.set("$containerRegistry/$containerImageName:$containerImageVersion")
    builder.set("paketobuildpacks/builder:tiny")
    publish.set(false)

    docker {
      publishRegistry {
        username.set(System.getenv("CONTAINER_REGISTRY_USER") ?: "")
        password.set(System.getenv("CONTAINER_REGISTRY_PASSWORD") ?: "")
        url.set("https://$containerRegistry")
      }
    }
  }

  jacocoTestReport {
    // Jacoco hooks into all tasks of type: Test automatically, but results for each of these
    // tasks are kept separately and are not combined out of the box.. we want to gather
    // coverage of our unit and integration tests as a single report!
    executionData.setFrom(
      files(
        fileTree(project.buildDir.absolutePath) {
          include("jacoco/*.exec")
        },
      ),
    )
    reports {
      xml.required = true
      html.required = true
    }
    dependsOn("integrationTest") // All tests are required to run before generating a report..
  }

  jar { // We have no need for the plain archive, thus skip creation for build speedup!
    enabled = false
  }

  getByName("sonar") {
    dependsOn("jacocoTestReport")
  }

  test { useJUnitPlatform { excludeTags("integration") } }
}

spotless {
  java {
    removeUnusedImports()
    googleJavaFormat()
  }

  groovyGradle {
    greclipse("4.6.3").configFile("greclipse.properties")
    toggleOffOn()
    replaceRegex("quotes", "(['])(.*)\\1", "\"\$2\"")
  }

  format("misc") {
    target(
      "**/*.js",
      "**/*.json",
      "**/*.md",
      "**/*.properties",
      "**/*.sh",
      "**/*.yml",
    )
    prettier(
      mapOf(
        "prettier" to "2.6.1",
        "prettier-plugin-sh" to "0.7.1",
        "prettier-plugin-properties" to "0.1.0",
      ),
    ).config(mapOf("keySeparator" to "="))
  }
}

sonar {
  // NOTE: sonarqube picks up combined coverage correctly without further configuration from:
  // build/reports/jacoco/test/jacocoTestReport.xml
  properties {
    property("sonar.projectKey", "digitalservicebund_java-application-template")
    property("sonar.organization", "digitalservicebund")
    property("sonar.host.url", "https://sonarcloud.io")
  }
}

licenseReport {
  // If there's a new dependency with a yet unknown license causing this task to fail
  // the license(s) will be listed in build/reports/dependency-license/dependencies-without-allowed-license.json
  allowedLicensesFile = File("$projectDir/allowed-licenses.json")
  filters = arrayOf(
    // With second arg true we get the default transformations:
    // https://github.com/jk1/Gradle-License-Report/blob/7cf695c38126b63ef9e907345adab84dfa92ea0e/src/main/resources/default-license-normalizer-bundle.json
    LicenseBundleNormalizer(null, true),
  )
}
