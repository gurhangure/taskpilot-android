plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Supplying a private IPv4 host explicitly opts this debug build into local HTTP.
val debugHttpHost = providers.gradleProperty("taskpilot.debugHttpHost").orElse("")
val apiBaseUrl = providers.gradleProperty("taskpilot.apiBaseUrl").orElse("")

/** Escapes a non-secret configuration value for a generated Java string literal. */
fun javaString(value: String): String = "\"" + value
    .replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""

/** Generates a debug-only policy permitting cleartext to at most one explicit local host. */
abstract class GenerateDebugNetworkPolicy : DefaultTask() {
    @get:Input
    abstract val httpHost: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** Rejects public hosts and writes the narrow domain exception under generated resources. */
    @TaskAction
    fun generate() {
        val host = httpHost.get()
        val octets = host.split('.').mapNotNull { it.toIntOrNull() }
        require(host.isEmpty() || (
            octets.size == 4 && octets.all { it in 0..255 } &&
                octets.joinToString(".") == host &&
                (host == "127.0.0.1" || octets[0] == 10 ||
                    (octets[0] == 172 && octets[1] in 16..31) ||
                    (octets[0] == 192 && octets[1] == 168))
            )) { "taskpilot.debugHttpHost must be an explicit private IPv4 address." }
        val domain = if (host.isEmpty()) "" else
            "<domain-config cleartextTrafficPermitted=\"true\"><domain includeSubdomains=\"false\">$host</domain></domain-config>"
        val output = outputDirectory.file("xml/debug_network_security_config.xml").get().asFile
        output.parentFile.mkdirs()
        output.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <base-config cleartextTrafficPermitted="false" />
                $domain
            </network-security-config>
        """.trimIndent())
    }
}

val generateDebugNetworkPolicy = tasks.register<GenerateDebugNetworkPolicy>("generateDebugNetworkPolicy") {
    httpHost.set(debugHttpHost)
    outputDirectory.set(layout.buildDirectory.dir("generated/debugNetworkPolicy/res"))
}

android {
    namespace = "com.gurhangure.taskpilot"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.gurhangure.taskpilot"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "API_BASE_URL", javaString(apiBaseUrl.get()))
        buildConfigField("String", "DEBUG_HTTP_HOST", "\"\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val host = debugHttpHost.get()
            val url = apiBaseUrl.get().ifEmpty { if (host.isEmpty()) "" else "http://$host:5080/" }
            buildConfigField("String", "API_BASE_URL", javaString(url))
            buildConfigField("String", "DEBUG_HTTP_HOST", javaString(host))
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(
            generateDebugNetworkPolicy, GenerateDebugNetworkPolicy::outputDirectory,
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
