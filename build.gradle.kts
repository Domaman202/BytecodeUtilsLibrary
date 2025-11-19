plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.nmcp.aggregation").version("1.2.1")
    `maven-publish`
    `java-library`
    signing
}

group = "io.github.domaman202" // todo: Временно. Поменять на "ru.DmN".
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.ow2.asm:asm:9.7")
    implementation("org.ow2.asm:asm-tree:9.7")
    implementation("org.ow2.asm:asm-util:9.7")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    withJavadocJar()
}

kotlin {
    jvmToolchain(8)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("Bytecode Utils Library")
                description.set("Annotation processor for bytecode transformation and manipulation")
                url.set("https://github.com/Domaman202/BytecodeUtilsLibrary")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("DomamaN202")
                        name.set("DomamaN202")
                        email.set("vip.domaman@mail.ru")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Domaman202/BytecodeUtilsLibrary.git")
                    developerConnection.set("scm:git:ssh://github.com/Domaman202/BytecodeUtilsLibrary.git")
                    url.set("https://github.com/Domaman202/BytecodeUtilsLibrary")
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

nmcpAggregation {
    centralPortal {
        username = System.getenv("MAVEN_USERNAME")
        password = System.getenv("MAVEN_PASSWORD")
        publicationName = "$group:BytecodeUtilsLibrary:$version"
        publishingType = "USER_MANAGED"
        publishingType = "AUTOMATIC"
    }

    publishAllProjectsProbablyBreakingProjectIsolation()
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenJava"])
}