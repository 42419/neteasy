#!/usr/bin/env bash
# 运行命令并保留输出；失败时把关键错误行作为 annotation 输出，
# 这样不用下载完整日志，在 Actions 页面就能看到失败原因。
# 用法：run.sh <标题> <命令...>
title="$1"
shift
log="$(mktemp)"
"$@" 2>&1 | tee "$log"
rc=${PIPESTATUS[0]}
if [ "$rc" -ne 0 ]; then
  # GitHub 每个 step 最多显示 10 条 error annotation
  grep -nE '(^e: |error:|FAILED|Exception|What went wrong|Unresolved reference|Execution failed)' "$log" \
    | head -10 \
    | while IFS= read -r line; do
        msg=$(printf '%s' "$line" | tr -c '[:print:]' ' ' | tr '%' ' ' | head -c 400)
        echo "::error title=$title::$msg"
      done
fi
exit "$rc"
