plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ============================================================
// neteasy 内嵌 Node 后端：源码同步 + 依赖安装
// 注意：doFirst/doLast 等嵌套闭包不能直接引用脚本顶层 val
//（Kotlin 会捕获整个脚本对象 this$0，配置缓存无法序列化）。
// 必须在任务配置 lambda 内部先用局部变量承接，闭包只捕获值对象。
// ============================================================

// api-enhanced 仓库路径。
// 解析优先级：环境变量 NETEASY_NODE_PROJECT_DIR > gradle.properties 的
// neteasy.nodeProjectDir > 构建报错提示（不硬编码本机路径，便于协作者使用）
val envNodeProjectDir: String? = System.getenv("NETEASY_NODE_PROJECT_DIR")
val propNodeProjectDir: String = providers.gradleProperty("neteasy.nodeProjectDir")
    .orElse("")
    .get()
val nodeProjectSourceDir: String =
    if (!envNodeProjectDir.isNullOrBlank()) envNodeProjectDir else propNodeProjectDir
val sourceDir = File(nodeProjectSourceDir)
val nodeAssetsDir = layout.projectDirectory.dir("src/main/assets/nodejs-project")
val neteasyNodeProjectDir = rootProject.layout.projectDirectory.dir("nodejs-project")
val nodeDepsMarker = nodeAssetsDir.file(".deps-installed")
// 补丁脚本（由 neteasy 工程维护，随同步任务拷入 assets）
val patchScript = nodeAssetsDir.file("patch-path-to-regexp.js")

/**
 * 把 api-enhanced 源码同步到 assets/nodejs-project（构建产物，已 gitignore）。
 * 排除仓库里的开发/文档/测试文件；plugins/ 必须保留（上传模块引用）。
 */
val syncNodeProject = tasks.register<Sync>("syncNodeProject") {
    group = "neteasy"
    description = "从 api-enhanced 同步 Node 后端源码到 assets/nodejs-project"
    // 局部变量承接脚本引用（配置缓存要求：闭包内不得捕获脚本对象）
    val srcDir = sourceDir
    val srcDirPath = nodeProjectSourceDir
    doFirst {
        if (!srcDir.exists() || !srcDir.isDirectory) {
            throw GradleException(
                "无法定位 api-enhanced 仓库: $srcDirPath\n" +
                    "请通过以下任一方式配置（用正斜杠避免转义）：\n" +
                    "  1) 环境变量 NETEASY_NODE_PROJECT_DIR=D:/path/to/api-enhanced\n" +
                    "  2) neteasy/gradle.properties 中的 neteasy.nodeProjectDir=D:/path/to/api-enhanced",
            )
        }
    }
    from(nodeProjectSourceDir) {
        exclude(
            "**/.git/**",
            "**/.github/**",
            "**/.codegraph/**",
            "test/**",
            "scripts/**",
            "module_example/**",
            "docs/**",
            "public/**",
            "**/*.d.ts",
            "tsconfig.json",
            "eslint.config.js",
            "**/.eslintrc*",
            ".prettierrc",
            ".npmignore",
            ".editorconfig",
            ".dockerignore",
            "Dockerfile",
            ".travis.yml",
            "scf_bootstrap",
            "vercel.json",
            "AGENTS.md",
            ".gitignore",
            "LICENSE",
            "main.test.js",
            "server.test.js",
        )
    }
    // 移动端入口 / pnpm 配置 / 兼容补丁由 neteasy 工程维护（neteasy/nodejs-project/）
    from(neteasyNodeProjectDir) {
        include("mobile-entry.js", ".npmrc", "patch-path-to-regexp.js")
    }
    into(nodeAssetsDir)
}

/**
 * 安装 production 依赖。必须平铺（.npmrc 里 node-linker=hoisted）：
 * pnpm 默认的 symlink 式 node_modules 在 APK assets 解压后会损坏。
 * --ignore-scripts：跳过 prepare 等生命周期脚本（husky 是 devDependency，
 * --prod 不安装它，prepare 必然失败；本项目无强制原生模块，安全）。
 * 仅当 package.json / pnpm-lock.yaml / .npmrc 变化时重跑。
 */
