# 自然系天賦實作總結

## 完成項目

### 1. talents.yml 配置文件
✅ 添加了完整的 `nature` 天賦分支，包含9個技能：

#### 基礎技能 (Tier 5 - 無前置)
- **星碎** (slot 52): 投射3發追蹤星光碎片，造成魔法傷害
- **落雷** (slot 51): 召喚落雷攻擊至多3個目標
- **直線風壓** (slot 47): 風刃攻擊並給予自身速度加成
- **土崩** (slot 45): 召喚龍捲風拉扯並擊飛敵人

#### 中階技能 (Tier 4)
- **觀星** (slot 43): 被動技能，+25 精神 & 魔法（前置：落雷 OR 星碎）
- **自然教義** (slot 40): 被動技能，3級可升，每級+25 精神 & 魔法（前置：土崩 OR 直線風壓）
- **龍捲風LV2** (slot 38): 強化版龍捲風，增加傷害範圍（前置：自然教義 Lv3）

#### 高階技能 (Tier 3)
- **煥發LV2** (slot 32): 範圍治療+持續傷害區域（前置：自然教義 Lv1 OR 觀星 Lv1）
- **連鎖閃電LV2** (slot 33): 連鎖6個目標的閃電並暈眩（前置：觀星 Lv1）

### 2. Talent 類別修改
✅ 添加了 `PrerequisiteMode` 枚舉來支持 `anyOf` 邏輯：
- `PrerequisiteMode.ALL`: 需要滿足所有前置條件 (AND)
- `PrerequisiteMode.ANY`: 至少滿足一個前置條件 (OR)

✅ 修改了 `canLearn()` 方法以支持 OR 邏輯的前置條件檢查

### 3. TalentManager 類別修改
✅ 更新了 `loadTalentFromConfig()` 方法：
- 支持載入 `anyOf` 格式的前置條件
- 正確解析 `statBonus` 嵌套結構（如 `statBonus: { spirit: 25, magic: 25 }`）
- 將 statBonus 屬性儲存為 `statBonus_spirit`, `statBonus_magic` 等格式

✅ 更新了 `applyTalentEffects()` 方法：
- 支持 `PASSIVE` 類型天賦的屬性加成
- 正確讀取 `statBonus_` 前綴的效果

### 4. TalentTree 類別修改
✅ 更新了 `getPrerequisiteStatus()` 方法：
- 正確顯示 `anyOf` 前置條件的狀態
- 使用 "或" 連接符來展示多個可選前置條件

### 5. TalentSkillManager 類別修改
✅ 添加了所有7個主動技能的實作方法：
- `executeStarShatter()`: 星碎技能
- `executeLightningStrike()`: 落雷技能
- `executeWindBlade()`: 直線風壓技能
- `executeEarthCollapse()`: 土崩技能
- `executeTornadoLv2()`: 龍捲風LV2技能
- `executeFlourishLv2()`: 煥發LV2技能
- `executeChainLightningLv2()`: 連鎖閃電LV2技能

✅ 添加了輔助方法：
- `launchHomingProjectile()`: 發射追蹤彈射物
- `getTargetEntity()`: 獲取玩家視線目標
- `spawnTornado()`: 生成龍捲風效果
- `chainLightning()`: 連鎖閃電效果
- `drawLightningLine()`: 繪製閃電連接線

## 技能機制說明

### 星碎 (Star Shatter)
- **觸發**: 手持金粒右鍵
- **效果**: 發射3發自動追蹤的星光碎片
- **傷害**: 33 + 全基礎屬性 × 0.05 魔法傷害
- **視覺**: FIREWORK 粒子效果

### 落雷 (Lightning Strike)
- **觸發**: 手持骨頭右鍵
- **效果**: 對視線目標召喚落雷，波及周圍至多3個敵人
- **傷害**: 75 + 全基礎屬性 × 0.2 + 武器傷害 × 0.16 魔法傷害
- **視覺**: 閃電效果 + ELECTRIC_SPARK 粒子

