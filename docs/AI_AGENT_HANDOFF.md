# CustomRPG 接手與審查交接

## Dynamic World Events — Current Stage（2026-10-18）

- Current Stage：Stage 1–9 程式與本機驗證完成；Paper 實服驗收為 NEEDS_VERIFICATION。
- Completed Work：確認基準 `3b7aeb0` 工作樹乾淨、Paper 1.21.11／Java 21；既有 39 tests 通過。可重用 WorldZoneManager、SafeZoneManager、ProtectionAreaManager、MobManager、既有戰鬥；沒有資料庫或舊玩家建築來源追蹤。
- Modified Files：新增 `worldevent/` 的 core／scheduler／location／terrain／meteor／mob／reward／persistence；`config/world_events.yml`、5 個 event-only 怪物模板、主插件與指令註冊、MobManager 的 pre-spawn identity callback／普通池排除。
- In Progress：沒有刻意留白的 Meteor 開發階段；實服視覺／AI／傷害事件順序／保護插件版本整合與 profiling 尚未執行。
- Known Issues：未有 Paper 實機環境；不能識別未知插件直接改世界的所有建築；一般模式不會把原有已探索區塊自動視為安全，明確管理員測試可對單次事件放寬。
- Build Status／Test Status：JDK 21，首次 `mvn clean verify` 成功（84 tests）；選址回報修正後為 88 tests；WorldGuard／WorldEdit 整合後為 101 tests；管理員測試模式後為 113 tests；WorldEdit 診斷與測試補修後 `mvn verify` 共 116 tests／0 failures／0 errors／0 skipped。31 份 YAML 可解析。使用者已選擇持久個人素材錢包，無實體物品跨存檔領獎交易。輸出 `target/CustomRPG-1.0.jar`。
- Next Steps：閱讀 [WORLD_EVENTS.md](WORLD_EVENTS.md)，在 Paper 1.21.11 測試服可用 `/mmorpg event test meteor here` 於舊平地驗收完整流程、重啟還原及多人歸屬；一般模式另需驗收新生成來源檢查。

| Stage | 本輪結果與接續依據 |
| --- | --- |
| 1 Project Integration | COMPLETED：`3b7aeb0` 為基準；先前三份設計原稿仍不存在，依實際程式與本次需求實作；不重寫正常 Race／Skill／Equipment。 |
| 2 Event Core | COMPLETED：WorldEventInstance／合法狀態機、Definition registry、唯一 ID、持久隨機 scheduler、嚴格原子 repository。 |
| 3 Safe Location | COMPLETED：每 tick 1 候選、有界 loaded-only 搜尋；新自然區塊來源、修改撤銷、整體體積、結構、保護、邊界、重疊與裝飾實體檢查。 |
| 4 Terrain | COMPLETED：seeded 坑洞／核心、不可變 before/after 快照與校驗、落盤成功閘門、分批還原、未知修改拒絕與重複還原。 |
| 5 Animation | COMPLETED：單一 3×3 BlockDisplay、下降／拖尾／警告／撞擊；框架關閉動畫，沒有獨立孤兒 task。 |
| 6 Combat | COMPLETED：5 種／19 隻 event-only 模板重用 MobManager；pre-spawn PDC／UUID 持久保留，DEAD／UNLOADED 不重生；有效傷害與多人擊殺參與。 |
| 7 Reward | COMPLETED：按使用者選擇實作持久素材錢包；完成資格、領取標記、錢包同檔原子交易，無背包容量限制，可在清理後依 ID 補領。 |
| 8 Cleanup | COMPLETED：成功延後、失敗／逾時／取消共用清理；移除已載入 actor、分批恢復、有限 async 非生成載入、世界儲存後 CLOSED、停用與重啟恢復。 |
| 9 Verification | COMPLETED（本機）：116 tests；真實 Paper 事件、世界 region file 的耐久性、AI／碰撞與效能仍需實服測試。 |

本輪新增的防護細節：快照／manifest 寫入失敗時不施工且不重複建立實例；損壞狀態唯讀隔離；invalid config 不隱藏舊恢復區；還原 CAS 不覆寫未知第三方變更；施工前疏散點必須真的位於施工區外；副手工具也不能繞過保護。啟動時所有未結束討伐進入 CLEANING，不以「找不到 entity」為由補怪。

本輪修改／新增共 50 檔：

- 修改 10 檔：`README.md`、本檔、`docs/PROJECT_STATUS.md`、`CustomRPG.java`、`ConfigManager.java`、`MobManager.java`、`plugin.yml`、`pom.xml`、`ResourceConfigurationTest.java`、`MobSpawnLocationTest.java`。
- 新增 `docs/WORLD_EVENTS.md`、`config/world_events.yml`、`config/mobs/types/meteor.yml`。
- 新增 `src/main/java/com/customrpg/worldevent/` 23 個類別，涵蓋 core／scheduler／location／terrain／meteor／mob／reward／persistence。
- 新增 `src/test/java/com/customrpg/worldevent/` 14 個 test 類別，76 個執行案例；包括完整模擬生命週期、未完成施工／還原、持久交易、一次性生成、Display 清理、保護與壞檔案例，以及選址失敗通知、WorldGuard／WorldEdit 整合與管理員測試模式。另在原 MobSpawnLocationTest 新增 1 項測試專用生成檢查案例。

