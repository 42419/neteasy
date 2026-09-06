# 第三方代码引用说明

本项目部分 UI 交互效果的实现参考/改编自以下开源项目，按其许可证要求在此注明。

## Nevodev/Cresto

- 项目地址：https://github.com/Nevodev/Cresto
- 许可证：Apache License 2.0
- 引用位置：`app/src/main/java/top/yunov/neteasy/ui/components/TactileBackButton.kt`
- 说明：按压反馈的弹簧参数、`TransformOrigin`、`drawBehind` 叠加顺序等具体机制，
  改编自该项目 `glasense-ui` 模块 `Button.kt` 中的 `ButtonPressEffect.Scale`
  实现，并按本项目图标按钮的场景做了简化（去掉了原版按钮组件的 role/disabled
  等通用按钮语义，只保留返回按钮需要的按压反馈本身）。