### 直線風壓 (Wind Blade)
- **觸發**: 手持木棒右鍵
- **效果**: 放出直線風刃並給予自身速度I (3秒)
- **傷害**: 55 + 全基礎屬性 × 0.14 + 武器傷害 × 0.1 物理傷害
- **視覺**: SWEEP_ATTACK + CLOUD 粒子

### 土崩/龍捲風 (Earth Collapse/Tornado)
- **觸發**: 手持海靈晶體右鍵
- **效果**: 在前方1格生成移動的龍捲風，拉扯並擊飛敵人
- **傷害**: (全基礎屬性 × 1.0 + 武器傷害 × 0.72) ÷ 4 物理傷害 (分4次)
- **視覺**: CLOUD 粒子螺旋效果
- **龍捲風LV2**: 範圍增加至4.5格

### 煥發LV2 (Flourish LV2)
- **觸發**: 手持海靈晶體右鍵
- **效果**: 在指定位置創建持續10秒的治療+傷害區域
- **範圍**: 半徑8格
- **治療**: 魔法屬性 × 0.5 / 秒
- **傷害**: 全基礎屬性 × 0.43 / 秒 魔法傷害
- **視覺**: HAPPY_VILLAGER 粒子

### 連鎖閃電LV2 (Chain Lightning LV2)
- **觸發**: 手持金粒右鍵
- **效果**: 閃電連鎖至多6個目標並暈眩0.5秒
- **傷害**: 全基礎屬性 × 0.35 + 武器傷害 × 0.28 魔法傷害
- **視覺**: ELECTRIC_SPARK + ENCHANTED_HIT 粒子

## 修正的問題

### 問題1: anyOf 前置條件無法載入
**原因**: TalentManager 只支持單一前置條件格式
**解決**: 
- 添加 `PrerequisiteMode` 枚舉
- 修改載入邏輯以支持 `anyOf` 列表
- 更新前置條件檢查邏輯

### 問題2: 觀星和自然教義顯示 "null加成: 自身null * 0.0"
**原因**: 
1. 使用了不存在的屬性名 `SPIRIT` 和 `WISDOM`（應為小寫）
2. `WISDOM` 在 PlayerStats 中不存在
3. `statBonus` 嵌套結構未被正確解析
4. PASSIVE 類型天賦的屬性加成未被應用

**解決**:
1. 將 YAML 中的屬性名改為小寫: `spirit`, `magic`
2. 將 `WISDOM` 改為 `magic`（智慧對應魔法屬性）
3. 修改 TalentManager 載入邏輯以解析 `statBonus` 嵌套結構
4. 修改 `applyTalentEffects()` 以支持 PASSIVE 類型的屬性加成

## 屬性對應關係

| 遊戲屬性 | PlayerStats 欄位 | 說明 |
|---------|-----------------|-----|
| 力量 | strength | 物理攻擊 |
| 智慧 | magic | 魔法攻擊 |
| 敏捷 | agility | 暴擊/遠程 |
| 體力 | vitality | 最大血量 |
| 防禦 | defense | 減免傷害 |
| 精神 | spirit | 魔力相關 |

## 測試建議

1. **前置條件測試**:
   - 確認觀星需要落雷 OR 星碎
   - 確認自然教義需要土崩 OR 直線風壓
   - 確認煥發需要自然教義 OR 觀星

2. **屬性加成測試**:
   - 學習觀星後檢查 +25 精神 & 魔法
   - 學習自然教義 Lv1/2/3 檢查 +25/50/75 精神 & 魔法

3. **技能測試**:
   - 測試每個主動技能的觸發條件
   - 確認傷害計算正確
   - 檢查視覺效果和音效

4. **冷卻與魔力消耗**:
   - 確認各技能的冷卻時間
   - 確認魔力消耗正確扣除

## 文件結構
```
src/main/
├── java/com/customrpg/
│   ├── talents/
│   │   ├── Talent.java (修改 - 添加 PrerequisiteMode)
│   │   └── TalentTree.java (修改 - 更新前置條件顯示)
│   └── managers/
│       ├── TalentManager.java (修改 - 載入 & 應用邏輯)
│       └── TalentSkillManager.java (修改 - 添加技能實作)
└── resources/config/
    └── talents.yml (修改 - 添加 nature 分支)
```

