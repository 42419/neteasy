import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ============================================================
// neteasy 内嵌 Rust 后端（https://github.com/42419/neteasy-backend）
// 构建时从该仓库的 GitHub Release 下载预编译好的三个 ABI 动态库，逐个校验 SHA-256 后
// 放进 build/backend-jniLibs/<abi>/libneteasy_backend.so 并打进 APK。
// 升级后端：同时改 gradle.properties 里的 neteasy.backendVersion 和三个 SHA-256。
// 本地调试后端：把环境变量 NETEASY_BACKEND_JNILIBS_DIR（或 gradle.properties 的
// neteasy.backendJniLibsDir）指向 `cargo ndk -o jniLibs build --release` 的产物目录，即可跳过下载。
// ============================================================

val backendAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

// 解析优先级：环境变量 > gradle.properties；都没配就走下载
val backendJniLibsOverride: String? =
    System.getenv("NETEASY_BACKEND_JNILIBS_DIR")?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("neteasy.backendJniLibsDir").orNull?.takeIf { it.isNotBlank() }
val backendJniLibsDir = layout.buildDirectory.dir("backend-jniLibs")

/**
 * 下载并校验后端动态库。
 * 注意：任务类里只用注入的服务和 Property，不引用脚本顶层 val（配置缓存要求，见 Gradle 文档）。
 */
abstract class FetchBackendLibs : DefaultTask() {
    /** 形如 https://github.com/42419/neteasy-backend/releases/download/v0.1.1 */
    @get:Input
    abstract val baseUrl: Property<String>

    /** ABI → 期望的 SHA-256（小写十六进制） */
    @get:Input
    abstract val sha256ByAbi: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        sha256ByAbi.get().forEach { (abi, expected) ->
            val target = File(out, "$abi/libneteasy_backend.so")
            target.parentFile.mkdirs()
            val part = File(out, "$abi/libneteasy_backend.so.part")
            val url = "${baseUrl.get()}/libneteasy_backend-$abi.so"
            logger.lifecycle("下载 $url")
            URI(url).toURL().openStream().use { input ->
                part.outputStream().use { input.copyTo(it) }
            }
            val actual =
                MessageDigest.getInstance("SHA-256")
                    .digest(part.readBytes())
                    .joinToString("") { "%02x".format(it) }
            check(actual.equals(expected, ignoreCase = true)) {
                "后端动态库校验失败：$abi\n  期望 $expected\n  实际 $actual\n" +
                    "（升级后端版本后需要同步更新 gradle.properties 里的 neteasy.backendSha256.*）"
            }
            check(part.renameTo(target)) { "无法写入 ${target.path}" }
        }
    }
}

val fetchBackendLibs = tasks.register<FetchBackendLibs>("fetchBackendLibs") {
    group = "neteasy"
    description = "从 neteasy-backend 的 GitHub Release 下载并校验三个 ABI 的 libneteasy_backend.so"
    val version = providers.gradleProperty("neteasy.backendVersion")
    baseUrl.set(version.map { "https://github.com/42419/neteasy-backend/releases/download/$it" })
    sha256ByAbi.set(
        provider {
            backendAbis.associateWith { abi ->
                providers.gradleProperty("neteasy.backendSha256.$abi").orNull
                    ?: error("缺少 gradle.properties 配置：neteasy.backendSha256.$abi")
            }
        }
    )
    outputDir.set(backendJniLibsDir)
}

// 没有本地覆盖目录时，任何构建开始前先把后端库准备好
if (backendJniLibsOverride == null) {
    tasks.configureEach {
        if (name.startsWith("pre") && name.endsWith("Build")) {
            dependsOn(fetchBackendLibs)
        }
    }
}

