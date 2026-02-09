import java.util.Properties

// Read API keys from local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize")
    alias(libs.plugins.objectbox)
}

android {
    namespace = "com.soulmate"
    compileSdk = 34

    defaultConfig {
        // Legacy keys (can be removed later)
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("OPENAI_API_KEY") ?: ""}\"")
        
        // Doubao LLM (火山引擎方舟)
        buildConfigField("String", "DOUBAO_API_KEY", "\"${localProperties.getProperty("DOUBAO_API_KEY") ?: ""}\"")
        buildConfigField("String", "DOUBAO_MODEL_ID", "\"${localProperties.getProperty("DOUBAO_MODEL_ID") ?: ""}\"")
        buildConfigField("String", "DOUBAO_BASE_URL", "\"${localProperties.getProperty("DOUBAO_BASE_URL") ?: "https://ark.cn-beijing.volces.com/api/v3"}\"")
        
        // Doubao 多模态端点 (Phase 1)
        buildConfigField("String", "DOUBAO_CHAT_ENDPOINT_ID", "\"${localProperties.getProperty("DOUBAO_CHAT_ENDPOINT_ID") ?: ""}\"")
        buildConfigField("String", "DOUBAO_VISION_ENDPOINT_ID", "\"${localProperties.getProperty("DOUBAO_VISION_ENDPOINT_ID") ?: ""}\"")
        buildConfigField("String", "DOUBAO_IMAGE_GEN_ENDPOINT_ID", "\"${localProperties.getProperty("DOUBAO_IMAGE_GEN_ENDPOINT_ID") ?: ""}\"")
        buildConfigField("String", "DOUBAO_EMBEDDING_ENDPOINT_ID", "\"${localProperties.getProperty("DOUBAO_EMBEDDING_ENDPOINT_ID") ?: ""}\"")
        
        // Aliyun ASR
        buildConfigField("String", "ALIYUN_ASR_APP_KEY", "\"${localProperties.getProperty("ALIYUN_ASR_APP_KEY") ?: ""}\"")
        buildConfigField("String", "ALIYUN_ACCESS_KEY_ID", "\"${localProperties.getProperty("ALIYUN_ACCESS_KEY_ID") ?: ""}\"")
        buildConfigField("String", "ALIYUN_ACCESS_KEY_SECRET", "\"${localProperties.getProperty("ALIYUN_ACCESS_KEY_SECRET") ?: ""}\"")
        
        // Doubao Embedding (火山引擎向量模型) - 保留旧配置兼容
        buildConfigField("String", "DOUBAO_EMBEDDING_MODEL_ID", "\"${localProperties.getProperty("DOUBAO_EMBEDDING_MODEL_ID") ?: ""}\"")
        buildConfigField("String", "DOUBAO_EMBEDDING_API_KEY", "\"${localProperties.getProperty("DOUBAO_EMBEDDING_API_KEY") ?: ""}\"")
        applicationId = "com.soulmate"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.soulmate.HiltTestRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            pickFirst("lib/**/libobjectbox-jni.so")
            pickFirst("lib/**/libc++_shared.so")
        }
    }
}

dependencies {
    // Aliyun NUI SDK for ASR
    implementation(files("libs/nuisdk-release.aar"))
    // implementation(files("libs/xmovdigitalhuman-v0.0.1.aar"))
    // implementation("javax.vecmath:vecmath:1.5.2")
    // implementation("org.msgpack:msgpack-core:0.9.3")
    // implementation("io.socket:socket.io-client:2.1.0")
    
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.cardview:cardview:1.0.0")
    
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    
    // Coil - Image Loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // ViewModel & LiveData
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    
    // Hilt - Dependency Injection
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // Room 依赖已移除 - 使用 ObjectBox 作为本地数据库
    
    // WorkManager - Background Tasks (Critical for Heartbeat Protocol)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)
    
    // Coroutines & Flow
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    
    // Retrofit & OkHttp (for LLM API calls)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    
    // Gson
    implementation(libs.gson)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Hilt Testing
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)

    // MediaPipe - Edge AI Text Classification (Emotion Analysis)
    implementation("com.google.mediapipe:tasks-text:0.20230731")
    
    // ObjectBox
    implementation(libs.objectbox.android)
    implementation(libs.objectbox.kotlin)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    
    // LangChain4j dependencies removed - using Doubao via Retrofit instead

    // debugImplementation(libs.objectbox.browser)
}
