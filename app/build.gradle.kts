import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}


android {
    namespace = "com.rk.application"
    compileSdk = 37


    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    
    signingConfigs {
        create("release") {
            val isGITHUB_ACTION = System.getenv("GITHUB_ACTIONS") == "true"
            
            val propertiesFilePath = if (isGITHUB_ACTION) {
                "/tmp/signing.properties"
            } else {
                "/home/rohit/Android/xed-signing/signing.properties"
            }
            
            val propertiesFile = File(propertiesFilePath)
            if (propertiesFile.exists()) {
                val properties = Properties()
                properties.load(propertiesFile.inputStream())
                keyAlias = properties["keyAlias"] as String?
                keyPassword = properties["keyPassword"] as String?
                storeFile = if (isGITHUB_ACTION) {
                    File("/tmp/xed.keystore")
                } else {
                    (properties["storeFile"] as String?)?.let { File(it) }
                }

                storePassword = properties["storePassword"] as String?
            }

            if (storePassword.isNullOrBlank() || keyPassword.isNullOrBlank() || keyAlias.isNullOrBlank() || storeFile == null) {
                println("Release signing incomplete, falling back to debug keystore")
                storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
                storePassword = "testkey"
                keyAlias = "testkey"
                keyPassword = "testkey"
            }
        }
        getByName("debug") {
            storeFile = file(layout.buildDirectory.dir("../testkey.keystore"))
            storePassword = "testkey"
            keyAlias = "testkey"
            keyPassword = "testkey"
        }
    }
    
    
    buildTypes {
        release{
            // P1: enable R8 shrink + obfuscate to cut APK size and make the
            // release harder to reverse-engineer. Keep rules for termux/Coil/
            // coroutines/Compose/plugin live in proguard-rules.pro.
            isMinifyEnabled = true
            isCrunchPngs = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            resValue("string","app_name","Shellix")
        }
        debug{
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string","app_name","Shellix-Debug")
        }
    }

    
    defaultConfig {
        applicationId = "com.shellix.terminal"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
        vectorDrawables {
            useSupportLibrary = true
        }
        // P4: ship only English string/resources. The app UI is English-only;
        // the bundled ar/zh translations in core/resources are dropped to shrink
        // the APK (users fall back to English, which is the default).
        resourceConfigurations += listOf("en")
        ndk {
            // Shellix targets modern Android devices (arm64-v8a). Building/packaging
            // the other ABIs only inflates the APK and the NDK compile time.
            abiFilters += listOf("arm64-v8a")
        }
    }


    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    buildFeatures {
        viewBinding = true
        compose = true
        resValues = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":core:main"))
    implementation(libs.androidx.core.ktx)
}
