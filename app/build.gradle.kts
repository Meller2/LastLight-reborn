import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val llPackStageProp: String = providers.gradleProperty("llPackStage")
    .orElse(providers.environmentVariable("LL_PACK_STAGE"))
    .orElse("")
    .get()
    .trim()
val llPackStageDir: File? = llPackStageProp.takeIf { it.isNotEmpty() }?.let { file(it) }?.takeIf { it.isDirectory }
val hasOndevicePacker: Boolean = llPackStageDir != null

android {
    namespace = "dev.lastlight.reborn"
    compileSdk = 36
    ndkVersion = providers.gradleProperty("llNdkVersion").getOrElse("29.0.14206865")

    defaultConfig {
        applicationId = "dev.lastlight.reborn"
        minSdk = 33
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-alpha"

        buildConfigField("boolean", "HAS_ONDEVICE_PACKER", hasOndevicePacker.toString())

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_STL=c++_static",
                    "-DNX_PEANUTS_ROOT=${rootProject.projectDir.absolutePath.replace("\\", "/")}/peanuts-native"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isDebuggable = true
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
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidResources {
        noCompress += "zip"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

// Optional Phase 3 on-device packer stage (-PllPackStage=<stageDir> / LL_PACK_STAGE):
// Bundles CPython 3.14, libnxpack.so, libzbicdec.so, libcra.so, libdmsh_xlate.so,
// libSPIRV-Tools-shared.so, python.zip, and Metro tools.zip right into the APK.
if (llPackStageDir != null) {
    val stage = llPackStageDir
    val peanutsRoot = file("${rootProject.projectDir}/peanuts-native")
    val packOut = layout.buildDirectory.dir("llpack")

    val requiredFiles = listOf(
        "lib/libpython3.14.so",
        "lib/libnxpack.so",
        "lib/libzbicdec.so",
        "lib/libcra.so",
        "lib/libdmsh_xlate.so",
        "lib/libSPIRV-Tools-shared.so",
        "python/lib/python3.14/os.py"
    )
    requiredFiles.forEach { rel ->
        if (!File(stage, rel).isFile) {
            throw GradleException("llPackStage ($stage) is missing required file: $rel")
        }
    }

    val llPackJni = tasks.register<Sync>("llPackJni") {
        from(File(stage, "lib")) {
            include(
                "libpython3.14.so",
                "libnxpack.so",
                "libzbicdec.so",
                "libcra.so",
                "libdmsh_xlate.so",
                "libSPIRV-Tools-shared.so"
            )
        }
        into(packOut.map { it.dir("jni/arm64-v8a") })
    }

    val llPackPython = tasks.register<Zip>("llPackPython") {
        from(File(stage, "python")) {
            exclude("**/__pycache__/**")
            exclude("**/site-packages/zbic.*", "**/site-packages/zbic-*/**")
        }
        archiveFileName.set("python.zip")
        destinationDirectory.set(packOut.map { it.dir("zips") })
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    val llPackTools = tasks.register<Zip>("llPackTools") {
        from(peanutsRoot) {
            include(
                "scripts/*.py",
                "dm/tools/*.py",
                "dm/tools/rewriter_equiv/*",
                "dm/games/*.py",
                "dm/games/0100F0400E850000/**",
                "dm/games/*/patches.py",
                "dm/gfx/nvn/*.h",
                "dm/peanuts/profile/mod_memory.tsv",
                "dm/peanuts/profile/gen_runtime_profile.py",
                "dm/gfx/tools/*.py",
                "dm/gfx/tools/*.lock",
                "dm/gfx/tools/*.metal",
                "dm/gfx/tools/*.tsv",
                "dm/gfx/tools/dmsh_xlate/Program.cs",
                "dm/gfx/tools/dmsh_xlate/*.csproj",
                "dm/ios/tools/ios_metal.py",
                "dm/third_party/ryujinx/src/**/*.cs",
                "dm/third_party/ryujinx/src/**/*.csproj",
                "dm/gfx/tools/dmsh_xlate_android/*.cs",
                "dm/gfx/tools/dmsh_xlate_android/*.csproj"
            )
            exclude(
                "dm/games/*/README.md",
                "dm/games/*/vk_prewarm.bin",
                "dm/games/*/vk_programs.bin",
                "**/bin/**",
                "**/obj/**",
                "**/__pycache__/**",
                "**/.DS_Store"
            )
        }
        archiveFileName.set("tools.zip")
        destinationDirectory.set(packOut.map { it.dir("zips") })
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }

    val llPackAssets = tasks.register("llPackAssets") {
        dependsOn(llPackPython, llPackTools)
        val zipsDir = packOut.map { it.dir("zips") }
        val assetsPackDir = packOut.map { it.dir("assets/pack") }
        inputs.files(llPackPython, llPackTools)
        outputs.dir(assetsPackDir)
        doLast {
            val outDir = assetsPackDir.get().asFile
            outDir.deleteRecursively()
            outDir.mkdirs()
            val md = MessageDigest.getInstance("SHA-256")
            listOf("python.zip", "tools.zip").forEach { name ->
                val src = zipsDir.get().file(name).asFile
                val bytes = src.readBytes()
                md.update(name.toByteArray())
                md.update(bytes)
                Files.copy(src.toPath(), File(outDir, name).toPath())
            }
            val hex = md.digest().joinToString("") { "%02x".format(it) }.take(16)
            File(outDir, "version.txt").writeText("$hex\n")
        }
    }

    android.sourceSets.getByName("main") {
        jniLibs.srcDir(packOut.map { it.dir("jni") })
        assets.srcDir(packOut.map { it.dir("assets") })
    }
    tasks.named("preBuild") {
        dependsOn(llPackJni, llPackAssets)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
