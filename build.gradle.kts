import java.nio.file.Paths
import java.util.Properties
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.api.Git

import java.io.IOException

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    }
}

plugins {
    `kotlin-dsl`
    idea
}

val defaultEsVersion = readVersion("es-default.version")
val nebulaVersion = "9.1.1"

val fallbackPluginVersion = "v0.0.0-es$defaultEsVersion"
val pluginVersion = try {
    val git = Git.open(project.rootDir.getParentFile().resolve(".git"))
    git.describe().setTags(true).setMatch("v*-es*").call() ?: fallbackPluginVersion
} catch (e: IOException) {
    fallbackPluginVersion
}

class GitDescribe(val describe: String) {
    private val VERSION_REGEX = "[0-9]+\\.[0-9]+\\.[0-9]+(\\-(alpha|beta|rc)\\-[0-9]+)?"

    private val matchedGroups =
        "v(?<plugin>${VERSION_REGEX})-es(?<es>${VERSION_REGEX})(-(?<abbrev>.*))?".toRegex()
            .matchEntire(describe)!!
            .groups

    val plugin = matchedGroups["plugin"]!!.value
    val es = matchedGroups["es"]!!.value
    val abbrev = matchedGroups["abbrev"]?.value

    fun esVersion() = if (hasProperty("esVersion")) {
        property("esVersion")
    } else {
        // When adopting to new Elasticsearch version
        // create `buildSrc/es.version` file so IDE can fetch correct version of Elasticsearch
        readVersion("es.version") ?: es
    }

    fun pluginVersion() = buildString {
        append(plugin)
        if (abbrev != null) {
            append("-$abbrev")
        }
    }

    fun projectVersion() = buildString {
        append("$plugin-es${esVersion()}")
        if (abbrev != null) {
            append("-$abbrev")
        }
    }
}
val describe = GitDescribe(pluginVersion)

val generatedResourcesDir = Paths.get(buildDir.path, "generated-resources", "main")

sourceSets {
    main {
        output.dir(mapOf("builtBy" to "generateVersionProperties"), generatedResourcesDir)
    }
}

tasks.create("generateVersionProperties") {
    outputs.dir(generatedResourcesDir)
    doLast {
        val versionProps = Properties().apply {
            put("tag", describe.describe)
            put("projectVersion", describe.projectVersion())
            put("pluginVersion", describe.pluginVersion())
            put("esVersion", describe.esVersion())
        }
        generatedResourcesDir.resolve("es-plugin-versions.properties").toFile().writer().use {
            versionProps.store(it, null)
        }
    }
}

repositories {
    mavenLocal()
    gradlePluginPortal()
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

idea {
    module {
        isDownloadJavadoc = false
        isDownloadSources = false
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.30")
    implementation("org.elasticsearch.gradle:build-tools:${describe.esVersion()}")
    implementation("com.netflix.nebula:gradle-ospackage-plugin:${nebulaVersion}")
    constraints {
        // Due to end of jCenter repository
        implementation("com.avast.gradle:gradle-docker-compose-plugin:0.14.2")
        implementation("com.netflix.nebula:nebula-core:4.0.1")
    }
}

// Utils

fun readVersion(fileName: String): String? {
    project.projectDir.toPath().resolve(fileName).toFile().let {
        if (it.exists()) {
            val esVersion = it.readText().trim()
            if (!esVersion.startsWith('#')) {
                return esVersion
            }
            return null
        }
        return null
    }
}
