plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace="ru.mirear"
 compileSdk=35
 defaultConfig { applicationId="ru.mirear"; minSdk=26; targetSdk=35; versionCode=8; versionName="0.7.1" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}