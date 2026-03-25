# NEI-RecipeTree

简体中文 | English

NEI-RecipeTree is a recipe tree / bill of materials viewer for `NotEnoughItems` on Minecraft `1.7.10`, adapted from the recipe tree idea in EMI and rebuilt for the NEI workflow.

---

## 中文

### 简介

`NEI-RecipeTree` 是一个面向 Minecraft `1.7.10` 与 `NotEnoughItems` 的合成树模组，用来把 NEI 的单个配方页扩展成可递归浏览的整棵材料树。

它的目标不是替代 NEI，而是在 NEI 原有配方查看流程上补一层“材料展开 / 配方选择 / 总成本统计 / 导出”的能力。

### 兼容性

- Minecraft：`1.7.10`
- Forge：`10.13.4.1614`
- MCP Mapping：`stable_12`
- 依赖：`NotEnoughItems`
- 语言级别：通过 `Jabel` 使用现代 Java 语法，产物仍为 Java 8 字节码

### 主要功能

- 在 NEI 配方界面直接打开当前物品的合成树
- 对同一产物在多种候选配方之间切换，并将选择回写到整棵树
- 递归展开材料需求，汇总总材料成本
- 支持折叠 / 展开节点，保留用户手动选择的分解路径
- 右上角快捷导出：
  - 导出到 NEI 收藏夹
  - 导出为 PNG 图片
- 批次面板支持快速调整数量并自动推荐合适批次
- 悬停物品框时高亮当前节点、直连连线和下一级子节点
- 悬停左上角配方角标时显示该节点的配方预览

### 使用方式

#### 1. 打开合成树

在 NEI 的配方界面中点击 `Recipe Tree` 按钮，即可从当前配方进入合成树界面。

如果某个物品存在多个可选配方，可以：

- 在 NEI 配方页点击 `选择此配方`
- 或者进入合成树后，对节点使用左键浏览并切换配方

#### 2. 界面操作

| 操作 | 说明 |
| --- | --- |
| `左键` | 浏览并选择当前节点的配方 |
| `Shift + 左键` | 自动选择第一个匹配配方 |
| `右键` | 折叠 / 展开当前节点 |
| `Shift + 右键` | 清除当前节点的配方选择 |
| `Del` | 重置整棵树 |
| `Shift + Del` | 关闭界面 |
| `滚轮（批次面板）` | 数量 `±1` |
| `Shift + 滚轮（批次面板）` | 数量 `±16` |
| `Ctrl + 滚轮（批次面板）` | 数量 `×2 / ÷2` |
| `左键（批次面板）` | 设为推荐批次 |

#### 3. 导出

- **导出到收藏夹**：将当前树中的配方链写入 NEI Bookmark
- **导出为图片**：将当前树保存为 PNG 文件到：

```text
minecraft/screenshots/recipe_trees/
```

图片导出使用离屏 FBO 渲染，不依赖当前窗口大小，也不会污染当前 GUI 的缩放与偏移状态。

### 开发与构建

在项目目录中执行：

```bash
cd NEI-RecipeTree
./gradlew compileJava --console=plain
./gradlew build
./gradlew runClient
```

### 代码结构

- `src/main/java/moe/takochan/neirecipetree/bom`：材料树模型、配方解析、分解状态管理
- `src/main/java/moe/takochan/neirecipetree/gui`：树布局、渲染、交互、高亮、导出图片
- `src/main/java/moe/takochan/neirecipetree/nei`：NEI 按钮注入、NEI 配方页联动
- `src/main/java/moe/takochan/neirecipetree/recipe`：NEI 配方引用与适配层
- `src/main/resources/assets/neirecipetree`：语言文件、GUI 纹理等资源

### 致谢

- EMI 的配方树 / BoM 思路提供了最初的交互参考
- 感谢 `NotEnoughItems` 提供配方浏览与收藏夹基础能力

---

## English

### Overview

`NEI-RecipeTree` is a recipe tree mod for Minecraft `1.7.10` and `NotEnoughItems`. It extends NEI's single-recipe view into a recursively browsable material tree.

The goal is not to replace NEI, but to add one more layer on top of the existing NEI workflow: material expansion, recipe selection, total cost aggregation, and export.

### Compatibility

- Minecraft: `1.7.10`
- Forge: `10.13.4.1614`
- MCP mappings: `stable_12`
- Dependency: `NotEnoughItems`
- Language level: modern Java syntax via `Jabel`, still compiled to Java 8 bytecode

### Features

- Open a full recipe tree directly from an NEI recipe page
- Switch between multiple valid recipes for the same output and apply the selection back into the tree
- Recursively expand ingredient requirements and calculate total material cost
- Collapse / expand nodes while preserving manual recipe choices
- Quick actions in the top-right corner:
  - export to NEI bookmarks
  - export to PNG image
- Batch panel for fast quantity changes and ideal-batch selection
- Hovering an item frame highlights the node itself, its connected lines, and its direct children
- Hovering the small recipe badge in the top-left shows the recipe preview for that node

### Usage

#### 1. Open the recipe tree

From an NEI recipe screen, click the `Recipe Tree` button to open the tree for the current recipe.

If an item has multiple valid recipes, you can either:

- click `Select this recipe` in the NEI recipe page
- or left-click the node inside the tree to browse and switch recipes

#### 2. Controls

| Action | Description |
| --- | --- |
| `LMB` | Browse and choose a recipe for the current node |
| `Shift + LMB` | Auto-select the first matching recipe |
| `RMB` | Collapse / expand the current node |
| `Shift + RMB` | Clear the current node's recipe selection |
| `Del` | Reset the entire tree |
| `Shift + Del` | Close the screen |
| `Mouse wheel (batch panel)` | Change amount by `±1` |
| `Shift + mouse wheel (batch panel)` | Change amount by `±16` |
| `Ctrl + mouse wheel (batch panel)` | Change amount by `×2 / ÷2` |
| `LMB (batch panel)` | Set the ideal batch size |

#### 3. Export

- **Export to Bookmarks**: writes the current recipe chain into NEI bookmarks
- **Export as Image**: saves the current tree as a PNG file under:

```text
minecraft/screenshots/recipe_trees/
```

Image export uses off-screen FBO rendering, so it does not depend on the current window size and does not mutate the active GUI zoom or pan state.

### Development and Build

Run from the project directory:

```bash
cd NEI-RecipeTree
./gradlew compileJava --console=plain
./gradlew build
./gradlew runClient
```

### Project Structure

- `src/main/java/moe/takochan/neirecipetree/bom`: material tree model, recipe resolution, decomposition state
- `src/main/java/moe/takochan/neirecipetree/gui`: tree layout, rendering, interaction, highlighting, image export
- `src/main/java/moe/takochan/neirecipetree/nei`: NEI button injection and recipe-screen integration
- `src/main/java/moe/takochan/neirecipetree/recipe`: NEI recipe references and adapters
- `src/main/resources/assets/neirecipetree`: language files, GUI textures, and other resources

### Credits

- EMI for the original recipe tree / BoM interaction idea
- `NotEnoughItems` for the recipe browsing and bookmark foundation
