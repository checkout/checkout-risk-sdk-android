plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

kotlin {
    explicitApi()
}

android {
    namespace = "com.checkout.risk"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            buildConfigField(
                "Boolean",
                "DEFAULT_LOGCAT_MONITORING_ENABLED",
                "false",
            )
        }

        debug {
            buildConfigField(
                "Boolean",
                "DEFAULT_LOGCAT_MONITORING_ENABLED",
                "true",
            )
        }

        buildFeatures {
            buildConfig = true
        }

        publishing {
            multipleVariants {
                includeBuildTypeValues("release", "debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.11.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.fingerprint.android:pro:2.3.4")
    implementation("com.checkout:checkout-sdk-event-logger-android:1.0.1")
    implementation("com.google.android.material:material:1.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0-RC")

    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>("release") {
                from(components.getByName("release"))
                groupId = "com.github.checkout"
                artifactId = "checkout-risk-sdk-android"
                version = "1.0.5"
            }
        }
    }
}
