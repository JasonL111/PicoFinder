plugins { alias(libs.plugins.kotlin.jvm) }
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
tasks.test {
    useJUnit()
    systemProperty("protocol.vectors", rootProject.file("../protocol/test-vectors.json").absolutePath)
    testLogging { events("passed", "skipped", "failed") }
}
