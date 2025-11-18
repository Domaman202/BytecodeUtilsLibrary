plugins {
    kotlin("jvm") version "2.2.20"
    `maven-publish`
    `java-library`
}

group = "ru.DmN"
version = "1.0.0"

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

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            pom {
                name.set("Bytecode Utils Library")
                description.set("Java bytecode manipulation library.")
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
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}