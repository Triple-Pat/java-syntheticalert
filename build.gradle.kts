import net.ltgt.gradle.errorprone.errorprone
import net.ltgt.gradle.nullaway.nullaway

plugins {
  `java-library`
  jacoco
  alias(libs.plugins.errorprone)
  alias(libs.plugins.nullaway)
  alias(libs.plugins.spotless)
  alias(libs.plugins.mavenPublish)
}

group = "com.triplepat"

version = "0.0.0"

repositories { mavenCentral() }

dependencies {
  // The one dependency: four nullness annotations, a few kilobytes, nothing
  // transitive. `api` because @NullMarked is part of the public contract and
  // Kotlin callers read it. Everything else here runs inside javac or tests.
  api(libs.jspecify)
  errorprone(libs.errorprone.core)
  errorprone(libs.nullaway)

  testImplementation(platform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
  withSourcesJar()
  withJavadocJar()
}

// The module is @NullMarked (JSpecify): every reference is non-null unless
// marked @Nullable. NullAway enforces that at compile time; a caller who
// passes null anyway gets the JVM's own NullPointerException.
nullaway {
  annotatedPackages.add("com.triplepat.syntheticalert")
  jspecifyMode.set(true)
}

tasks.withType<JavaCompile>().configureEach {
  // Compile against the Java 17 API whatever JDK runs the build; CI tests on
  // 17, 21, and 25.
  options.release.set(17)
  // Every javac and Error Prone warning is fatal. Error Prone runs as a javac
  // plugin, so this is the lint step; CI has no separate one.
  options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
  options.errorprone {
    // Every Error Prone check is on, including the off-by-default ones. Turn a
    // check off here only with the reason next to it.
    allDisabledChecksAsWarnings.set(true)
    // The package is @NullMarked (package-info.java), which JSpecify defines
    // to cover every class in it; annotating each class again is noise.
    disable("AddNullMarkedToClass")
    // Enforces a Java 8 API floor. `options.release` above holds the real
    // floor, Java 17.
    disable("Java8ApiChecker")
    nullaway { error() }
  }
}

tasks.withType<Javadoc>().configureEach {
  (options as StandardJavadocDocletOptions).apply {
    addBooleanOption("Xdoclint:all", true)
    addBooleanOption("Werror", true)
  }
}

tasks.test {
  useJUnitPlatform()
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport { reports { xml.required.set(true) } }

// The 100% gate is deliberately NOT wired into `check`, so that CI can run it
// as its own step after the Coveralls upload and the badge shows measured
// coverage even when the gate fails. Run it explicitly:
// `./gradlew build jacocoTestCoverageVerification`.
tasks.jacocoTestCoverageVerification {
  violationRules {
    rule {
      limit {
        counter = "LINE"
        minimum = "1.0".toBigDecimal()
      }
      limit {
        counter = "BRANCH"
        minimum = "1.0".toBigDecimal()
      }
    }
  }
}

tasks.withType<AbstractArchiveTask>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
}

spotless {
  java { googleJavaFormat(libs.versions.googleJavaFormat.get()) }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle()
  }
}

// Publishing is configured but not exercised: no tag and no Central upload
// until go-syntheticalert has proven out in production.
mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()
  coordinates("com.triplepat", "syntheticalert", version.toString())
  pom {
    name.set("syntheticalert")
    description.set(
      "A time-based callback to drive a synthetic alert metric, so a Triple Pat " +
        "check-in timer can verify your alerting pipeline end to end."
    )
    inceptionYear.set("2026")
    url.set("https://github.com/Triple-Pat/java-syntheticalert")
    licenses {
      license {
        name.set("The Apache License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
      }
    }
    developers {
      developer {
        id.set("triplepat")
        name.set("Triple Pat")
        url.set("https://triplepat.com")
      }
    }
    scm {
      url.set("https://github.com/Triple-Pat/java-syntheticalert")
      connection.set("scm:git:git://github.com/Triple-Pat/java-syntheticalert.git")
      developerConnection.set("scm:git:ssh://git@github.com/Triple-Pat/java-syntheticalert.git")
    }
  }
}
