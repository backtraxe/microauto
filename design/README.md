# MicroAuto App Icon

- 画布：512 × 512，圆角 112
- 背景：`#4338CA` → `#0EA5E9` 线性渐变
- 图形：全白工作流轨迹，主路径收束为箭头，一条分支路径连接两个节点圆环
- 占比：外轮廓约 240 × 216px（约画布 47%），四周留白约 27%
- Adaptive Icon：前景保持在 72dp 安全区内，背景为渐变
- Monochrome：主路径 + 箭头 + 分支 + 两个实心节点，用于 Android 13+ 主题图标

`icon.svg` 用于传统 mipmap PNG；`icon-foreground.svg` 用于 Adaptive Icon 前景 PNG。
