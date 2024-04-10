import java.io.*

plugins{
    `java-gradle-plugin`
    `maven-publish`
}

val arcVersion: String by project
val mindustryVersion: String by project
val javapoetVersion: String by project
val kotlinVersion: String by project

fun arc(module: String): String{
    return "com.github.Anuken.Arc$module:$arcVersion"
}

fun mindustry(module: String): String{
    return "com.github.Anuken.Mindustry$module:$mindustryVersion"
}

fun javapoet(): String{
    return "com.squareup:javapoet:$javapoetVersion"
}

fun kotlinPlugin(module: String): String{
    return "org.jetbrains.kotlin.$module:org.jetbrains.kotlin.$module.gradle.plugin:$kotlinVersion"
}

allprojects{
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    sourceSets["main"].java.setSrcDirs(arrayListOf(layout.projectDirectory.dir("src")))
    version = "v146.0.5"

    repositories{
        google()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/releases/")
        maven("https://raw.githubusercontent.com/Zelaux/MindustryRepo/master/repository")
        maven("https://jitpack.io")
    }

    configure<JavaPluginExtension>{
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach{
        options.apply{
            isIncremental = true
            encoding = "UTF-8"
            compilerArgs.add("-Xlint:-options")
        }

        sourceCompatibility = "17"
        targetCompatibility = "8"

        doFirst{
            sourceCompatibility = "8"
        }
    }

    tasks.withType<Javadoc>().configureEach{
        options{
            encoding = "UTF-8"

            val exports = (project.property("org.gradle.jvmargs") as String)
                .split(Regex("\\s+"))
                .filter{it.startsWith("--add-opens")}
                .map{"--add-exports ${it.substring("--add-opens=".length)}"}
                .reduce{accum, arg -> "$accum $arg"}

            val opts = File(temporaryDir, "exports.options")
            BufferedWriter(FileWriter(opts, Charsets.UTF_8, false)).use{it.write("-Xdoclint:none $exports")}
            optionFiles(opts)
        }
    }
}

configure(allprojects - project(":downgrader")){
    dependencies{
        annotationProcessor(project(":downgrader"))
    }
}

configure(arrayListOf(project(":downgrader"), project(":entity"))){
    sourceSets["main"].resources.setSrcDirs(arrayListOf(layout.projectDirectory.dir("assets")))

    group = "com.github.GlennFolker.EntityAnno"
    publishing.publications.register<MavenPublication>("maven"){
        from(components["java"])
    }

    dependencies{
        implementation(arc(":arc-core"))
    }
}

project(":entity"){
    dependencies{
        implementation(mindustry(":core"))
        implementation(javapoet())
    }
}

project(":"){
    apply(plugin = "java-gradle-plugin")

    group = "com.github.Folker"
    configure<GradlePluginDevelopmentExtension>{
        plugins.register("entityAnno"){
            id = "com.github.GlennFolker.EntityAnno"
            displayName = "EntityAnno"
            implementationClass = "ent.EntityAnnoPlugin"
        }
    }

    dependencies{
        implementation(arc(":arc-core"))
        implementation(kotlinPlugin("jvm"))
        implementation(kotlinPlugin("kapt"))
    }
}
