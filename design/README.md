# MicroAuto App Icon

- 画布：512 × 512，圆角 112
- 背景：`#4338CA` → `#0EA5E9` 线性渐变
- 图形：S 形执行轨迹，左下琥珀圆点代表 micro 起点，右上播放三角代表 auto 执行
- 占比：外轮廓约 244 × 214px（约画布 48%），四周留白约 26%
- Adaptive Icon：前景保持在 72dp 安全区内，背景为渐变
- Monochrome：轨迹 + 播放三角，用于 Android 13+ 主题图标

`icon.svg` 用于传统 mipmap PNG；`icon-foreground.svg` 用于 Adaptive Icon 前景 PNG。
