plugins {
  id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose")
}
android {
  namespace = "com.playtoolbox.app"; compileSdk = 35
  defaultConfig { applicationId="com.playtoolbox.app"; minSdk=27; targetSdk=35; versionCode=5; versionName="5.0" }
  buildFeatures { compose = true }
  compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget="17" }
}
dependencies {
  val bom = platform("androidx.compose:compose-bom:2025.01.00")
  implementation(bom)
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.activity:activity-compose:1.10.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
