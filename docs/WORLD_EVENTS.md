# Dynamic World Events / Meteor Impact

實作日期：2026-10-18。目標為 Paper API **1.21.11**、Java 21；尚未啟動 Paper 測試服。此功能允許程式生成**暫時性**坑洞與隕石，必須備份並還原；不需要人工建築、特定 Seed 或固定座標。這是本次新需求對 Zero Manual Building 的明確定義。

## 已實作流程

```text
持久隨機排程 → 分 tick 嘗試安全選址 → 建立完整 before/after 計畫
→ 原子寫入不可變快照 → 原子寫入事件 → 公告
→ 單一 BlockDisplay 墜落 → 再次驗證場地 → 分批撞擊坑／核心
→ 生成一次性怪物 → ACTIVE → 全數有效擊殺 → COMPLETED
→ 個人素材入帳 → 延後清理 → 移除實體 → 分批還原 → 儲存世界 → CLOSED
```

失敗／逾時／管理員取消共用 CLEANING。狀態機驗證合法轉移，重複完成被拒絕。怪物死亡不補怪，卸載是 UNLOADED，並不算擊殺。第一次生成前先持久保留 SPAWNING 與 UUID，生成事件被其他插件取消時整個事件安全失敗，不強制補怪。

預設怪物共 19 隻：隕石寄生體 8、星界爬行者 4、殞落守衛 4、精英 2、核心守護者 1。模板在 `config/mobs/types/meteor.yml`，重用 MobManager 的等級、屬性及戰鬥，帶有 `event-only` 標籤並排除普通／Boss 隨機池。PDC 保存 `event_id`、`event_mob_id`、`event_type`、`event_level`、`event_rank`。等級來自事件所在地的 WorldZoneManager，與最高等級玩家無關。

所有事件 actor 都有事件 ID；場地內普通自然怪及無關 CUSTOM 怪不接管。事件怪不離開場地追擊，沒有合法玩家戰鬥貢獻的必要怪物死亡會使事件失敗，而不是被視為完成。清理移除的實體不發成功獎勵。

## 選址安全邊界

選址只搜尋玩家附近**已載入**區塊，每 tick 最多測試一個候選點，最多 32 次；不為選址生成或強載區塊。起點以當下生存／冒險玩家位置選擇，公告與管理資訊使用事件實際座標。

一般模式只信任插件啟用後經 `ChunkPopulateEvent` 觀測到的新生成區塊；來源紀錄落盤後才可選。舊的、已探索但來源不明的區塊永遠不自動視為安全。玩家放置／破壞／倒水／活塞／爆炸／實體搬移等已觀測修改會撤銷整個相關區塊資格，重啟不清除這些紀錄。管理員明確執行下述 `test meteor here` 時，只對該次測試放寬條件，不將區塊加入來源白名單。

因此安裝到舊世界後，一般事件需要玩家正常探索到新生成的自然地形才可能選址；沒有合格位置就取消、記錄原因、延後。森林、砂地、複雜植被與大片既有地圖容易被拒絕。手動驗收可改用明確的管理員測試指令，不必繼續尋找新區塊；沒有略過領地、容器、液體或持久快照檢查的全域強制模式。

整個坑洞及外圍 4 格保護帶都需通過：

- 主世界、非和平難度、世界邊界、出生點安全區、自訂保護區、舊 Boss 區、事件重疊檢查。
- 所有涉及區塊的 `getStructures()` 必須為空，連地下結構所在區塊也拒絕；不使用昂貴的無界 locate 搜尋。
- 高度差預設最多 5 格，檢查連續垂直體積，不是只看一塊 GRASS_BLOCK。
- 僅接受明確白名單的天然穩定土石、空氣與少數單格植物。容器、方塊實體、流體、可含水方塊、重力方塊、木材、傳送門與多格植物不在白名單。
- 拒絕範圍內的展示／裝飾實體、載具、村民、命名生物與已馴服動物，減少破壞玩家設施的風險。