// ===== 版本号跟随 git tag =====
// 有 tag（如 v0.1.0）时 versionName=0.1.0、versionCode 按 主*10000+次*100+补丁 计算（0.1.0 → 100）；
// 无 tag（本地开发分支）时回退到 0.0.1（versionCode 1）。
// providers.exec 是配置缓存安全的写法（不要用 "git ...".execute()）。
val gitTag: String =
    try {
        val result =
            providers.exec {
                commandLine("git", "describe", "--tags", "--abbrev=0")
                // 配置缓存会在“存储缓存”阶段单独重跑这个 ValueSource 做指纹快照，
                // 那一步是 Gradle 内部机制触发的，不经过这里的 try/catch——
                // 没有 tag 时 git 以退出码 128 失败会直接把配置缓存写入过程炸掉。
                // 关掉非零退出码抛异常，改成拿到的 exitValue 手动判断，
                // 保证这个 provider 在“无 tag”场景下永远不抛异常，配置缓存存取都安全。
                isIgnoreExitValue = true
            }
        if (result.result.get().exitValue == 0) {
            result.standardOutput.asText.get().trim().removePrefix("v")
        } else {
            ""
        }
    } catch (e: Exception) {
        ""
    }
val appVersionName: String = gitTag.ifBlank { "0.0.1" }
val appVersionCode: Int =
    appVersionName.split(".").let { parts ->
        val nums = parts.mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() } + listOf(0, 0, 0)
        (nums[0] * 10000 + nums[1] * 100 + nums[2]).coerceAtLeast(1)
    }

android {
    namespace = "top.yunov.neteasy"
    // core-ktx 1.19.0 要求 compileSdk 37（SDK 已安装 android-37.0 平台）
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "top.yunov.neteasy"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // 发布签名：优先用环境变量指定的固定 keystore（CI 从 GitHub Secrets 解出来传进来）。
        // 本地没配这几个环境变量时，release 构建会退回到 debug 签名（能编译能装，
        // 但每台机器的 debug 签名不一样，不能拿来当正式对外发布的包）。
        create("release") {
            val keystorePath = System.getenv("NETEASY_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("NETEASY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NETEASY_KEY_ALIAS")
                keyPassword = System.getenv("NETEASY_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 开启 R8 混淆 + 资源裁剪，压缩 APK（配合 src/main/keepRules/rules.keep 的 keep 规则）
            optimization {
                enable = true
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                // src/main/keepRules 下的 *.keep 由 AGP 自动合并进 R8（rules.keep 里是项目级 keep）
            )
            signingConfig =
                if (System.getenv("NETEASY_KEYSTORE_PATH").isNullOrBlank()) {
                    signingConfigs.getByName("debug")
                } else {
                    signingConfigs.getByName("release")
                }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // ===== 内嵌 Rust 后端 =====
    // 打包预编译的 libneteasy_backend.so（<ABI>/libneteasy_backend.so），来源见文件顶部说明
    sourceSets {
        getByName("main") {
            jniLibs.directories += (backendJniLibsOverride ?: backendJniLibsDir.get().asFile.path)
        }
    }

    // 按 ABI 拆分 APK（arm64-v8a / armeabi-v7a / x86_64 各打一个）
    // 同时约束原生库只构建这三个 ABI
    // 仪器测试（connectedAndroidTest）在模拟器上跑时用 -Pneteasy.noAbiSplits 关掉拆分，只出一个 APK
    splits {
        abi {
            isEnable = !providers.gradleProperty("neteasy.noAbiSplits").isPresent
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    // material-icons-core（Home/Search/Pause/PlayArrow/ArrowBack 等基础图标）
    implementation("androidx.compose.material:material-icons-core")
    // material-icons-extended：MusicNote/GraphicEq/Stars 等更丰富的图标（个人资料、封面占位等）
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // 封面动态取色：Palette 提取种子色 + material-kolor 生成整套 M3 配色
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.materialkolor:material-kolor:4.0.5")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // M3：网络 + 图片加载（后端在 127.0.0.1 本地，OkHttp 轻量封装）
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    // 播放系统集成：音频焦点 + MediaSession（锁屏/通知栏/蓝牙耳机控制）+ 前台播放服务
    implementation(libs.androidx.media)
    implementation(libs.androidx.lifecycle.service)
    // 只给设置页返回按钮用，测完崩不崩就知道要不要留
    implementation(libs.backdrop)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
