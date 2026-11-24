import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

android {
    compileSdk = 37
    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "com.utc.color.blockads.core.enginebridge"
}

abstract class VerifyNativeCore : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadata: ConfigurableFileCollection
    @get:Internal abstract val repositoryRoot: DirectoryProperty
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val binaries: ConfigurableFileCollection

    @TaskAction
    fun verify() {
        val guidance =
            "Rust/JNI binaries are missing or stale. Run scripts/build-native.sh before building the APK " +
                "(and `git submodule update --init` if open-adblock-core is empty)."
        val stamp = metadata.singleFile
        val digest = MessageDigest.getInstance("SHA-256")
        val root = repositoryRoot.get().asFile.toPath()
        sources.files
            .sortedBy { root.relativize(it.toPath()).toString().replace('\\', '/') }
            .forEach { source ->
                digest.update(
                    root
                        .relativize(source.toPath())
                        .toString()
                        .replace('\\', '/')
                        .toByteArray(Charsets.UTF_8)
                )
                digest.update(0.toByte())
                digest.update(source.readBytes())
                digest.update(0.toByte())
            }
        fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        val fingerprint = hex(digest.digest())
        fun current(): Boolean {
            if (!stamp.isFile || binaries.files.any { !it.isFile }) return false
            val properties = Properties().apply { stamp.inputStream().use { load(it) } }
            return properties.getProperty("sources") == fingerprint && binaries.files.all { binary ->
                properties.getProperty(binary.parentFile.name) ==
                    hex(MessageDigest.getInstance("SHA-256").digest(binary.readBytes()))
            }
        }
        if (!current()) {
            logger.lifecycle("Updating Rust/JNI libraries for all Android ABIs…")
            execOperations.exec {
                workingDir(repositoryRoot.get().asFile)
                commandLine("bash", "scripts/build-native.sh")
                // Android Studio launched from Finder may not inherit the shell's cargo PATH.
                environment("PATH", "${System.getProperty("user.home")}/.cargo/bin:${System.getenv("PATH")}")
            }
        }
        check(current()) { guidance }
    }
}

val verifyNativeCore =
    tasks.register<VerifyNativeCore>("verifyNativeCore") {
        repositoryRoot.set(rootProject.layout.projectDirectory)
        sources.from(
            rootProject.fileTree("open-adblock-core/crates") {
                include("**/*.rs", "**/*.js", "**/Cargo.toml")
                exclude("**/tests/**", "**/benches/**")
            },
            rootProject.fileTree("open-adblock-core/vendor") { include("**/*.rs", "**/Cargo.toml") },
            rootProject.fileTree("open-adblock-core/bindings/java/src/main") { include("**/*.java", "**/*.pro") },
            rootProject.files(
                "open-adblock-core/Cargo.toml",
                "open-adblock-core/Cargo.lock",
                "open-adblock-core/rust-toolchain.toml",
                "open-adblock-core/scripts/build-android.sh",
                "open-adblock-core/scripts/check-jni-exports.py",
                "scripts/build-native.sh",
                "scripts/native-stamp.py",
            ),
        )
        metadata.from(layout.projectDirectory.file("src/main/jniLibs/.build.properties"))
        binaries.from(
            listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86").map {
                layout.projectDirectory.file("src/main/jniLibs/$it/libopenadblock_jni.so")
            }
        )
    }

tasks.named("preBuild") { dependsOn(verifyNativeCore) }

dependencies {
    // Public Java API of the core; `api` so modules using EngineBridge see the callback types.
    api("io.github.tothanggtvt.openadblock:openadblock-core")
    implementation(project(":core:common"))
}
