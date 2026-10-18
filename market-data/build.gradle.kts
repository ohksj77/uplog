plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api(project(":calc-engine"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework:spring-web")
    implementation("tools.jackson.core:jackson-databind")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework:spring-test")
    testImplementation("org.hamcrest:hamcrest")
    testImplementation("org.flywaydb:flyway-core")
    testRuntimeOnly(libs.h2)
}
