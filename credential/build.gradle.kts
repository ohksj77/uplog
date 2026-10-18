plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":portfolio"))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
}
