plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework:spring-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
