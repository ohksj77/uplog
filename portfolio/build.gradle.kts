plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api(project(":calc-engine"))
    implementation(project(":market-data"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework:spring-webmvc")
    implementation("com.github.ben-manes.caffeine:caffeine")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework:spring-test")
    testImplementation("org.flywaydb:flyway-core")
    testRuntimeOnly(libs.h2)
}
