# 机器配方 JSON 写法

本文说明 `meknh_core:machine` 类型配方的 JSON 格式。

## 1. 文件位置

配方放在：

```text
src/main/resources/data/<命名空间>/recipe/<机器路径>/*.json
```

例如研磨机：

```text
src/main/resources/data/meknh_core/recipe/macerator/dust_copper.json
```

注意 1.21 的目录名是单数 **`recipe`**，不是 `recipes`。

游戏内修改后需要：

```text
/reload
```

重新加载数据包。

---

## 2. 最小结构

```json
{
  "type": "meknh_core:machine",
  "id": "meknh_core:macerator",
  "input": [
    { "type": "item", "ingredient": "minecraft:copper_ingot", "count": 1 }
  ],
  "output": [
    { "type": "item", "id": "mekanism:dust_copper", "count": 1 }
  ]
}
```

必需字段只有：

- `type`：固定 `meknh_core:machine`
- `id`：目标机器
- 至少一个 `input` 或 `output`（或者使用 `vanilla` 模板）

---

## 3. 顶层字段

```json
{
  "type": "meknh_core:machine",
  "id": "meknh_core:alloy_smelter",
  "level": "LV",
  "time": "200",
  "energy": 0.5,
  "input": [],
  "output": [],
  "extra_type": [],
  "vanilla": ""
}
```

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `type` | string | 无 | 固定 `meknh_core:machine` |
| `id` | string | 无 | 机器 ID，可省略命名空间，省略时补 `meknh_core:` |
| `level` | string | `ULV` | 最低配方等级：`ULV` / `LV` / `MV` / `HV` / `EV` / `IV` |
| `time` | int 或 string | 机器默认 | 配方时间，单位 tick；也可写 `"5s"` |
| `energy` | double | 机器默认 | 每 tick 电流，单位 A |
| `input` | array | `[]` | 输入原料 |
| `output` | array | `[]` | 输出产物 |
| `extra_type` | array | `[]` | 额外可执行该配方的机器 ID |
| `vanilla` | string | 无 | 转发到原版配方类型，目前支持 `minecraft:smelting` |

`time` 如果省略或为负数，使用机器定义里的 `time`；`energy` 同理。

### id 写法

下面两种等价：

```json
"id": "alloy_smelter"
```

```json
"id": "meknh_core:alloy_smelter"
```

### extra_type 示例

```json
"extra_type": ["meknh_core:forge_hammer"]
```

表示合金炉配方也可以被锻造锤执行。

---

## 4. 输入原料

每个输入对象都有 `type` 字段：

```json
{ "type": "item",  "ingredient": ..., "count": 1 }
{ "type": "fluid", "fluid": "...", "amount": 1000 }
{ "type": "gas",   "gas": "...", "amount": 1000 }
```

### 4.1 物品输入

```json
{
  "type": "item",
  "ingredient": "minecraft:copper_ingot",
  "count": 3
}
```

`count` 默认 `1`。

`ingredient` 支持三种写法。

#### 直接写物品 ID

```json
{
  "type": "item",
  "ingredient": "minecraft:iron_ingot",
  "count": 1
}
```

#### 物品标签

```json
{
  "type": "item",
  "ingredient": { "tag": "c:ingots/iron" },
  "count": 2
}
```

#### 原版 Ingredient 对象

```json
{
  "type": "item",
  "ingredient": { "item": "minecraft:iron_ingot" },
  "count": 1
}
```

### 4.2 流体输入

```json
{
  "type": "fluid",
  "fluid": "minecraft:water",
  "amount": 1000
}
```

`amount` 默认 `1000` mB。

标签写法：

```json
{
  "type": "fluid",
  "fluid": "#minecraft:water",
  "amount": 1000
}
```

### 4.3 化学品/气体输入

```json
{
  "type": "gas",
  "gas": "mekanism:oxygen",
  "amount": 100
}
```

`amount` 默认 `1000` mB。

也支持 `"type": "chemical"`，等价于 `"gas"`。

---

## 5. 输出产物

每个输出对象都有 `type` 字段，并可带 `chance`。

```json
{ "type": "item",  "id": "minecraft:iron_ingot", "count": 1, "chance": 1.0 }
{ "type": "fluid", "fluid": "minecraft:water", "amount": 1000, "chance": 1.0 }
{ "type": "gas",   "gas": "mekanism:oxygen", "amount": 100, "chance": 1.0 }
```

### 5.1 物品输出

