import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// 去掉非本平台的动态库
dependencies {
    listOf("linux-aarch64", "linux-x64", "osx-aarch64", "osx-x64", "win-x64").forEach {
        registerTransform(OnnxRuntimeLibraryFilter::class.java) {
            parameters.platform.set(it)
            from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar")
            to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "onnxruntime-${it}-jar")
        }
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "composeApp"
        browser {
            val rootDirPath = project.rootDir.path
            val projectDirPath = project.projectDir.path
            commonWebpackConfig {
                outputFileName = "composeApp.js"
                devServer = (devServer ?: KotlinWebpackConfig.DevServer()).apply {
                    static = (static ?: mutableListOf()).apply {
                        // Serve sources to debug inside browser
                        add(rootDirPath)
                        add(projectDirPath)
                    }
                }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val desktopMain by getting
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)

            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.cmp.image.pick.n.crop)
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.onnxruntime.android)
        }

        val skiaMain by creating {
            dependsOn(commonMain.get())

            iosMain.get().dependsOn(this)
            desktopMain.dependsOn(this)
            wasmJsMain.get().dependsOn(this)
        }

        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)

            val hostOs = System.getProperty("os.name")
            val arch = System.getProperty("os.arch")
            val artifactTypeAttr = when {
                hostOs == "Mac OS X" && arch == "aarch64" -> "onnxruntime-osx-aarch64-jar"
                hostOs == "Mac OS X" && arch == "x64" -> "onnxruntime-osx-x64-jar"
                hostOs == "Linux" && arch == "x64" -> "onnxruntime-linux-x64-jar"
                hostOs.startsWith("Windows") && arch == "x64" -> "onnxruntime-win-x64-jar"
                else -> error("Unsupported hostOs and arch: ${hostOs}, ${arch}")
            }
            val onnxRuntime = dependencies.create(libs.onnxruntime.jvm.get().let {
                "${it.group}:${it.name}:${it.version}"
            }) {
                attributes {
                    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, artifactTypeAttr)
                }
            }
            implementation(onnxRuntime)
        }
        wasmJsMain.dependencies {
            implementation(npm("@tensorflow/tfjs", "^4.22.0"))
        }
    }
}

android {
    namespace = "io.ssttkkl.mahjongdetector"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.ssttkkl.mahjongdetector"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "io.ssttkkl.mahjongdetector.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "io.ssttkkl.mahjongdetector"
            packageVersion = "1.0.0"
        }

        buildTypes.release.proguard {
            this.isEnabled.set(false)
        }
    }
}
