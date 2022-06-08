plugins {
    val kotlinVersion = "1.6.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("net.mamoe.mirai-console") version "2.10.3"
}

group = "bot.good"
version = "0.0.1"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}

dependencies {
    implementation ("me.sargunvohra.lib:pokekotlin:2.3.0")
    implementation ("org.jsoup:jsoup:1.14.3")
    implementation ("org.yaml:snakeyaml:1.30")

    implementation ("org.json:json:20220320")
    implementation ("commons-codec:commons-codec:1.10")
    testImplementation ("com.github.liuyueyi.media:markdown-plugin:2.6.3")
    testImplementation ("org.codehaus.groovy:groovy-all:3.0.10")
}
