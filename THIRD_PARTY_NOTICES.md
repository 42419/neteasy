# 第三方代码引用说明

本项目部分 UI 交互效果的实现引用/参考自以下开源项目，按其许可证要求在此注明。

## Kyant0/AndroidLiquidGlass

- 项目地址：https://github.com/Kyant0/AndroidLiquidGlass
- 许可证：Apache License 2.0
- 引用方式：Maven 依赖 `io.github.kyant0:backdrop`（见 `app/build.gradle.kts`）
- 引用位置：`app/src/main/java/top/yunov/neteasy/ui/components/GlassBackBar.kt`
- 说明：`GlassTopBackBar` 里顶部渐进模糊条带用的 `drawPlainBackdrop` + `runtimeShaderEffect`
  这套 API 就是这个库提供的，本项目没有修改其实现，只是按官方
  Progressive blur 教程（https://kyant.gitbook.io/backdrop/tutorials/progressive-blur）
  的写法调用。

## Nevodev/Cresto

- 项目地址：https://github.com/Nevodev/Cresto
- 许可证：Apache License 2.0
- 引用位置：`app/src/main/java/top/yunov/neteasy/ui/components/GlassBackBar.kt`
- 说明：`GlassTopBackBar` 里返回按钮固定悬浮定位（Box + align(TopStart)，滚动内容的兄弟节点
  而非父子）、`rememberPageBackdrop` 的 3 倍大背景色矩形预填充、渐进模糊条带的滚动门控淡入
  （`isScrolledPast`）这几处具体做法，参考自该项目 `feature/settings/AboutScreen.kt` 和
  `ui/components/glasense/GlasenseDynamicSmallTitle.kt`，按本项目场景做了改写（比如把
  `LazyListState` 版本的滚动门控换成了普通 `ScrollState` 也能用的版本），不是逐行照搬。
  按压回弹缩放（1→1.2倍）+ 高光叠加这套动效参数最早也是照这个项目 `glasense-ui` 模块
  `Button.kt` 里的 `ButtonPressEffect.Scale` 抄的。

## NeteaseCloudMusicApiEnhanced（api-enhanced）

- 项目地址：https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced
- 许可证：MIT License
- 引用方式：内嵌后端 [neteasy-backend](https://github.com/42419/neteasy-backend)（Rust）中的加密算法、请求流程和接口定义移植自该项目，
  对应关系见 neteasy-backend 的 README 与 `NOTICE`。本仓库不再包含也不再打包 api-enhanced 的 Node.js 源码。
