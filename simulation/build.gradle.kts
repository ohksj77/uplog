plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api(project(":calc-engine"))
    implementation(project(":market-data"))
    implementation("org.springframework:spring-webmvc")
    implementation("com.github.ben-manes.caffeine:caffeine")
}
