plugins {
	java
	id("org.springframework.boot") version "3.5.14"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.rota"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springModulithVersion"] = "1.4.11"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	// Distributed rate limiting (plan §8.5). JDK17 artifacts; NOT in the Boot BOM, version pinned.
	// The lettuce module reuses the Lettuce client already pulled in by starter-data-redis.
	implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
	implementation("com.bucket4j:bucket4j_jdk17-lettuce:8.14.0")
	// OpenAPI JSON at /v3/api-docs — input for the frontend Orval codegen (plan §11.4).
	// Spec-only starter (no swagger-ui). Not in the Boot BOM; 2.8.x line targets Boot 3.x.
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.8.17")
	// OpenAPI 3.x import parsing (plan §13.6, Phase 4). v3-only artifact (no Swagger 2/1
	// parsers). Not in the Boot BOM, version pinned. Exclude the javax (non-jakarta) swagger
	// flavor it pulls: springdoc already provides the jakarta flavor (same io.swagger.v3.oas
	// FQNs), and having both breaks /v3/api-docs (javax.xml.bind ClassNotFoundException).
	implementation("io.swagger.parser.v3:swagger-parser-v3:2.1.44") {
		exclude(group = "io.swagger.core.v3", module = "swagger-core")
		exclude(group = "io.swagger.core.v3", module = "swagger-models")
		exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
	}
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-jpa")
	// Required by Spring Security's Argon2PasswordEncoder (Argon2id password hashing).
	// Not managed by the Spring Boot BOM, so the version is pinned explicitly (plan §4: BC 1.79+).
	implementation("org.bouncycastle:bcprov-jdk18on:1.81")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
	// NOTE: spring-modulith-observability is intentionally absent (re-evaluate in Faz 18,
	// plan §14.3): it CGLIB-proxies every bean of the OPEN `common` module for trace spans,
	// which breaks servlet filters (GenericFilterBean.init() is final → NPE at Tomcat start).
	runtimeOnly("org.springframework.modulith:spring-modulith-runtime")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