val installNodeProjectDeps = tasks.register<Exec>("installNodeProjectDeps") {
    group = "neteasy"
    description = "安装 nodejs-project 的 production 依赖（平铺 node_modules）"
    dependsOn(syncNodeProject)
    workingDir(nodeAssetsDir)
    inputs.file(nodeAssetsDir.file("package.json"))
    inputs.file(nodeAssetsDir.file("pnpm-lock.yaml"))
    inputs.file(nodeAssetsDir.file(".npmrc"))
    outputs.file(nodeDepsMarker)
    // 局部变量承接脚本引用 + 标准方式判断 Windows（避免 Gradle internal API）
    val markerFile = nodeDepsMarker
    val pnpmCmd = if (System.getProperty("os.name").lowercase().contains("win")) {
        listOf("cmd", "/c", "pnpm", "install", "--prod", "--ignore-scripts")
    } else {
        listOf("pnpm", "install", "--prod", "--ignore-scripts")
    }
    commandLine(pnpmCmd)
    doLast {
        markerFile.asFile.writeText("installed ${System.currentTimeMillis()}")
    }
}

/**
 * 兼容补丁：path-to-regexp@8 用了 \p{ID_Start} 等 Unicode 属性正则，
 * nodejs-mobile 的 V8 不支持（SyntaxError），必须替换为 ASCII 等价写法。
 * 在依赖安装后执行；脚本幂等，重复运行安全。
 */
val patchNodeProjectDeps = tasks.register<Exec>("patchNodeProjectDeps") {
    group = "neteasy"
    description = "给 node_modules 打 nodejs-mobile 兼容补丁（path-to-regexp）"
    dependsOn(installNodeProjectDeps)
    workingDir(nodeAssetsDir)
    inputs.file(nodeDepsMarker) // 安装后 marker 更新 → 触发补丁
    inputs.file(patchScript)
    outputs.file(nodeAssetsDir.file(".patched"))
    val markerFile = nodeDepsMarker
    val patchMarker = nodeAssetsDir.file(".patched")
    val patchCmd = if (System.getProperty("os.name").lowercase().contains("win")) {
        listOf("cmd", "/c", "node", "patch-path-to-regexp.js")
    } else {
        listOf("node", "patch-path-to-regexp.js")
    }
    commandLine(patchCmd)
    doLast {
        patchMarker.asFile.writeText("patched ${System.currentTimeMillis()}")
    }
}

// 构建前先同步、安装依赖并打补丁（覆盖 preDebugBuild / preReleaseBuild 等）
// 用字符串引用任务名，避免 configureEach 闭包捕获脚本对象（配置缓存要求）
tasks.configureEach {
    if (name.startsWith("pre") && name.endsWith("Build")) {
        dependsOn("patchNodeProjectDeps")
    }
}

// ===== 版本号跟随 git tag =====
// 有 tag（如 v0.1.0）时 versionName=0.1.0、versionCode 按 主*10000+次*100+补丁 计算（0.1.0 → 100）；
// 无 tag（本地开发分支）时回退到 0.0.1（versionCode 1）。
// providers.exec 是配置缓存安全的写法（不要用 "git ...".execute()）。
val gitTag: String =
    try {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0")
        }.standardOutput.asText.get().trim().removePrefix("v")
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

    // ===== nodejs-mobile（内嵌 Node.js 后端）=====

    // NDK 版本（本地 SDK 已安装 28.2.13676358）
    ndkVersion = "28.2.13676358"

    // JNI 桥 native-lib.cpp 的 CMake 配置
    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // 打包预编译的 libnode.so 与 libc++_shared.so（libnode/bin/<ABI>/）
    // 注意：libnode.so 依赖 libc++_shared.so，必须一起打包，否则运行时 dlopen 失败
    sourceSets {
        getByName("main") {
            jniLibs.directories += "libnode/bin"
        }
    }

    // 按 ABI 拆分 APK（arm64-v8a / armeabi-v7a / x86_64 各打一个）
    // 同时约束原生库只构建这三个 ABI
    splits {
        abi {
            isEnable = true
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // M3：网络 + 图片加载（后端在 127.0.0.1 本地，OkHttp 轻量封装）
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    // 悬浮 Minibar 磨砂玻璃效果（半透明高斯模糊背景）
    implementation("dev.chrisbanes.haze:haze:1.7.2")
    // 播放系统集成：音频焦点 + MediaSession（锁屏/通知栏/蓝牙耳机控制）+ 前台播放服务
    implementation(libs.androidx.media)
    implementation(libs.androidx.lifecycle.service)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
