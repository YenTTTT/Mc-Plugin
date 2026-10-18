# CustomRPG 接手與審查交接

日期：2026-10-18（Asia/Taipei）。接手基準：`mobs` 分支、`1520e536552dc46433dd698741b155550d8a12e6`。

## Project Summary

Java 21 / Maven / Paper API `1.21.11-R0.1-SNAPSHOT` 的 MMORPG 插件，共 121 個 production Java 檔案。玩家屬性、天賦、種族、武器、裝備、怪物、舊區域及 BeautyQuests 整合已有程式；不代表全部通過遊戲內驗收。

最高原則仍是 **100% Vanilla World / Zero Custom Building**。本輪沒有新增建築、地圖、村莊、傳送點、副本或平衡數值。

實際可讀文件為 README、PROJECT_STATUS、BUG_FIX_REPORT、WEAPON_SYNC_FIX、SKILL_SWITCH_SYSTEM、NATURE_TALENTS_IMPLEMENTATION 及武器範例。三份 PLUGIN_DESIGN／材料裝備設計原稿、PROJECT_AUDIT_REPORT、OPTIMIZATION_CHANGELOG、UPDATED_DEVELOPMENT_ROADMAP 均不在目前工作樹；無法驗證先前文件引用的 143 節原稿。本文件集中承載審查與後續工作，避免另外建立重複報告。

## Previous Agent Changes

接手時 `git status` 為 clean，無 staged、unstaged 或 untracked 修改；`git diff HEAD`、`git diff --cached` 皆空。已檢查最近 10 次 commit，沒有單獨的 recovery/WIP commit；`7899402` 的任務整合「製作中」後已有 `4613133` 接續。不能僅因工作樹乾淨就認為工作完成。

最新提交 `1520e53` 包含 29 檔、1,628 行新增、249 行刪除，混合了：

- 距離分區、自然生成替換、環境池、安全區、追擊限制、設定與狀態文件。
- 傷害守衛、事件優先順序、玩家／天賦原子存檔、天賦加成紀錄、比例保血量、野獸關閉清理。
- 裝備 PDC ID／強化資料、原物品交換 API、保管欄位持久化 API。

無法確認提交內各部分是否都由同一 Agent 撰寫；以上是 Git 證據，沒有假造先前對話紀錄。

| 工作 | 接手時 | 本輪結束 |
| --- | --- | --- |
| 跨 package 存檔 helper | IN_PROGRESS：實際不能編譯 | COMPLETED：public static，完整建置通過 |
| 裝備原物品交換／保管持久化 | IN_PROGRESS：新 API 無 GUI／登入接線 | COMPLETED：接線與回歸測試完成；真實物品序列化待實服 |
| 距離／維度等級計算 | NEEDS_VERIFICATION | COMPLETED：本機邊界／維度測試；遊戲內效果另驗收 |
| 自然替換、池、上限、leash、Boss 邊界 | NEEDS_VERIFICATION | NEEDS_VERIFICATION：保留原實作，實服清單見 PROJECT_STATUS |
| 傷害守衛、屬性／天賦原子存檔 | 已有實作 | COMPLETED：保留並補上相關單元測試；跨檔交易尚未完成 |
| 天賦／種族／武器主要玩法 | 已有實作 | NEEDS_VERIFICATION：保留，僅修正事件手別、取消處理與生命週期 |
| 生態池內容覆蓋 | IN_PROGRESS | IN_PROGRESS：海洋、End、部分等級仍會保留原版怪 |
| Village／Bell／Structure／工作站服務 | NOT_STARTED | NOT_STARTED：不在本輪新增範圍 |
| 資料庫、跨檔交易、非同步儲存服務 | NOT_STARTED | NOT_STARTED |
| BLOCKED | 無已確認的程式阻塞 | 無；缺少 Paper 測試服屬 NEEDS_VERIFICATION，未宣稱實服完成 |

## Completed Work

- 保留前輪距離生怪、舊區域開關、技能守衛、天賦紀錄與既有公開 API；沒有重寫 Skill／Race 系統。
- 完成原物品裝卸接線：護甲使用原版欄位、額外欄位由插件保管，主手／副手只供檢視。
- 裝備同步統一使用前輪 PDC／精確舊名稱識別 API，不再另外以名稱子字串推測模板。
- 加入 39 項 JUnit／Mockito 測試，均為本機單元、設定或檔案測試。

## Recovered Incomplete Work

1. `PlayerStatsManager.saveAtomically` 原為 package-private，EquipmentManager 跨 package 呼叫造成編譯失敗。改為 public，保留原子替換與 fallback 行為。
2. GUI 改用 `equipFromItem`／`unequipToItem`，回傳真實舊物品，不再 `EquipmentData.toItemStack()` 重新製造可領取物品。
3. 補上登入載入、離線儲存／卸載、重生與物品破損後同步。強化寫回原物品 PDC。
4. 移除 GUI 和同步 listener 中被新識別 API 取代的私有重複轉換邏輯；未移除既有類別。

## Verified Bugs

