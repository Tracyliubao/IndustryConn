plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    kotlin("kapt")
}

android {
    namespace = "com.tracy.industry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tracy.industry"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        dataBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    /*rxjava  rxbinding  sqlbrite*/
    implementation("io.reactivex.rxjava2:rxjava:${project.property("RXJAVA_VERSION")}")
    implementation("io.reactivex.rxjava2:rxandroid:${project.property("RXANDROID_VERSION")}")
    implementation("androidx.work:work-runtime-ktx:${project.property("WORK_VERSION")}")
    /*rx permission*/
//    implementation("com.github.tbruyelle:rxpermissions:${project.property("RXPERMISSION_VERSION")}")
    // Room数据库
    implementation("androidx.room:room-runtime:${project.property("ROOM_VERSION")}")
    // kapt适配Kotlin，Java项目用annotationProcessor
    kapt("androidx.room:room-compiler:${project.property("ROOM_VERSION")}")
    // Room Kotlin扩展（支持协程，避免主线程操作）
    implementation("androidx.room:room-ktx:${project.property("ROOM_VERSION")}")

    // MMKV 工业级存储
    implementation("com.tencent:mmkv:1.3.0")
    // Gson JSON解析
    implementation("com.google.code.gson:gson:2.10.1")
    // 权限相关（可选，导出文件需要）
    implementation("com.github.permissions-dispatcher:permissionsdispatcher:4.9.1")
}
