plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api(project(":calc-engine"))
    implementation(project(":market-data"))
    implementation(project(":local-user"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework:spring-webmvc")
    implementation("com.github.ben-manes.caffeine:caffeine")
    // 계약 필드 "return"은 자바 예약어라 @JsonProperty로 이름을 붙인다.
    implementation("com.fasterxml.jackson.core:jackson-annotations")
    // 위젯 스냅샷 파일(widget/widget.json)을 쓴다.
    implementation("tools.jackson.core:jackson-databind")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.springframework:spring-test")
    testImplementation("org.flywaydb:flyway-core")
    testRuntimeOnly(libs.h2)
}
