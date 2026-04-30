plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.qyvos.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.qyvos.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }

        python {
            pip {
                install("openai>=1.0.0")
                install("pydantic>=2.0.0")
                install("httpx>=0.27.0")
                install("tiktoken>=0.7.0")
                install("tomli>=2.0.0")
                install("tenacity>=9.0.0")
                install("loguru>=0.7.0")
                install("colorama>=0.4.6")
                install("requests>=2.32.0")
                install("Pillow>=10.0.0")
                install("beautifulsoup4>=4.12.0")
                install("lxml>=5.0.0")
                install("html2text>=2024.0.0")
                install("aiofiles>=23.2.0")
            }
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.findProperty("KEYSTORE_PATH")?.toString() ?: "/tmp/qyvos-release.keystore")
            storePassword = project.findProperty("KEYSTORE_PASS")?.toString() ?: "qyvos2024"
            keyAlias = project.findProperty("KEY_ALIAS")?.toString() ?: "qyvos-release"
            keyPassword = project.findProperty("KEY_PASS")?.toString() ?: "qyvos2024"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    sourceSets {
        getByName("main") {
            python.setSrcDirs(listOf("src/main/python"))
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.gson)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.security.crypto)
    implementation(libs.browser)
    implementation(libs.markwon.core)
    implementation(libs.markwon.strikethrough)
    implementation(libs.markwon.tables)
    implementation(libs.markwon.tasklist)
    implementation(libs.lottie)
    implementation(libs.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.circleimageview)
}
