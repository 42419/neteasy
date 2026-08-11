#!/usr/bin/env node
// ============================================================
// 兼容补丁：path-to-regexp@8 使用 \p{ID_Start}/\p{ID_Continue} Unicode 属性正则，
// 而 nodejs-mobile 的 V8 不支持（编译时禁用），启动 express 5 会直接 SyntaxError。
// 这里替换为兼容的 ASCII 等价写法。
//
// 说明：
// - 这些正则只用于校验【路由参数名】（如 /user/:id 里的 id），不校验参数值。
// - 本项目的 436 个接口路由全部是 ASCII 命名，替换后行为完全一致。
// - 该脚本在 Gradle installNodeProjectDeps 之后执行（幂等：重复执行安全）。
// - 找不到目标或未全量应用时以非零退出，让构建在开发机失败而非真机崩溃。
// ============================================================
'use strict'

const fs = require('fs')
const path = require('path')

/**
 * 扫描 node_modules 下所有 path-to-regexp 的 dist/index.js。
 * 注意：pnpm hoisted 布局里 path-to-regexp 可能出现在任意层级
 * （如 node_modules/router/node_modules/path-to-regexp/），
 * 因此需要完整遍历嵌套 node_modules 目录。
 */
function findAllTargets(root) {
  const results = []
  const walk = (dir, depth) => {
    if (depth > 6) return // 防止过度递归
    let entries
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true })
    } catch {
      return
    }
    for (const entry of entries) {
      const full = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        walk(full, depth + 1)
      } else if (
        entry.name === 'index.js' &&
        dir.endsWith(path.join('path-to-regexp', 'dist'))
      ) {
        results.push(full)
      }
    }
  }
  walk(path.join(root, 'node_modules'), 0)
  return results
}

const targets = findAllTargets(__dirname)
if (targets.length === 0) {
  console.error(
    '[patch] 未找到任何 path-to-regexp/dist/index.js，无法打补丁。',
  )
  console.error(
    '[patch] 如果依赖已移除可忽略；否则请检查 node_modules 是否完整。',
  )
  process.exit(1)
}

const replacements = [
  {
    from: "const ID_START = /^[$_\\p{ID_Start}]$/u;",
    to: "const ID_START = /^[A-Za-z_$]$/;",
  },
  {
    from: "const ID_CONTINUE = /^[$\\u200c\\u200d\\p{ID_Continue}]$/u;",
    to: "const ID_CONTINUE = /^[A-Za-z0-9_$\\u200c\\u200d]$/;",
  },
  {
    from: "const ID = /^[$_\\p{ID_Start}][$\\u200c\\u200d\\p{ID_Continue}]*$/u;",
    to: "const ID = /^[A-Za-z_$][A-Za-z0-9_$\\u200c\\u200d]*$/;",
  },
]

let allOk = true
for (const target of targets) {
  let src = fs.readFileSync(target, 'utf8')
  let changed = 0
  for (const { from, to } of replacements) {
    if (src.includes(from)) {
      src = src.split(from).join(to)
      changed++
      console.log('[patch] 已替换:', from, '→', target)
    } else if (!src.includes(to)) {
      // 既不是原始内容也不是已补丁内容 → 版本变了，必须人工检查
      console.error('[patch] ⚠️ 未找到原始行（可能版本变更）:', from)
      console.error('[patch]    文件:', target)
      allOk = false
    }
  }
  fs.writeFileSync(target, src, 'utf-8')
  console.log(`[patch] ${target}: 替换 ${changed}/3 处`)
  if (changed !== replacements.length) {
    console.error('[patch] ❌ 该文件未全量应用补丁:', target)
    allOk = false
  }
}

if (!allOk) {
  console.error('[patch] 补丁未全量应用，构建终止（避免真机运行时崩溃）')
  process.exit(1)
}
console.log('[patch] path-to-regexp 兼容补丁全部完成 ✓')
