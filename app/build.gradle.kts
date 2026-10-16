import com.android.build.api.dsl.ApplicationExtension
import com.github.jk1.license.render.SimpleHtmlReportRenderer
import java.io.FileInputStream
import java.util.Properties
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.dependency.license.report)
  alias(libs.plugins.jetbrains.kotlin.serialization)
  id("kotlin-parcelize")
}

extensions.configure<ApplicationExtension> {
  namespace = "com.richardluo.globalIconPack"
  compileSdk = 37

  val versionProps =
    Properties().apply { load(FileInputStream("${project.rootDir}/version.properties")) }
  val major = versionProps["majorVersion"].toString().toInt()
  val minor = versionProps["minorVersion"].toString().toInt()
  val patch = versionProps["patchVersion"].toString().toInt()

  defaultConfig {
    applicationId = "com.richardluo.globalIconPack"
    minSdk = 27
    targetSdk = 37
    versionCode = major * 10000 + minor * 100 + patch
    versionName = "$major.$minor.$patch"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // Release uses the auto-generated debug keystore so anyone can build without a
    // separate keystore.properties / keystore file.  Replace with your own signing
    // config when publishing to production.
    getByName("debug") {
      // Uses the default Android debug keystore – no extra setup required.
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
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

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

tasks.register("printVersion") {
  description = "printVersion"
  doLast {
    val androidExt = project.extensions.getByType(ApplicationExtension::class.java)
    println(androidExt.defaultConfig.versionName)
  }
}

dependencies {
  compileOnly(libs.libxposed.api)
  implementation(libs.libxposed.service)
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
  implementation(libs.navigation3.ui)
  implementation(libs.navigation3.runtime)
  implementation(libs.lifecycle.viewmodel.navigation3)
  implementation(libs.reorderable)
  implementation(libs.ui.tooling.preview)
  "debugAppImplementation"(libs.ui.tooling)
}

licenseReport {
  outputDir = project.layout.projectDirectory.dir("../licenses").asFile.path
  renderers = arrayOf(SimpleHtmlReportRenderer("third-party-libs.html"))
}
