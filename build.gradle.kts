
plugins {
    java
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.2.2"
}

group = property("group")!!
version = property("version")!!

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "discordsrv"
        url = uri("https://nexus.scarsz.me/content/groups/public/")
    }
}

dependencies {
    // Paper API: compatibility baseline
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    // SQLite database driver
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")

    // DiscordSRV API (optional integration)
    compileOnly("com.discordsrv:discordsrv:1.28.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    runServer {
        minecraftVersion("26.1.2")
    }

    processResources {
        filteringCharset = "UTF-8"

        val pluginVersion = project.version
        filesMatching("plugin.yml") {
            expand("version" to pluginVersion)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}
