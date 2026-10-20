# MicroAuto App Icon

- 画布：512 × 512，圆角 112
- 背景：`#0D9488` → `#0F766E` 线性渐变 + 左上薄荷柔光
- 图形：机器人头像，白色渐变头、薄荷侧耳、琥珀天线、深青眼睛与微笑，底部悬浮阴影
- 占比：外轮廓约 232 × 224px（约画布 45%），四周留白约 27%，与上一版保持一致
- Adaptive Icon：前景保持在 72dp 安全区内，背景为全幅渐变
- Monochrome：机器人头像剪影（眼睛为镂空），用于 Android 13+ 主题图标

`icon.svg` 用于传统 mipmap PNG；`icon-foreground.svg` 用于 Adaptive Icon 前景 PNG。
