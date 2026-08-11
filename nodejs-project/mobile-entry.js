// ============================================================
// neteasy 移动端入口：在 Android 沙箱中启动 api-enhanced 后端
// 由 Gradle 的 SyncNodeProject 任务同步进 assets/nodejs-project
// ============================================================
'use strict'

const fs = require('fs')
const path = require('path')
const os = require('os')

// 必须在 require 任何其他模块之前设置：
// Android 沙箱里 /tmp 不可写，os.tmpdir() 必须重定向到应用私有目录
const projectDir = path.dirname(process.argv[1]) // filesDir/nodejs-project
const appDataDir = path.dirname(projectDir) // filesDir
const tmpDir = path.join(appDataDir, 'tmp')
fs.mkdirSync(tmpDir, { recursive: true })

// 1) 环境变量（双保险，PC/桌面端生效；nodejs-mobile 在 Android 上不认 env）
process.env.TMPDIR = tmpDir
process.env.TEMP = tmpDir
process.env.TMP = tmpDir
process.env.HOME = appDataDir

// 2) monkey-patch os.tmpdir()：nodejs-mobile 在 Android 上 os.tmpdir() 返回 /tmp
//    （TMPDIR 环境变量不生效），而 main.js / generateConfig.js / util/request.js /
//    server.js 都在 require 时同步调用 os.tmpdir() 读写 anonymous_token、
//    xeapi_public_key 与上传临时文件。覆盖 os.tmpdir 强制所有模块拿到可写私有目录。
os.tmpdir = () => tmpDir

// anonymous_token：util/request.js 在 require 时同步读取，必须先建好空文件
const tokenFile = path.join(tmpDir, 'anonymous_token')
if (!fs.existsSync(tokenFile)) {
  fs.writeFileSync(tokenFile, '', 'utf-8')
}

console.log('[mobile-entry] backend starting, projectDir =', projectDir)
console.log('[mobile-entry] tmpdir =', tmpDir)

/**
 * 预热：先获取 xeapi 公钥，再调用 generateConfig。
 * 上游 generateConfig 的顺序是先 register_anonimous（xeapi 加密，依赖公钥）
 * 再获取公钥——首次启动必然自举失败。这里仅在公钥缺失时先取一次写盘。
 * 注意：getXeapiPublicKey 需要 deviceId 才会返回完整公钥（含 sk）。
 */
async function prewarmXeapiKey() {
  const keyPath = path.join(tmpDir, 'xeapi_public_key')
  if (fs.existsSync(keyPath)) return // 已有缓存，跳过（generateConfig 会自行刷新）
  try {
    const { generateDeviceId } = require('./util/index')
    const { getXeapiPublicKey } = require('./util/xeapiKey')
    global.deviceId = global.deviceId || generateDeviceId()
    const publicKey = await getXeapiPublicKey({}, global.deviceId)
    fs.writeFileSync(keyPath, JSON.stringify(publicKey), 'utf-8')
    console.log('[mobile-entry] xeapi public key ready')
  } catch (err) {
    console.error('[mobile-entry] prewarm xeapi key failed:', err && err.message)
  }
}

prewarmXeapiKey()
  .then(() => require('./generateConfig')())
  .catch((err) => {
    console.error('[mobile-entry] generateConfig failed:', err && err.message)
  })
  .finally(() => {
    try {
      const { serveNcmApi } = require('./server')
      serveNcmApi({
        checkVersion: false, // 绕开 child_process.exec（nodejs-mobile 不支持子进程）
        port: 19800,
        host: '127.0.0.1', // 只绑本机，不暴露局域网
      })
    } catch (err) {
      // 模块加载异常（如 tmpdir 不可写）不能让它击穿 promise 链导致进程崩溃
      console.error('[mobile-entry] server load failed:', err && err.stack)
    }
  })
