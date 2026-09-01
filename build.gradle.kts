plugins {
    java
    id("com.diffplug.spotless") version "8.10.1"
    checkstyle
}

group = "dev.mrz"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API only for now. NMS: see PLAN.md ("NMS recipe") before adding a full server jar.
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")

    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

checkstyle {
    toolVersion = "14.1.0"
    configDirectory = file("config/checkstyle")
}

tasks {
    test {
        useJUnitPlatform()
        maxHeapSize = "1g"
    }
    jar {
        archiveBaseName.set("Panic")
    }
    build {
        dependsOn(spotlessCheck, checkstyleMain, checkstyleTest)
    }
}

spotless {
    java {
        googleJavaFormat("1.36.1")
    }
}
