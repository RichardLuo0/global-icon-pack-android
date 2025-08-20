import com.github.jk1.license.render.SimpleHtmlReportRenderer
import java.io.FileInputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.dependency.license.report)
  id("kotlin-parcelize")
}

android {
  namespace = "com.richardluo.globalIconPack"
  compileSdk = 36

  val versionProps =
    Properties().apply { load(FileInputStream("${project.rootDir}/version.properties")) }
  val major = versionProps["majorVersion"].toString().toInt()
  val minor = versionProps["minorVersion"].toString().toInt()
  val patch = versionProps["patchVersion"].toString().toInt()

  defaultConfig {
    applicationId = "com.richardluo.globalIconPack"
    minSdk = 27
    targetSdk = 36
    versionCode = major * 10000 + minor * 100 + patch
    versionName = "$major.$minor.$patch"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val keystoreProps =
    Properties().apply { load(FileInputStream("${project.rootDir}/keystore.properties")) }
  signingConfigs {
    create("release") {
      keyAlias = keystoreProps["keyAlias"].toString()
      keyPassword = keystoreProps["keyPassword"].toString()
      storeFile = file(keystoreProps["storeFile"].toString())
      storePassword = keystoreProps["storePassword"].toString()
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    create("debugApp") {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debug")
      isDefault = true
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

  buildFeatures {
    buildConfig = true
    compose = true
  }
  androidResources {
    @Suppress("UnstableApiUsage")
    generateLocaleConfig = true
  }

  dependenciesInfo {
    // Disables dependency metadata when building APKs.
    includeInApk = false
    // Disables dependency metadata when building Android App Bundles.
    includeInBundle = false
  }
}

tasks.register("printVersion") { doLast { println(android.defaultConfig.versionName) } }

dependencies {
  compileOnly(libs.api)
  implementation(libs.core.ktx)
  implementation(libs.activity)
  implementation(libs.viewmodel)
  implementation(libs.viewmodel.savedstate)
  implementation(libs.compose.foundation)
  implementation(libs.preference)
  implementation(libs.material3)
  implementation(libs.material.icons.extended)
  implementation(libs.libsu)
  implementation(libs.documentfile)
  implementation(libs.hiddenapibypass)
  implementation(libs.navigation)
  implementation(libs.reorderable)
  implementation(libs.ui.tooling.preview)
  "debugAppImplementation"(libs.ui.tooling)
}

licenseReport {
  outputDir = project.layout.projectDirectory.dir("../licenses").asFile.path
  renderers = arrayOf(SimpleHtmlReportRenderer("third-party-libs.html"))
}
