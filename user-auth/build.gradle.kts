plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    // 세션 JWT(HS256) 서명·검증
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework:spring-webmvc")
    implementation("tools.jackson.core:jackson-databind")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework:spring-test")
    testImplementation("jakarta.servlet:jakarta.servlet-api")
}
