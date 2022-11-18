import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.6.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("net.mamoe.mirai-console") version "2.13.0"
}

group = "bot.good"
version = "0.0.1"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    implementation ("me.sargunvohra.lib:pokekotlin:2.3.0")
//    implementation ("org.jsoup:jsoup:1.14.3")
    implementation ("org.yaml:snakeyaml:1.30")
//    implementation("org.mozilla:rhino:1.7.14")
    implementation("org.openjdk.nashorn:nashorn-core:15.4")
    implementation("org.json:json:20220320")
    implementation("commons-codec:commons-codec:1.10")
    testImplementation ("com.github.liuyueyi.media:markdown-plugin:2.6.3")
    testImplementation ("org.codehaus.groovy:groovy-all:3.0.10")
    api("io.ktor:ktor-client-core:2.0.0")
    api("io.ktor:ktor-client-okhttp:2.0.0")
    implementation(kotlin("stdlib-jdk8"))
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}