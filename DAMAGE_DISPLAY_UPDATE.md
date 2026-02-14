# ✅ 血量和傷害顯示系統更新完成！

## 🎯 修改內容

### 1. ✅ 移除血量條符號（█）
- **玩家血量：** 只顯示數字 `§a❤ 20.0/20.0`
- **怪物血量：** 只顯示數字 `[Lv.5] 菁英殭屍 §a❤ 65/65`
- **保留顏色系統：** 綠色(>75%) → 黃色(>50%) → 橙色(>25%) → 紅色(≤25%)

### 2. ✅ 新增傷害數字顯示
- **位置：** ActionBar（與血量顯示相同位置）
- **顯示時間：** 1 秒（20 ticks）
- **普通傷害：** 黃色 `§e-12.5`
- **暴擊傷害：** 紅色加粗 + 特殊符號 `§c§l✦ 18.3 ✦`
- **智能切換：** 顯示傷害時暫停血量更新，1秒後恢復

---

## 📊 顯示效果

### 玩家血量（平時）
```
§a❤ 20.0/§c20.0    （滿血 - 綠色）
§e❤ 12.0/§c20.0    （半血 - 黃色）
§c❤ 3.0/§c20.0     （瀕死 - 紅色）
```

### 攻擊時顯示傷害
```
§e-8.5             （普通傷害）
§c§l✦ 15.2 ✦       （暴擊傷害）
```

### 怪物血量
```
[Lv.5] §7菁英殭屍 §a❤ 65/§c65     （滿血）
[Lv.5] §7菁英殭屍 §e❤ 40/§c65     （受傷）
[Lv.5] §7菁英殭屍 §c❤ 10/§c65     （瀕死）
```

---

## 🔧 技術實現

### 新增文件

#### 1. DamageDisplayManager.java
**功能：** 管理傷害數字顯示
- 追蹤每個玩家的傷害顯示狀態
- 顯示 1 秒後自動清除
- 提供 `isDisplaying()` 方法供 HealthDisplayManager 查詢

**核心方法：**
```java
// 顯示傷害
public void showDamage(Player player, double damage, boolean isCritical)

// 檢查是否正在顯示
public boolean isDisplaying(UUID playerId)

// 格式化傷害文字
private String formatDamage(double damage, boolean isCritical)
```

#### 2. DamageDisplayListener.java
**功能：** 監聽攻擊事件並顯示傷害
- 監聽 `EntityDamageByEntityEvent`
- 只處理玩家攻擊生物的情況
- 判斷是否為暴擊（玩家在空中攻擊）

### 修改文件

#### 1. HealthDisplayManager.java
**修改：** 添加與 DamageDisplayManager 的協調
- 添加 `damageDisplayManager` 引用
- 在更新血量前檢查是否正在顯示傷害
- 如果正在顯示傷害，跳過血量更新

**修改的方法：**
```java
// 設置 DamageDisplayManager 引用
public void setDamageDisplayManager(DamageDisplayManager damageDisplayManager)

// 更新玩家血量前檢查
private void updatePlayerHealthDisplay(Player player) {
    if (damageDisplayManager != null && damageDisplayManager.isDisplaying(player.getUniqueId())) {
        return; // 跳過更新
    }
    // ...
}
```

#### 2. CustomRPG.java
**修改：** 整合新系統
- 添加 import 語句
- 添加 `damageDisplayManager` 成員變數
- 初始化 DamageDisplayManager
- 設置兩個管理器的關聯
- 註冊 DamageDisplayListener
- 在 onDisable 時關閉

---

## 🎮 遊戲體驗

### 正常狀態
```
持續顯示血量在 ActionBar
§a❤ 20.0/§c20.0
```

### 攻擊怪物時
```
1. 攻擊瞬間，ActionBar 切換為傷害數字
   §e-12.5

2. 1秒後，恢復顯示血量
   §a❤ 20.0/§c20.0
```

### 暴擊攻擊時
```
1. 從空中跳下攻擊，觸發暴擊
   §c§l✦ 18.3 ✦

2. 1秒後恢復血量顯示
```

---

## 🔍 暴擊判定

目前使用簡單的判定方式：