專案原本沒有可靠辨識所有玩家建築的服務，本系統也**不宣稱能辨識未知插件直接 setBlock 的所有建築**。已知 WorldGuard、GriefPrevention、GriefDefender、Lands、Towny、Residence、HuskClaims、HuskTowns、Factions、PlotSquared 啟用時，沒有註冊對應領地 adapter 就拒絕事件選址，管理員測試也不能略過。WorldEdit／FAWE 分類為編輯工具，一般事件額外要求編輯追蹤；管理員明確指定位置的測試不以編輯工具 adapter 為必要條件。沒有使用「領地插件查詢失敗就假設荒地」的 fallback。

已內建 **WorldGuard 7／標準 WorldEdit 7** 整合（編譯 API 為 WorldGuard 7.0.15、WorldEdit 7.3.9，均為 provided，不打包進 JAR）。透過 softdepend 確保可選插件先載入；缺少插件仍可正常啟動。啟動時會記錄 `WorldGuard 7 region protection connected`、`WorldEdit 7 terrain edit tracking connected`。

WorldGuard 對每個候選柱體查詢從世界最低到最高建築高度的區域交集：碰到任何已定義領地就拒絕，即使領地允許建築或管理員是 OP／成員也不繞過。荒地仍須允許全域 build、block-break、block-place、mob-spawning 旗標；全域 PVP 等無關旗標不會讓整個世界被誤認成領地。RegionManager 不可用、虛擬查詢結果、API 錯誤或版本不相容時拒絕選址。依據：[官方區域查詢 API](https://worldguard.enginehub.org/en/latest/developer/regions/spatial-queries/)、[區域資料可能不可用](https://worldguard.enginehub.org/en/latest/developer/regions/managers/)。

標準 WorldEdit 在 `EditSessionEvent` 的 BEFORE_CHANGE 階段包裝 extent：方塊／生態域／實體建立前，先持久撤銷相關區塊的自然來源資格；進行中事件／恢復區禁止編輯。來源存檔失敗、無法解析世界、非主執行緒編輯或停用前留下的 session 都拒絕寫入。註冊失敗會停用並嘗試解除部分註冊，停用插件時也解除事件匯流排註冊。[官方 WorldEdit extent 整合方式](https://worldedit.enginehub.org/en/latest/api/concepts/edit-sessions/)。**FAWE 非同步追蹤尚未支援**，因此一般自動／start 事件仍拒絕選址；管理員 `test meteor here` 可在保留領地查詢的前提下測試，這不代表已支援 FAWE 的編輯攔截。

`No protection adapter for WorldEdit` 可能是 FAWE 啟用而跳過同步 hook，或標準 WorldEdit 註冊失敗；僅憑該訊息不能確認是哪一個。新版本會在拒絕原因中保留 FAWE 狀態或初始化例外，`/mmorpg event info` 的 Integrations 會列出啟用中的整合、插件版本、adapter 註冊狀態及失敗原因。插件名稱註冊會正規化已知名稱，避免大小寫不同使領地檢查被略過。

其他保護提供者可透過 `plugin.getWorldEventManager().protection().register(pluginName, predicate)` 整合；predicate 必須可靠驗證該位置能修改，拋例外視為拒絕。其餘已知領地插件仍無內建 adapter，其他外部 world writer 的整合也仍需確認。

## 地形與恢復

預設坑半徑 12、最大挖深 4、核心半徑 3。固定種子噪聲、距離衰減與逐柱原始高度決定不規則輪廓；黑石／玄武岩／凝灰岩地面與黑曜石／哭泣黑曜石／岩漿塊核心是真正方塊，不只粒子。

快照保存每一個計畫修改位置的完整原始 BlockData 字串與預期事件 BlockData，包含原本空氣和植物。只支援前述簡單狀態，沒有聲稱能還原容器物品、未知 TileState、液體或任意多方塊結構。快照有 SHA-256 校驗與不可覆寫檢查。

順序嚴格為快照寫入、`FileChannel.force(true)`、atomic move、事件狀態寫入，成功後才改世界；不支援 atomic move 的檔案系統拒絕施工。寫入失敗不施工，保留診斷；執行中持久化失敗暫停工作，5 秒後重試。

施工與還原預設每 tick 最多 128 個快照條目，`setBlockData(..., false)` 不額外觸發物理更新。事件期間保護放置／破壞、工具點擊、桶、流體、活塞、爆炸、火焰、植物生長、傳送門等入口。施工／還原前將附近玩家移至已驗證的場地外緣；找不到疏散位置時等待，不強制把方塊填進玩家身體。

還原可從索引 0 重複執行：目前已是原始資料就跳過；目前仍是該位置的預期事件資料才恢復；遇到第三種資料就停止、保留快照與保護區，顯示 `Terrain conflict`，不覆寫未知修改。人工修正／備份恢復後可用 cleanup 重試，**沒有強制覆寫未知資料的指令**。

事件開始後只為已驗證範圍持有引用計數 chunk tickets。恢復需要卸載區塊時，以有限非生成 async chunk 請求載入，回到 Paper 主執行緒逐批恢復；世界或既有區塊不可用就等待／保留恢復資料，不生成新的區塊代替遺失的世界。完成後 `world.save()` 再發布 CLOSED，釋放 tickets。沒有每 tick 掃描所有世界實體，僅啟動時掃描一次已載入 actor。

停用時停止新工作、取消共享 timer、移除已載入事件實體與 Display、儲存 CLEANING 並釋放 tickets；不在 onDisable 一次修改數千方塊。下次啟動會取消未完成的討伐並恢復地形，**不接續或重新生成怪物**。已成功完成事件的未領獎資格仍保留。

狀態檔錯誤時唯讀停用新事件，保留所有原檔，依可讀快照隔離相關範圍並清除自己的 actor。修正設定／恢復狀態備份後重啟，再走正常還原。不要刪除恢復檔來解除保護。

Minecraft 世界／原版區塊檔的實際硬體耐久性由 Paper 和儲存系統決定；本機 mock 測試沒有模擬作業系統斷電或損壞的 region files。

## 貢獻與個人獎勵

使用者已選擇**持久個人素材錢包**。預設每位合格參與者獲得：隕石碎片 12、星界水晶 3、隕石核心碎片 1，可在 YAML 修改。

統計實際傷害（上限為該次可扣生命值）與各隻怪物的參與者；擊殺參與不只給最後一擊。預設至少造成 1 點傷害且占總傷害 1%；單純在旁邊、重複死亡通知或非有限傷害值不增加資格。支援可歸屬到玩家的直接攻擊、投射物、技能來源與已馴服生物；沒有可靠來源的反傷／無來源 DoT／特定自製召喚獸不保證歸屬，需實服確認。

專案沒有可可靠歸屬施法者的統一治療／坦克貢獻服務，本版不捏造治療或承傷分數。既有虛擬 HP 自行取消傷害時，以其實際 PDC 扣血差值記錄；仍須驗收該舊系統與其他取消事件插件的順序。

COMPLETED 解鎖核心右鍵領獎；也能在場景清理後 `/events claim <eventId>` 補領。領取標記與素材餘額在同一個原子文件一起寫入，失敗就回復記憶體交易；重登／重啟不會重置。沒有背包塞入或地面掉落，背包滿不影響獎勵，沒有實體素材提領功能。

本版素材是最小穩定 ID／顯示名稱與錢包餘額，供後續工作站製作系統使用；沒有假裝已有經濟插件或高階裝備合成。本版不新增 Gold 或額外 EXP 領獎。隕石模板 EXP 為 0，完成獎勵由個人素材處理。

## 設定、指令與資料

設定：`plugins/CustomRPG/config/world_events.yml`，缺檔才複製，不覆寫舊檔；修改後整服重新啟動。預設隨機 45–120 分鐘、最低 1 位合適玩家、最多 1 個未關閉事件、討伐 20 分鐘、成功後保留 120 秒。沒有合格玩家／位置則延後 300 秒。下次時間持久化；逾期重啟重新等待一個隨機間隔，不在啟動瞬間連發。

一般玩家：

```text
/events
/events info
/events list
/events wallet
/events claim <eventId>
```

管理員（`customrpg.event.admin`，預設 op；指令入口與 handler 都檢查權限）：

```text
/mmorpg event list
/mmorpg event start meteor
/mmorpg event start meteor here
/mmorpg event test meteor here
/mmorpg event info
/mmorpg event stop <eventId>
/mmorpg event cleanup <eventId>
/mmorpg event debug <eventId>
```

`start meteor here` 只是候選點，仍使用地表高度及全部一般安全驗證。start 是非阻塞選址要求；「已開始安全選址」不代表已建立事件。成功後全服公告；取消時直接通知下指令的玩家或主控台，列出嘗試數、各拒絕原因與次數、排程延後秒數。自動排程失敗僅寫入伺服器紀錄，不全服洗頻。`/mmorpg event info` 可查看最後診斷及完整原始原因統計。debug 顯示狀態、各怪物狀態數、快照 ID、批次索引與阻塞原因。cleanup 重新嘗試安全還原，和 stop 共用流程。

### 直接在目前位置驗收

站在已探索的平地，以具有 `customrpg.event.admin` 權限的玩家執行：

```text
/mmorpg event test meteor here
```

- 使用目前 X、Z 的地表位置，不要求生存模式或最低線上人數，不需要新生成區塊；可以在舊草地或自行整理的土石／木板場地測試。事件與全服公告標示「管理員測試」。
- 略過自然來源紀錄、原版結構所在區塊、生態域及本插件 SafeZoneManager 安全區（含出生點安全圈）；允許高低差至多 12 格，接受常見建材、木材／樹葉、礦石及更多植物。材質範圍見 TerrainValidator 的 testing 白名單。
- 仍要求主世界、非和平難度、已載入範圍、世界邊界及可還原材質。箱子／方塊實體、液體／含水方塊、砂礫等重力方塊、門／床／活塞等複雜狀態不支援。自訂 ProtectionAreaManager、Boss 區、WorldGuard／其他領地 adapter、事件重疊、最大事件數及存檔健康檢查照常生效。
- WorldEdit／FAWE 的編輯追蹤缺失或失敗不再阻擋指定位置測試；這與領地查詢分開處理。**測試期間不要使用 WorldEdit／FAWE 編輯場地**，缺少追蹤時無法保證攔截這些編輯；發現與快照不同的未知地形變更，施工／還原仍會停止並保留資料。一般事件仍要求可靠編輯追蹤。
- 撞擊前用相同測試條件再驗證，不會因舊來源紀錄而二次取消。測試怪物生成與玩家疏散僅放寬本插件安全區檢查，碰撞、地面、世界邊界與自訂保護區仍檢查。
- 完整執行快照、墜落、坑洞／核心、怪物、貢獻、素材錢包、延後清理與還原；會真實暫改場地。可用 `stop <eventId>` 提前清理。`test-mode` 標記持久保存，舊存檔缺少標記預設為一般事件；重啟仍取消戰鬥並還原，不恢復測試戰鬥。

此指令只對明確指定的事件放寬條件，不修改一般選址設定，也不永久信任舊區塊。

常見「選址後沒有事件」的原因：舊區塊即使未蓋建築，仍因沒有新生成來源紀錄而被拒絕；搜尋距離 64–256 格可能涵蓋伺服器視距外的未載入區塊；和平難度、森林木材、過陡地形、原版結構或保護區也會拒絕。先查 info 確認原因，再到插件啟用後新探索、已載入且平坦的自然地形測試。`here` 同樣不能跳過這些條件。

```text
data/world-events/state.yml                 排程、事件、怪物、貢獻、領獎、錢包
data/world-events/natural-chunks.yml        新自然區塊來源／撤銷資格
data/world-events/snapshots/<eventId>.yml   不可變 before/after 快照
```

不要在執行期間手改資料檔。快照與已關閉事件目前保留作為恢復／補領／稽核依據，尚無自動封存政策；大量長期資料須另做容量與主執行緒 I/O profiling。

## 架構與擴充

`WorldEventDefinition` 提供地形需求、純幾何計畫、動畫、怪物規格及獎勵。`WorldEventManager.register` 可加入其他類型；沒有實作 Demon Rift 等其他事件。新增類型沿用狀態機、排程、快照、保護、怪物 registry、貢獻、錢包與還原，不重新建立第二套戰鬥系統。所有 world-event mutation API 都由 Paper 主執行緒呼叫；未宣告 Folia 支援。

## 驗證與實服驗收

目前共 **116 項**（以最終 verify 報告為準）。涵蓋狀態／排程、快照校驗、壞檔唯讀保護、半完成恢復、施工與還原批次、未知修改拒絕、生成前持久身分、19 隻規格、event-only 池排除、貢獻與領獎交易、重啟補領、管理權限、Display 清理、最小保護帶疏散及完整模擬生命週期；另外驗證指令延後回報、未知地形拒絕、32 次混合拒絕統計／持久延後，以及玩家離開時的取消通知。WorldGuard／WorldEdit 的案例使用真實 WorldGuard 區域索引／旗標計算與 WorldEdit 事件匯流排，驗證荒地、地下／高空領地、全域旗標、查詢失敗、可選類別缺失、編輯前落盤、事件區拒絕、停用、非同步拒絕及 FAWE 一般事件拒絕。管理員測試驗證舊區塊可通過且不永久信任、擴展材質、保護與液體拒絕、指令權限、旗標持久化／舊檔相容、生成檢查，以及一般／測試兩種完整生命週期的還原與領獎；另涵蓋編輯追蹤失敗的管理測試、領地仍拒絕、大小寫註冊正規化，以及初始化錯誤／版本診斷與部分註冊清理。

仍需在世界／插件資料備份後，以 Paper 1.21.11 測試服驗收：

- 新生成平原／自然石地的選址、村莊／地下結構／舊區塊拒絕、邊界與保護 adapter 的真實回傳。
- 安裝實際版本的 WorldGuard 7／WorldEdit 7，確認啟動連線紀錄；荒地可通過保護檢查，領地／全域 deny 不可通過，WorldEdit 編輯撤銷來源資格且不能改事件場地。FAWE 的一般事件仍應拒絕，固定位置管理員測試保留領地查詢後可通過。
- `/start meteor here` 不繞過來源或保護；建築、桶、工具、爆炸、活塞、植物和傳送門防護。
- `/mmorpg event test meteor here` 可在舊平地／自建簡單場地測試，含本插件出生安全圈；仍拒絕容器／液體／領地，確認怪物生成、疏散、領獎、stop／重啟還原及測試標記。
- 3×3 BlockDisplay 與拖尾可見，真實世界 19 隻怪物的碰撞、AI、難度、追擊與原戰鬥事件順序。
- 多人直接攻擊／箭／技能／寵物的有效貢獻；反傷、虛擬 HP、其他插件取消與異常移除情境。
- 核心未解鎖／解鎖／雙擊／登出／重啟補領與滿背包；確認只入素材錢包一次。
- ACTIVE、FALLING、半施工、半還原各階段停止伺服器再啟動，確認沒有補怪、殘留 Display 或丟失地形；完成後的普通玩家修改不被舊快照覆寫。
- 真實 chunk 卸載、世界卸載、磁碟故障、world.save 耗時、長期 snapshot 大小及 tick profiling。此版本不建議用熱重載作為正式驗收方式。
