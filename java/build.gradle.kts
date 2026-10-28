plugins { `java-library`; `maven-publish` }
group = "org.whitedoggy"
version = "0.1.0-dev.1"
repositories { mavenCentral() }
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
    withJavadocJar()
}
dependencies {
    implementation("tools.jackson.core:jackson-core:3.1.2")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
}
tasks.test {
    useJUnitPlatform()
    systemProperty("fixtures", rootDir.resolve("../fixtures").absolutePath)
}
publishing { publications { create<MavenPublication>("library") { from(components["java"])
    pom { licenses { license {
        name.set("MIT License")
        url.set("https://opensource.org/license/mit/")
        distribution.set("repo")
    } } } } } }

tasks.withType<AbstractArchiveTask>().configureEach { exclude("**/.gitkeep") }
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

publishing { repositories { maven { name = "localBuild"; url = uri(layout.buildDirectory.dir("repository")) } } }
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:deprecation") }

val smoke = sourceSets.create("smoke") {
    java.srcDir("examples")
    compileClasspath += sourceSets.main.get().output + configurations.runtimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}
tasks.register<JavaExec>("liveSmoke") {
    description = "Opt-in live check; requires -PkeyFile and -Pnickname. Prints no key or character raw data."
    classpath = smoke.runtimeClasspath
    mainClass.set("LiveSmoke")
    doFirst { args(providers.gradleProperty("keyFile").get(), providers.gradleProperty("nickname").get()) }
}
tasks.register<JavaExec>("benchmark") {
    classpath = smoke.runtimeClasspath
    mainClass.set("Benchmark")
}

// Include the root license in binary, sources, and Javadoc artifacts.
tasks.withType<Jar>().configureEach {
    from(rootDir.resolve("../LICENSE")) { into("META-INF") }
}
