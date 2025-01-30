import org.elasticsearch.gradle.test.RestIntegTestTask
import org.elasticsearch.gradle.testclusters.ElasticsearchCluster
import org.elasticsearch.gradle.testclusters.TestDistribution
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.*

import java.nio.file.Paths

fun Project.extraConfiguration() {
    configure<org.elasticsearch.gradle.plugin.PluginPropertiesExtension> {
        version = Versions.plugin
    }

    setProperty("licenseFile", project.rootProject.file("LICENSE.txt"))
    setProperty("noticeFile", project.rootProject.file("NOTICE.txt"))

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_15
        targetCompatibility = JavaVersion.VERSION_15
    }

    configure<NamedDomainObjectContainer<ElasticsearchCluster>> {
        val integTestCluster by named("integTest") {
            setTestDistribution(TestDistribution.OSS)
        }

        val integTestTask = tasks.getByName<RestIntegTestTask>("integTest") {
            dependsOn("bundlePlugin")
        }

        tasks.named("check") {
            dependsOn(integTestTask)
        }
    }

    val distDir = Paths.get(buildDir.path, "distributions")

    tasks.register("deb", com.netflix.gradle.plugins.deb.Deb::class) {
        dependsOn("bundlePlugin")

        packageName = "elasticsearch-${project.name}-plugin"
        requires("elasticsearch", Versions.elasticsearch)
            .or("elasticsearch-oss", Versions.elasticsearch)

        from(zipTree(tasks["bundlePlugin"].outputs.files.singleFile))

        val esHome = project.properties["esHome"] ?: "/usr/share/elasticsearch"
        into("$esHome/plugins/${project.name}")

        doLast {
            if (properties.containsKey("assembledInfo")) {
                distDir.resolve("assembled-deb.filename").toFile()
                    .writeText(assembleArchiveName())
            }
        }
    }

    tasks.named("assemble") {
        dependsOn("deb")
    }
}
