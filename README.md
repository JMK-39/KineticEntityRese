# KineticEntityRese

[简体中文](#简体中文) | [English](#english)

## 简体中文

### 模组定位

**KineticEntityRese** 是 Kinetic 系列的生物状态重置与 BOSS 防堆尸附属模块。它用于记录指定生物进入战斗时的状态，并根据玩家死亡、免死触发或死亡事件被取消等条件累计失败次数；达到设定阈值后，将目标生物恢复到战斗开始时的状态。

这个模块适合大型 BOSS 战、剧情战和高难度服务器，用来避免玩家通过反复死亡、复活和持续磨血绕过战斗设计。

### 主要功能

- **生物状态快照**：玩家或玩家拥有的仆从首次攻击受规则管理的生物时，记录该生物当时的完整状态。
- **失败次数累计**：可分别决定是否统计玩家的正常死亡、免死触发以及被其他模组取消的死亡事件。
- **独立阈值规则**：每种实体都能设置自己的重置阈值，例如监守者 1 次、末影龙 3 次。
- **状态恢复**：达到阈值后恢复目标生物的战斗前数据，并重新回满生命值。
- **保持当前位置**：重置状态时保留目标当前的位置、朝向和运动状态，避免直接把 BOSS 传送回旧坐标。
- **玩家与仆从识别**：玩家直接造成的伤害，以及 `OwnableEntity` 类型仆从给主人造成的战斗进度都会被识别。
- **检测半径**：可以设置玩家死亡时搜索附近受管理实体的半径。
- **可视化规则编辑器**：通过 KineticCore 的 F6 配置中心进入实体列表，新增、修改或移除重置规则。
- **服务端权威规则**：规则保存和最终战斗判定由服务端负责，远程服务器中需要管理权限才能修改规则。

### 配置文件

```text
config/kineticcore/entity_rese.toml
```

核心字段包括：

- `general.enable`：是否启用生物状态重置系统。
- `general.radius`：玩家死亡时的检测半径。
- `general.rules`：每个实体的重置规则。

当前规则格式：

```text
实体ID;死亡阈值;统计正常死亡;统计免死触发;统计取消死亡
```

例如：

```text
minecraft:warden;1;true;true;true
minecraft:ender_dragon;3;true;false;false
```

### 使用建议

优先使用 F6 中的可视化编辑器维护规则，不建议手动批量改写配置。对大型模组 BOSS 建议先确认其状态是否能安全通过 NBT 快照恢复，再用于正式服务器。

### 运行环境

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore：必须

## English

### Overview

**KineticEntityRese** is the entity-state reset and anti-cheese companion module for the Kinetic family. It records the combat-start state of configured entities and counts selected player-failure events. When the configured threshold is reached, the target entity is restored to its recorded state and healed to full health.

### Key Features

- Captures an entity snapshot when combat begins.
- Independent counters for real player death, prevented death and cancelled death.
- Per-entity reset thresholds.
- Restores entity data while preserving current position, rotation and motion.
- Supports damage caused by players and player-owned entities.
- Configurable player-death detection radius.
- Visual rule editor integrated into the KineticCore F6 configuration center.
- Server-authoritative rule persistence and validation.

### Configuration

```text
config/kineticcore/entity_rese.toml
```

Rule format:

```text
EntityID;Threshold;RealDeath;PreventedDeath;CancelledDeath
```

### Requirements

- Minecraft 1.20.1
- Minecraft Forge 47.4.2
- Java 17
- KineticCore: required

## 开源协议与版权 (License)

Copyright (C) 2024-2026 XYAT.

本项目基于 **GNU Lesser General Public License v3.0 (LGPLv3)** 协议开源。

This project is open-sourced under the **GNU Lesser General Public License v3.0 (LGPLv3)**.
