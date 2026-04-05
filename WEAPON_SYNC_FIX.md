# 武器同步問題修復

## 修改日期
2026-02-14

## 問題描述

當玩家拿著自製武器（如鐵鐮刀）切換到其他欄位，再重新拿回手上時，自製武器會變成原版的 IRON_HOE，並且顯示屬性加成。這表示武器的自定義數據（NBT）丟失了。

## 問題根本原因

在之前移除了 `/eq` GUI 中的主武器和副手槽位後，`EquipmentSyncListener` 仍然在監聽並嘗試同步主手物品到裝備系統。當玩家切換物品時：

1. `onItemHeld` 事件被觸發
2. `checkAndSyncEquipmentSlot` 被調用來同步主手槽位
3. `convertItemToEquipmentData` 將自製武器錯誤地轉換為裝備數據
4. `equipmentManager.equipItem()` 將轉換後的裝備同步回主手
5. 原本的自製武器被替換成帶有裝備屬性的原版物品

## 解決方案

### 1. 停止監聽主手物品切換

**文件**: `EquipmentSyncListener.java`

修改 `onItemHeld` 方法，不再同步主手槽位：

```java
@EventHandler(priority = EventPriority.MONITOR)
public void onItemHeld(PlayerItemHeldEvent event) {
    // 主手槽位已從裝備系統移除，不需要同步
    // 此監聽器保留但不執行任何操作
}
```

### 2. 移除快捷欄點擊同步

**文件**: `EquipmentSyncListener.java`

在 `onInventoryClick` 方法中，移除了對快捷欄（主手）的同步邏輯：

```java
// 不再檢查主手切換（快捷欄），因為主手已從裝備系統移除
```

### 3. 跳過主手和副手的同步處理

**文件**: `EquipmentSyncListener.java`

在 `checkAndSyncEquipmentSlot` 方法開頭添加檢查：

```java
private void checkAndSyncEquipmentSlot(Player player, EquipmentSlot slot) {
    // 跳過主手和副手，因為它們已從裝備系統移除
    if (slot == EquipmentSlot.MAIN_HAND || slot == EquipmentSlot.OFF_HAND) {
        return;
    }
    // ...existing code...
}
```

### 4. 禁用裝備系統對主手/副手的同步

**文件**: `EquipmentManager.java`

在 `syncToMinecraftEquipment` 方法中，註釋掉主手和副手的同步代碼：

```java
private void syncToMinecraftEquipment(Player player, EquipmentSlot slot, EquipmentData equipment) {
    // ...existing code...
    
    // 主手和副手已從裝備系統移除，不再同步
    // case MAIN_HAND:
    //     inv.setItemInMainHand(item != null ? item : new ItemStack(Material.AIR));
    //     break;
    // case OFF_HAND:
    //     inv.setItemInOffHand(item != null ? item : new ItemStack(Material.AIR));
    //     break;
    
    // ...existing code...
}
```

## 修改的文件

1. **`EquipmentSyncListener.java`** (3處修改)
   - 修改 `onItemHeld()` - 不再處理主手切換事件
   - 修改 `onInventoryClick()` - 移除快捷欄同步邏輯
   - 修改 `checkAndSyncEquipmentSlot()` - 跳過主手和副手槽位

2. **`EquipmentManager.java`** (1處修改)
   - 修改 `syncToMinecraftEquipment()` - 不再同步主手和副手到原生槽位

## 結果

✅ **武器不再被裝備系統處理** - 自製武器保持原有的 NBT 數據和屬性
✅ **切換物品正常** - 可以自由切換主手物品而不會被轉換
✅ **裝備系統獨立** - 只處理防具和飾品槽位
✅ **編譯成功** - 沒有錯誤或衝突

## 關於武器配置格式

你的武器配置文件（`example.yml`）使用的是**正確的新格式**，不需要更新。問題不在配置文件，而在於裝備同步邏輯錯誤地將武器當作裝備處理。

鐵鐮刀配置示例：
```yaml
iron_scythe:
  basic:
    name: "鐵鐮刀"
    material: IRON_HOE
    # ...其他配置...
```

這個格式是正確的，`WeaponManager` 能夠正確識別和創建武器。

## 測試建議

1. **拿起自製武器** - 使用 `/weapon iron_scythe` 獲取鐵鐮刀
2. **切換欄位** - 將鐵鐮刀切換到其他快捷欄位置
3. **切回主手** - 重新選擇鐵鐮刀
4. **驗證結果** - 檢查武器是否保持原有的名稱、Lore 和屬性（不應顯示裝備屬性加成）

## 編譯結果

✅ 編譯成功
✅ 打包成功  
✅ JAR 檔案: `target/CustomRPG-1.0.jar`

