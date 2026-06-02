plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "org.datayoo"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.intellij.plugins.markdown")
        bundledPlugin("org.jetbrains.idea.maven")


        // Add plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }

    // i18n generator (ported from i18n-maven-plugin)
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.aliyun.oss:aliyun-sdk-oss:3.18.4")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252.25557"
        }

        changeNotes = """
            1.0.0
            - 品牌名称更新为「数由 GUI」
            - 算子扫描结果改为表格展示，提升可读性
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    // This plugin does not contribute Settings configurable options.
    // Disabling searchable options avoids unstable IDE bootstrap during sync/build.
    named("buildSearchableOptions") {
        enabled = false
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
