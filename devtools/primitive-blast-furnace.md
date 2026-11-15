# 土高炉移植与验收

## 注册与使用

- 控制器 `meknh_core:primitive_blast_furnace`，结构砖 `meknh_core:primitive_blast_furnace_bricks`。
- JSON 显式声明 `controller_block: meknh_core:primitive_blast_furnace`，使用原生自绑定生成控制器，不注册重复方块。
- 副产物 `meknh_core:dust_tiny_dark_ash`、`meknh_core:dust_ash`。
- 无电压、无能耗、无流体槽；三输入、三输出，菜单/屏幕/JEI 共用坐标。
- 创造栏可取控制器、砖和材料，或使用 `/give @s meknh_core:primitive_blast_furnace`。
- 3×4×3，共32砖、1控制器、3格内部空腔；控制器位于正面中间列、离底部1格。
  同高度背后一格允许空气/雪层/雪块；其上2格严格为空气，顶部敞开。
- 不内置加工配方，后续手动编写。通用配方读取、加工与JEI接入逻辑保留；资源检查脚本不会生成或限定加工配方。
- 未臆造GT铁杆/螺丝/工具链的生存合成替代配方；目前控制器和结构砖由创造栏/指令取得。

## 熔岩不是世界流体

成型时客户端BER在控制器背后一格绘制1×1朝上平面，高度0.875；纹理取原版熔岩STILL图集，
顶点FULL_BRIGHT，不添加世界光照、不放置真正熔岩、不要求机器运行。有限渲染包围盒包括
控制器和内部格，不使用无限AABB。状态只读服务端同步的FORMED/ACTIVE/FACING。

成型时每20个offset tick出熔岩粒子；工作时出浓烟。服务端工作tick对内部格实体尝试熔岩
类型3伤害，每10个offset tick去一层雪，雪块先转7层雪。失型、输入失配或输出堵塞重置进度。

## 物品与同步

完成前在影子库存累计预留输入和全部输出，避免双重消耗/多产物争同一空槽。配方每tick查找，
避免输入和reload后的过期引用。菜单进度/时长拆16bit halves，空客户端BE使用占位并记录日志。
扳手先保存完整NBT再标记packed以防额外掉库存；普通破坏单独掉库存。

## 证据与检查

依据 GTCEu 1.20.1 的 PrimitiveBlastFurnaceMachine 实现。资源检查需同版本 GT 源码检出，
可用脚本第二参数指定检出目录。贴图包含原始耐火砖及正面idle/active/
发光层、动画元数据；灰粉按GT DULL主副色合成。GUI用Mek NORMAL槽与原尺寸RIGHT进度条，
槽坐标来自GT原UI，进度条按中心对齐，不用GT GUI图假冒Mek图。

```sh
node devtools/check-pbf-assets.mjs
gradlew.bat -I devtools/pbf-runtime-probe.init.gradle classes pbfRuntimeProbe --offline --no-configuration-cache
# Java 21，依赖无关的同步/布局探针，输出只写 build：
javac --release 21 -d build/devtools/pbf-probe src/main/java/c_y/meknh_core/common/blast/BlastProgressSync.java src/main/java/c_y/meknh_core/common/blast/PrimitiveBlastFurnaceLayout.java devtools/PrimitiveBlastFurnaceProbe.java
java -cp build/devtools/pbf-probe PrimitiveBlastFurnaceProbe
```

生产解析探针直接调用本项目结构解析/坐标旋转，验证四朝向炉膛都在背后一格及烟道两格位置。
已用它复现并修正移植初稿的前后切片颠倒，而非仅比对一份手写JSON期望。
同步/布局探针覆盖9个short边界，进度0/0.5/1日志分别0/16/32像素；这是几何证据，不是截图。

## 游戏内仍需验收（未启动游戏实测）

1. 四朝向成型与破坏，熔岩面只在正确炉膛、成型即见；失型后消失，内部始终无真实熔岩。
2. 空闲/工作粒子差异、暗处全亮、工作伤害3、雪块逐层清除及烟道空气限制。
3. 空/半满/完成界面及JEI，长时长同步、Shift搬运、取走输入、输出堵塞、reload与区块重载。
4. 扳手打包→放下→取空→普通破坏，不复制/丢失库存；背包满及生存镐挖掉落。
5. 独立服务端启动与客户端连接。资源/纯解析测试和build成功不代替这些世界交互验收。