| 問題 | 證據／影響 |
| --- | --- |
| 編譯失敗 | 初次 JDK 21 `mvn verify` 在 EquipmentManager 呼叫存檔 helper 時失敗 |
| 主手／副手可重複取得 | 原 GUI 卸除透過 `unequipItem` 後製造新物品，底層主副手同步卻已停用，原物品仍在背包 |
| 裝卸遺失物品資料 | 原 GUI 以模板重造物品；附魔、耐久、第三方 NBT 不隨原物品交換 |
| Shift 路徑不可達／拖曳未接管 | 提早 return 令下方 Shift 自動裝備分支無法到達；拖曳到 placement slot 被允許原生移入 |
| 整疊消失／舊裝備重造 | 成功裝備後直接清空游標或將來源 amount 設 0，只應消耗一件 |
| 壞存檔與寫入失敗 | 有效 YAML 但無法還原 ItemStack 的欄位被跳過後可能覆寫；登出存檔失敗仍清快取 |
| 延遲刷新重新打開 GUI | 使用刷新旗標忽略真正關閉事件，待執行刷新仍可重開 |
| 依賴描述錯誤 | `soft-depend` 不被 Bukkit PluginDescriptionFile 識別，實際 soft dependency 清單為空 |
| 離線延遲工作 | 裝備、種族、被動天賦登入任務可在登出後重新建立快取／套用效果 |
| 被動回血工作累加 | 每次 refresh 都建立 timer，沒有追蹤、替換或登出取消 |
| 副手重複觸發 | 兩個技能互動 listener 都讀主手物品，卻未排除副手的同次互動事件 |
| exact 生怪高度不精確 | 原 exact API 把越界 Y clamp 到合法高度，變成另一個位置 |

## Applied Fixes

- 上述編譯、裝備交換、Shift、拖曳、整疊、刷新與存檔問題均已修正。背包滿時以 `addItem` 的 leftovers 歸還或掉落原物品。
- 裝備載入先完整還原到暫存 map，再發布；異常時保留原檔且拒絕新存入。寫入失敗時保留 authoritative 記憶體資料，重登不讀舊檔覆蓋；後續正常儲存可重試。
- 強化拒絕已被替換的選取資料；停用時關閉仍開啟的裝備 GUI，避免 listener 解除後展示物品可被拿走。
- 修正 `softdepend`，僅在 BeautyQuests 啟用時建立／註冊任務與 NPC 整合；未安裝時 `/rpgnpc` 回覆所需插件。這是程式與描述檔修復，尚未實測缺依賴啟動矩陣。
- 技能只處理主手互動；被動傷害 listener 忽略當下已取消事件；回血 timer 每位玩家只保留一份，在加成移除、登出、停用時取消。
- exact API 拒絕越界高度，不強載區塊或換高度。

## Remaining Issues

以下均未以重構或改平衡來掩蓋：

| 優先級／狀態 | 位置與後續處理 |
| --- | --- |
| P1 / NEEDS_VERIFICATION | `MobListener.onVirtualHPDamage/onVirtualHPHeal`：非致死扣血取消原事件，下一 tick 以捕獲值同步 MC HP；同 tick 多次傷害／回血與致死事件可能競爭。須測量真實 Paper 事件順序、擊殺歸屬、護甲與虛擬 HP 比率，再決定修正，避免擅改 Boss 血量平衡。 |
| P1 / IN_PROGRESS | 屬性與天賦為不同檔案，`applyTalentEffects` 依序儲存；插件保管裝備與原版 playerdata 也不是同一交易。原子替換單檔不能防止硬斷電下跨檔回退／複製；需備份與交易／journal 設計。本輪未改資料格式。 |
| P1 / IN_PROGRESS | `QuestCooldownManager`、`NpcManager`、`RaceManager` 仍使用部分寬鬆 loadConfiguration／直接 save；NPC 或冷卻檔損壞後有覆寫風險。應在定義可用性與管理員恢復流程後統一嚴格載入、原子儲存及失敗重試。 |
| P1 / NEEDS_VERIFICATION | 屬性／天賦讀取失敗雖不覆寫該檔，仍返回預設資料供遊戲使用；其他檔案可能繼續更新。需定義載入失敗時是否拒絕角色操作／登入。裝備存入已採拒絕策略。 |
| P2 / IN_PROGRESS | `EquipmentManager.canEquip` 的屬性需求迴圈仍為預留；原生裝備同步不完整套用需求。統一驗證前須決定不合法原版穿裝應停用加成、退回背包或拒絕穿戴。 |
| P2 / NEEDS_VERIFICATION | `TalentPassiveEffectManager.applyAttributeBonuses` 直接增加目前最大生命／移速，重複 refresh 可能累加；天賦被動、武器 base-damage 與技能守衛的全流程順序仍須實服核對。本輪未變更倍率。 |
| P2 / NEEDS_VERIFICATION | 互動 listener 的已取消事件與右鍵空氣的 Paper 預設取消語義、第三方領地插件，以及舊技能／武器技能雙入口需整合驗收。不能直接一律 ignoreCancelled，否則可能使空氣施法失效。 |
| P2 / IN_PROGRESS | `PlayerStatsManager.levelUp` 遞迴處理連升，極大管理指令 XP 可能造成 stack overflow／長時間主執行緒停頓；尚無明確最高玩家等級規格，不擅設上限。 |
| P2 / IN_PROGRESS | 主執行緒 YAML I/O、MobListener 每 60 tick 掃描所有世界實體、大型 TalentSkillManager、舊新技能框架／ArmorManager 並存；先用 profiler 建立基準，再分段優化。不能把換 ConcurrentHashMap 當成非同步 Bukkit 安全保證。 |
| P2 / NEEDS_VERIFICATION | Java 21 編譯尚有 Sound 舊 enum API 等 deprecation／unchecked 警告。Paper API 的 SNAPSHOT 非鎖定版本，尚未證明相容所有 1.21.x。 |
| P2 / NEEDS_VERIFICATION | 保管飾品死亡掉落政策、跨版本 ItemStack 序列化、已存在玩家的插件熱重載、LibsDisguises／BeautyQuests 缺失或版本不合，均須測試服驗收。建議整服重新啟動驗收。 |
| P3 / NOT_STARTED | Village ID、Bell waypoint、Structure 快取與動態副本、工作站製作、動態事件、資料庫服務依原版世界路線後續開發；Rune GUI 明確仍是開發中。 |

