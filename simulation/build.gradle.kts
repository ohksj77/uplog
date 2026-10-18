plugins {
    id("uplog.java-conventions")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    api(project(":calc-engine"))
    implementation(project(":market-data"))
    // 요청자(로그인 사용자) 확인: 실제 종목 시세는 요청자 자신의 키로만 받는다
    implementation(project(":local-user"))
    implementation("org.springframework:spring-webmvc")
    implementation("com.github.ben-manes.caffeine:caffeine")
}
