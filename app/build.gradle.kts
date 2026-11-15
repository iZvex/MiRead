plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace="ru.mirear"
 compileSdk=35
 defaultConfig { applicationId="ru.mirear"; minSdk=26; targetSdk=33; versionCode=14; versionName="0.7.7-diagnostics" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}