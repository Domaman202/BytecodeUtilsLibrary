plugins {
    kotlin("jvm") version "2.2.20"
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