2026-10-18 選址回報補修：使用者回報 start 只有初始訊息；確認 endSearch 原本僅記錄 logger。現在由指令傳入回報 callback，選址取消直接通知發令者，附嘗試數、各原因次數及延後時間；info／logger 保留原始原因統計。自動排程只記錄紀錄，不廣播失敗。仍維持舊區塊／未載入／保護等拒絕條件。新增 4 項測試涵蓋真正未知區塊拒絕且不產生快照／事件／地形修改、32 次混合拒絕與持久延後、玩家離開，以及指令延後回報。

2026-10-18 WorldGuard 補修：新增可選 WorldGuard 7.0.15／WorldEdit 7.3.9 provided 依賴與 softdepend。WorldGuard 查整根世界高度柱體，拒絕任何已定義領地，荒地仍檢查全域 build／break／place／mob-spawning；使用非成員身分，無 OP bypass。標準 WorldEdit 的 BEFORE_CHANGE extent 在寫入前撤銷來源，拒絕事件區／非同步／停用後 session，並於 shutdown 解註冊。13 項新測試涵蓋真實索引、旗標、事件匯流排與可選類別缺失；FAWE 與其他領地插件仍無內建支援。尚未啟動實際 Paper＋WG／WE 測試服。

2026-10-18 使用者要求可實際驗收：新增 `/mmorpg event test meteor here`，限 event admin 玩家且必須明確指定 here；允許舊／修改過區塊、本插件安全區及更多簡單材質，略過原版結構區塊／生態域，坡度上限放寬至 12。仍檢查領地／Boss 區／事件容量／重疊、已載入範圍、主世界非和平、邊界、液體／方塊實體與持久化。`test-mode` 存檔預設 false、舊檔相容；撞擊再驗證、怪物位置／生成及疏散都使用測試條件。測試會執行真正快照、怪物、獎勵、清理與還原；不永久信任區塊、不更動一般自動事件條件。新增 12 個測試執行案例（含完整生命周期兩種模式），詳見 WORLD_EVENTS.md。

2026-10-18 WorldEdit 缺少 adapter 補修：僅由原訊息不能確認是 FAWE 或同步 WorldEdit 初始化失敗，使用者實際版本尚未取得。ProtectionValidator 將領地提供者與編輯追蹤分開；僅固定位置 test 可略過 WorldEdit／FAWE 追蹤前提，真實領地查詢仍拒絕未知或禁止區域。一般自動／start 事件仍保守拒絕。保存初始化錯誤並在 info 列插件版本與註冊狀態，FAWE 顯示明確未支援原因；失敗 hook 停用並解除部分註冊。已知插件名稱正規化，避免小寫名稱讓 predicate 被誤跳過。新增 3 項回歸案例；缺少追蹤的測試期間不能使用 WorldEdit／FAWE 修改場地，未知變更仍由快照衝突檢查停止施工／還原。

新的世界設計語義：**Zero Manual Building 允許插件建立暫時性坑洞與隕石，必須自行還原**。下方是上一輪 121 類別／39 tests 的歷史審查紀錄，涉及「尚未開發動態事件」或「本輪沒有地形生成」的舊敘述由本段與 WORLD_EVENTS.md 的最新狀態取代。現在 production Java 為 144 類別。

### 本輪 Known Issues / 未實作範圍

- 一般事件只接受啟用後觀測的新生成區塊；管理員 test 可明確在舊區塊測試，但不永久授予來源資格。不能辨識未知插件直接寫入的所有玩家建築。已內建 WorldGuard 7／標準 WorldEdit 7；FAWE 一般事件仍拒絕、固定位置 test 不要求編輯追蹤。其他已知領地插件若沒有 adapter，包含 test 都拒絕。
- 治療／坦克貢獻沒有可靠共同入口，因此沒有虛構分數；無可靠來源的 DoT／自製召喚獸等歸屬需實服確認。既有虛擬 HP 與其他插件取消事件的順序風險仍保留。
- 素材是持久錢包中的最小定義，沒有實體提領、Gold、新的 EXP 領取或製作系統；這符合本次選定交付方式，並非已有完整材料倉庫／經濟插件。
- 不支援 Folia；沒有實際啟動 Minecraft Server。快照／已關閉事件保留供稽核和補領，長期封存與大型主執行緒 I/O 仍需 profiling；world.save 可能有同步成本。
- 不接續中斷的戰鬥，啟動後只做還原；已完成獎勵仍能補領。遇未知地形衝突、區塊檔遺失、世界不可用或資料損壞時保留資料，不能宣稱已成功復原。
- 本輪未修改正式世界／玩家資料／資料庫，未 commit／push；其他事件類型只保留 Definition 擴充入口。

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
