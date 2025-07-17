import org.opensearch.gradle.plugin.PluginPropertiesExtension
import org.opensearch.gradle.test.RestIntegTestTask
import org.opensearch.gradle.testclusters.OpenSearchCluster
import org.opensearch.gradle.testclusters.TestDistribution
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*

fun Project.configureOpensearchPlugin(
    name: String,
    description: String,
    classname: String,
    numberOfTestClusterNodes: Int = 1,
) {
    configure<org.opensearch.gradle.plugin.PluginPropertiesExtension> {
        this.name = name
        this.description = description
        this.classname = classname
        version = project.version.toString()
        licenseFile = project.file("LICENSE.txt")
        noticeFile = project.file("NOTICE.txt")
        version = Versions.plugin
    }

    setProperty("licenseFile", project.rootProject.file("LICENSE.txt"))
    setProperty("noticeFile", project.rootProject.file("NOTICE.txt"))

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_15
        targetCompatibility = JavaVersion.VERSION_15
    }

    configure<NamedDomainObjectContainer<OpenSearchCluster>> {
        create("integTest") {
            setTestDistribution(TestDistribution.INTEG_TEST)
            if (numberOfTestClusterNodes != 1) {
                numberOfNodes = numberOfTestClusterNodes
            }
            plugin(tasks.named<Zip>("bundlePlugin").get().archiveFile)
        }

        val integTestTask = tasks.register<RestIntegTestTask>("integTest") {
            dependsOn("bundlePlugin")
        }

        tasks.named("check") {
            dependsOn(integTestTask)
        }
    }

    val distDir = layout.buildDirectory.dir("distributions")

    tasks.register("deb", com.netflix.gradle.plugins.deb.Deb::class) {
        dependsOn("bundlePlugin")

        packageName = "opensearch-${project.name}-plugin"
        requires("opensearch", Versions.opensearch)

        from(zipTree(tasks["bundlePlugin"].outputs.files.singleFile))

        val esHome = project.properties["esHome"] ?: "/usr/share/opensearch"
        into("$esHome/plugins/${project.name}")

        doLast {
            if (properties.containsKey("assembledInfo")) {
                distDir.get().file("assembled-deb.filename").asFile
                    .writeText(assembleArchiveName())
            }
        }
    }

    tasks.named("assemble") {
        dependsOn("deb")
    }
}