## Build / Test Results

環境：JDK `21.0.10.7-hotspot`，UTF-8。初次 `mvn verify` 失敗，修復 helper 存取後恢復編譯。新增 test-scope JUnit Jupiter 5.11.4、Mockito 5.15.2，明確固定 Surefire 3.2.5；不加入插件執行期依賴。

本機測試涵蓋：原物品裝卸與不可重複取回、PDC／強化、壞檔與失敗寫入、GUI 轉移／拖曳／關閉、登入登出、技能手別、被動回血 timer、距離分區邊界與維度、技能守衛巢狀／例外清理，以及全部 29 份 bundled YAML 的 Bukkit 解析（30 份資源中另有 1 份 Markdown）。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot'
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
mvn clean verify
```

最終驗證：`mvn clean verify` 成功，**39 tests / 0 failures / 0 errors / 0 skipped**。JAR：`target/CustomRPG-1.0.jar`；測試報告：`target/surefire-reports/`。故意損壞存檔的測試會輸出 severe／warning，屬驗證預期。

未啟動 Minecraft／Paper Server，未連線正式資料庫，未修改既有玩家資料或世界。Mockito 物品測試不能取代真正 ItemStack 的 NBT／跨重啟序列化測試；YAML 可解析也不等於所有模板及外掛整合可正常執行。

## Files Modified

Production／設定：

- `pom.xml`
- `src/main/resources/plugin.yml`
- `src/main/java/com/customrpg/CustomRPG.java`
- `src/main/java/com/customrpg/equipment/EquipmentManager.java`
- `src/main/java/com/customrpg/gui/EquipmentGUI.java`
- `src/main/java/com/customrpg/listeners/EquipmentSyncListener.java`
- `src/main/java/com/customrpg/listeners/RaceListener.java`
- `src/main/java/com/customrpg/listeners/SkillListener.java`
- `src/main/java/com/customrpg/listeners/SkillTriggerListener.java`
- `src/main/java/com/customrpg/managers/MobManager.java`
- `src/main/java/com/customrpg/managers/PlayerStatsManager.java`
- `src/main/java/com/customrpg/managers/TalentPassiveEffectManager.java`

新增 11 個 test 檔（`src/test/java/com/customrpg/` 下）：`config/ResourceConfigurationTest`、`equipment/EquipmentManagerTest`、`gui/EquipmentGUITest`、`listeners/EquipmentSyncListenerTest`、`listeners/SkillInteractionTest`、`managers/MobSpawnLocationTest`、`managers/PlayerStatsManagerTest`、`managers/TalentPassiveEffectManagerTest`、`managers/WorldZoneManagerTest`、`utils/SkillDamageGuardTest`、`world/DistanceZoneTest`。

文件：本檔、`docs/PROJECT_STATUS.md`、`BUG_FIX_REPORT.md`、`README.md`。本輪不自動 commit 或 push。

## Next Recommended Actions

1. 在備份後的 Paper 1.21.11 測試服驗收：附魔／耐久／強化裝備交換、整疊只耗一件、滿背包、Shift／數字鍵／雙擊／拖曳、飾品存取後重啟、死亡與關閉介面；驗證物品總量及所有 NBT 不變。
2. 測試 BeautyQuests／LibsDisguises 有、無及失敗啟動情境，以及既有任務擊殺歸屬。
3. 完成 PROJECT_STATUS 的自然生成、leash、區塊卸載重載及多玩家上限清單，補上虛擬 HP 同 tick 傷害／回血案例。
4. 先處理剩餘 P1：確定資料不可用策略、跨檔交易與其他檔案保護；不要直接遷移或改寫正式資料。
5. 穩定並建立效能基準後，再進入 Village／Bell 階段。生態池內容、權重、Rare／Champion 階級仍需獨立設計與驗收。
