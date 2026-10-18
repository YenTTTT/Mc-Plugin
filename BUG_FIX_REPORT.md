# 装备系统Bug修复报告

## 问题描述
`/equipment give` 命令无法抓取到 `armors.yml` 里的装备

## 根本原因
1. **ArmorType 枚举缺少 MEDIUM 类型**
   - `armors.yml` 中使用了 `type: MEDIUM`（中甲）
   - 但 `ArmorType.java` 枚举只有 `LIGHT`、`CLOTH`、`HEAVY` 三种类型
   - 导致装备加载时抛出 `IllegalArgumentException: No enum constant com.customrpg.equipment.ArmorType.MEDIUM`

2. **加载失败导致装备模板为空**
   - ArmorManager 和 EquipmentManager 都尝试从 `armors.yml` 加载装备
   - 由于 `type` 字段错误，装备无法正确解析
   - 最终导致装备模板列表为空

## 修复方案
1. **添加 MEDIUM 装甲类型到 ArmorType 枚举**
   ```java
   public enum ArmorType {
       LIGHT("輕甲", "§a", "刺客"),
       MEDIUM("中甲", "§e", "戰士"),  // 新增
       CLOTH("布甲", "§b", "法師"),
       HEAVY("重甲", "§7", "鬥士");
   }
   ```

2. **增强日志输出**
   - 在 `EquipmentManager.loadEquipmentTemplates()` 中添加详细的加载日志
   - 在 `EquipmentManager.loadEquipmentFromConfig()` 中添加属性解析日志
   - 方便未来调试

## 测试步骤
1. 重启 Minecraft 服务器或使用 `/reload confirm` 命令
2. 检查服务器日志，应该能看到：
   ```
   [INFO] [CustomRPG] 開始從 armors.yml 載入裝備...
   [INFO] [CustomRPG] 成功載入裝備: novice_warrior_helmet - 破舊戰盔
   [INFO] [CustomRPG] 成功載入裝備: novice_warrior_chestplate - 破舊戰士胸甲
   ...
   ```

3. 使用命令测试：
   ```
   /equipment list                           # 查看所有可用装备
   /equipment give novice_warrior_helmet     # 给予装备测试
   ```

## 修改的文件
1. `src/main/java/com/customrpg/equipment/ArmorType.java`
   - 添加 `MEDIUM` 装甲类型

2. `src/main/java/com/customrpg/equipment/EquipmentManager.java`
   - 增强日志输出
   - 添加异常堆栈跟踪

## 额外说明
- ArmorManager 和 EquipmentManager 都从 `armors.yml` 加载数据，这是设计上的冗余
- ArmorManager 主要用于旧系统兼容
- EquipmentManager 是新的统一装备系统
- 建议未来整合这两个管理器以避免重复加载

## 编译命令
```bash
mvn clean package
```

编译后的插件位于: `target/CustomRPG-1.0.jar`

