plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hlju.funlinkbluetooth.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform("androidx.compose:compose-bom:2026.03.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.navigation3:navigation3-runtime:1.1.0-rc01")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")

    // MIUIX
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.0")
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("io.github.kyant0:capsule:2.1.3")
    implementation("top.yukonga.miuix.kmp:miuix-shapes:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.mockk:mockk:1.14.9")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