```java
boolean isCritical = !attacker.isOnGround() && attacker.getFallDistance() > 0.0F;
```

**條件：**
- 玩家不在地面上
- 玩家有下降距離（正在下落）

**未來可擴展：**
- 結合武器特殊效果
- 結合玩家屬性（暴擊率）
- 結合 buff 系統

---

## 📂 文件結構

```
src/main/java/com/customrpg/
├── managers/
│   ├── HealthDisplayManager.java    （已修改）
│   └── DamageDisplayManager.java    （新增）
├── listeners/
│   ├── HealthDisplayListener.java   （保持不變）
│   └── DamageDisplayListener.java   （新增）
└── CustomRPG.java                    （已修改）
```

---

## ⚙️ 配置選項（未來可擴展）

```yaml
# config/config.yml (建議)
damage-display:
  enabled: true
  duration: 20              # ticks (1秒)
  normal-color: "&e"        # 黃色
  critical-color: "&c&l"    # 紅色加粗
  critical-symbol: "✦"      # 暴擊符號
```

---

## 📊 編譯狀態

✅ **BUILD SUCCESS**
- 編譯時間：3.121 秒
- 新增文件：2 個
- 修改文件：2 個
- JAR 位置：`target/CustomRPG-1.0.jar`

---

## 🎯 測試方法

### 測試血量顯示
```bash
# 1. 加入伺服器，查看 ActionBar
# 應顯示：§a❤ 20.0/§c20.0

# 2. 受傷測試
# 血量降低時顏色應變化

# 3. 回血測試
# 血量增加時顏色應恢復
```

### 測試傷害顯示
```bash
# 1. 生成怪物
/custommob spawn test_zombie

# 2. 普通攻擊
# 地面攻擊，應顯示：§e-8.5

# 3. 暴擊攻擊
# 跳起來攻擊，應顯示：§c§l✦ 15.2 ✦

# 4. 觀察切換
# 顯示傷害1秒後，應自動恢復血量顯示
```

### 測試怪物血量
```bash
# 1. 生成怪物
/custommob spawn test_zombie

# 2. 查看怪物名稱
# 應顯示：[Lv.5] 測試殭屍 §a❤ 65/§c65

# 3. 攻擊怪物
# 血量數字應立即更新
# 顏色應隨血量變化
```

---

## 💡 顯示優先級

```
優先級從高到低：
1. 傷害數字（持續1秒）
2. 玩家血量（持續顯示）
```

**邏輯：**
- 攻擊時：立即顯示傷害，暫停血量更新
- 1秒後：傷害消失，恢復血量顯示
- 如果連續攻擊：每次攻擊都重置顯示時間

---

## 🚀 部署步驟

1. **編譯完成** ✅
   ```bash
   target/CustomRPG-1.0.jar
   ```

2. **停止伺服器**
   ```bash
   stop
   ```

3. **替換插件**
   ```bash
   cp target/CustomRPG-1.0.jar /your/server/plugins/
   ```

4. **啟動伺服器**
   ```bash
   start
   ```

5. **測試功能**
   - 查看血量顯示
   - 攻擊怪物查看傷害數字
   - 測試暴擊效果

---

## 🎨 視覺對比

### 修改前
```
玩家：████████████████████ ❤ 20.0/20.0
怪物：[Lv.5] 殭屍 ██████████ 65/65
```

### 修改後
```
玩家：§a❤ 20.0/§c20.0
怪物：[Lv.5] 殭屍 §a❤ 65/§c65
攻擊：§e-12.5 或 §c§l✦ 18.3 ✦
```

**優點：**
- 更簡潔
- 數字更清晰
- 攻擊反饋更直觀
- 顏色保留血量狀態資訊

---

**實現完成時間：** 2026-02-13 22:06  
**狀態：** ✅ 完全實現並編譯成功  
**新增類別：** 2 個 (DamageDisplayManager + Listener)  
**修改類別：** 2 個 (HealthDisplayManager + CustomRPG)  

🎉 **血量和傷害顯示系統更新完成！**

現在玩家可以清楚看到：
- 自己的血量（帶顏色）
- 怪物的血量（帶顏色）
- 攻擊造成的傷害（普通/暴擊）

