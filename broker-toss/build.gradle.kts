plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api(project(":portfolio"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-webmvc")
    implementation("tools.jackson.core:jackson-databind")
    implementation("org.slf4j:slf4j-api")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework:spring-test")
    testImplementation("org.hamcrest:hamcrest")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