```json
{
  "type": "item",
  "id": "mekanism:ingot_bronze",
  "count": 4,
  "chance": 1.0
}
```

- `count` 默认 `1`
- `chance` 默认 `1.0`，范围 `0.0 < chance <= 1.0`

有概率的产物：

```json
{
  "type": "item",
  "id": "minecraft:gold_nugget",
  "count": 1,
  "chance": 0.25
}
```

### 5.2 流体输出

```json
{
  "type": "fluid",
  "fluid": "minecraft:water",
  "amount": 500,
  "chance": 1.0
}
```

### 5.3 化学品输出

```json
{
  "type": "gas",
  "gas": "mekanism:hydrogen",
  "amount": 100,
  "chance": 1.0
}
```

---

## 6. 完整示例

### 6.1 合金炉：青铜锭

```json
{
  "type": "meknh_core:machine",
  "id": "meknh_core:alloy_smelter",
  "level": "LV",
  "time": "200",
  "energy": 0.5,
  "input": [
    { "type": "item", "ingredient": "minecraft:copper_ingot", "count": 3 },
    { "type": "item", "ingredient": "mekanism:ingot_tin", "count": 1 }
  ],
  "output": [
    { "type": "item", "id": "mekanism:ingot_bronze", "count": 4 }
  ]
}
```

### 6.2 研磨机：铜粉

```json
{
  "type": "meknh_core:machine",
  "id": "meknh_core:macerator",
  "level": "ULV",
  "time": "5s",
  "energy": 0.25,
  "input": [
    { "type": "item", "ingredient": "minecraft:copper_ingot", "count": 1 }
  ],
  "output": [
    { "type": "item", "id": "mekanism:dust_copper", "count": 1 }
  ]
}
```

### 6.3 带流体和化学品

```json
{
  "type": "meknh_core:machine",
  "id": "meknh_core:extractor",
  "level": "LV",
  "time": "10s",
  "energy": 0.5,
  "input": [
    { "type": "item", "ingredient": "minecraft:raw_iron", "count": 1 },
    { "type": "fluid", "fluid": "minecraft:water", "amount": 1000 }
  ],
  "output": [
    { "type": "item", "id": "mekanism:dust_iron", "count": 2 },
    { "type": "fluid", "fluid": "minecraft:water", "amount": 500, "chance": 0.5 }
  ]
}
```

### 6.4 原版熔炉模板

电炉用一条 JSON 读取原版 `minecraft:smelting` 配方：

```json
{
  "type": "meknh_core:machine",
  "id": "meknh_core:electric_stove",
  "level": "LV",
  "time": "200",
  "energy": 0.25,
  "vanilla": "minecraft:smelting"
}
```

使用 `vanilla` 时可以不写 `input` / `output`，运行时按输入物品查询原版熔炉配方。

---

## 7. 机器定义 JSON

机器本身在：

```text
src/main/resources/data/addon/machine/*.json
```

示例：

```json
{
  "id": "macerator",
  "level": "all",
  "input": [
    { "type": "item", "slots": 1, "stack_limit": 64 }
  ],
  "output": [
    { "type": "item", "slots": 1, "stack_limit": 64 }
  ],
  "time": "200",
  "energy": 0.25,
  "overlay": "macerator"
}
```

| 字段 | 说明 |
|---|---|
| `id` | 机器 ID，用于配方 `id` |
| `level` | 注册等级：`all`、`ULV`、`["LV","HV"]`、`"LV..EV"` 等 |
| `input` | 输入槽定义 |
| `output` | 输出槽定义 |
| `time` | 默认配方时间 |
| `energy` | 默认每 tick 电流 |
| `overlay` | 正面覆盖层贴图目录名 |

槽位 `type` 支持：

- `item`
- `fluid`
- `gas`

其中 `slots` 是槽位数量，`stack_limit` 是堆叠上限。

---

## 8. 注意事项

1. `level` 是**最低要求**，不是精确等级。  
   `LV` 配方可以在 LV/MV/HV/EV/IV 机器运行；不能反向运行。

2. 蒸汽机器可以执行 `ULV` 与 `LV` 配方，不能执行 MV 及以上。

3. 多个配方同时匹配时，按数据包加载顺序取第一个。

4. `chance` 只对产物生效，输入不能写概率。

5. 概率产物在开始处理前的输出空间检查中按“可能产出”处理，不保证一定能放下。

6. 修改 JSON 后执行 `/reload`；新增文件后若游戏已经启动，建议重启客户端/服务器，确保资源包已重新扫描。
