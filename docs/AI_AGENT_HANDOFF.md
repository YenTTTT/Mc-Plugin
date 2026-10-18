# CustomRPG 接手與審查交接

## 固定耗魔與種族加成／GUI修正 — 最新狀態（2026-10-18）

- 使用者要求天賦改回原本固定 MANA，並確認「提高種族加成並修正 GUI」。COMPLETED：程式、八個新增回歸案例、文件及JAR；NEEDS_VERIFICATION：Paper實服平衡／客戶端。沒有改動實際伺服器或玩家原檔。
- `TalentSkillManager` 直接用 `Talent.getManaCost()` 檢查／扣除，移除最大MP比例成本方法；GUI顯示固定MANA，不足提示同值。原YAML消耗值完整沿用，物品／快捷鍵仍共用冷卻與扣魔入口。兩種入口的固定20MANA與最大MP80／800、免費技能、魔力不足／雙向冷卻均驗證。
- `raceBudget(L) = 18 + 18×(clamp(L,1,60)−1)/59`（整數除法），Lv.1合計18點、Lv.60合計36點。RaceData拆為固定基礎18點與額外0～18成長點：正base權重先配1點，餘點及growth用固定權重最高商逐點分配，避免等級提高反而掉某屬性。內建十二族720個等級組合皆非零、守預算且單調；明確全零自訂權重仍零，不憑空補點。
- 惡魔六項依力量／魔法／敏捷／體力／防禦／精神：Lv.1＝4/4/3/3/2/2，Lv.30＝6/6/4/4/3/3，Lv.60＝8/8/6/6/4/4。提高實際來源加成，並非僅改顯示；既有常駐輸出+10%共同上限、親和／暴率／移速／額外MP上限保留。
- RaceGUI列表／詳情採玩家目前等級，標示「種族額外加成」，並以Lv.1／30／60實際預覽取代誤導的成長倍率。`/race info` 區分起始及目前加成。登入／重算採既有覆寫式來源，不疊加、不補滿MP；較高精神會正常提高最大MP／回魔。
- 完整 `mvn verify` **441 tests，0 failures／errors／skipped**，紀錄 `target/fixed-mana-race-verify.log`；產物 `target/CustomRPG-1.0.jar`。更新JAR並重啟即可，不刪設定或重置種族。現行規則見 [RACES.md](RACES.md)、[BALANCE_RUNTIME.md](BALANCE_RUNTIME.md)。下方「比例耗魔／種族1～12點」皆為先前階段。


## 天賦機制物品施法恢復 — 最新狀態（2026-10-18）

- 使用者要求把機制物品施放功能改回來。COMPLETED：恢復 86 招原有主動天賦的材料／按鍵，16 招擴充新增冰霜海磷碎片、雷霆海靈晶體、大地燧石、聖光螢石粉（右鍵）。四個攜帶槽、快捷鍵 1～4、種族 5 保留；不是回退戰鬥 v2。
- `TalentItemListener` 只處理主手，依四槽順序選擇匹配物品的已學 ACTIVE；蹲下右鍵按物品循環選擇，只切換不施放。一般左／右／蹲下左鍵進既有 `castSkillDirectly`，共享學習／攜帶／冷卻／比例 MP／HP 與傷害管線。退出清除循環狀態，重置／卸下後不會施放舊選擇。材料不消耗。
- `TalentItemBinding` 支援既有中文材料與武器類別，也可精確使用 Material 名稱。GUI 顯示機制物品及按鍵。`TalentManager` 載入兩份設定時，對已知內建 ID 的 HOTKEY 設定在記憶體補回綁定，保留檔案原位元、自訂材料／明確點擊按鍵及平衡值；支援擴充技能被搬進 primary。未知自訂 HOTKEY 保持快捷鍵。
- 選區工具先註冊，接著物品天賦，最後武器點擊入口；皆在 HIGHEST 路由。物品被 DENY／方塊互動被取消時不施放，允許原版空氣互動僅 block-use DENY。匹配天賦即攔截原版物品、武器自帶技能與法杖右鍵普攻，失敗也不串放；法杖也補主手閘門。
- 新增 18 案：12 物品事件、3 載入相容／102 招綁定、3 實際共享魔力／雙向冷卻。完整 `mvn verify` **433 tests，0 failures／errors／skipped**，產物 `target/CustomRPG-1.0.jar`，紀錄 `target/talent-item-verify.log`。NEEDS_VERIFICATION：Paper 客戶端施法、領地／反作弊插件及實際武器互動；未啟動伺服器。部署後重啟，不需刪除設定；完整操作見 [SKILL_SWITCH_SYSTEM.md](../SKILL_SWITCH_SYSTEM.md)。
- 下方「移除機制物品」段落是先前需求的歷史階段，現況以上述恢復版為準。


## 戰鬥數值 v2 正式接線（2026-10-18）

- 使用者本輪明確要求把前輪模型套入現有戰鬥、玩家資料、種族等；不再是「只完成模型」。COMPLETED：程式／版本遷移／離線測試與 JAR；NEEDS_VERIFICATION：Paper 實機、其他插件與遊玩平衡。本機沒有運行伺服器或直接修改實際玩家檔。
- 新增八個 production adapters：BalanceMigration、BalancePlayerRules、BalanceItemRules、CombatBalance、BalanceCombatListener、BalanceItemRefresh、ExtraDamageBudget、VirtualHealthProjection。InitialBalanceModel 為公式來源。玩家升級每級三點與一點天賦、停止六項自動+1，封頂60，HP／MP／DEF接模型；天賦來源改暫態欄，不再寫回自由配點。變身HP也分離，結束不還原過期最大HP。
- 首次讀舊玩家檔：先 SHA-256 原始位元備份到 data/balance-backups/v2/players，再以六已配屬性＋未花點數七權重按最大餘數法換成 3×(L−1)。超60夾60與溢出XP歸零，原資料在備份。MP按比例換算；舊未花天賦最多59，已學／技能選擇保留。版本2不重複遷移，失敗保留原檔並阻止該角色戰鬥／登入。天賦首次v2保存也先備份到 talents 子目錄；不清種族選擇。
- 八武器與980工坊／舊裝備依有效等級、整套來源預算重算，舊武器ID與物品本體保留；每格防禦至少1，整套仍精確對上模型。額外格／符文／強化總核心屬性最多當級整套110%；需求未達不給RPG屬性。舊物品只更新數值lore／版本，持久資料、名稱、附魔、耐久、數量留存；商人商品更新結果但保留庫存次數、價格配料、補貨期限。
- 所有玩家普攻共用快照，弓／三叉戟發射保存傷害、DPS、破甲；法杖純粒子彈保存同一類基本攻擊快照。近戰蓄力、暴擊、攻速正規化與常駐總預算10%；種族／武器專精／稀有度／附魔合併。種族六屬性按權重上限1～12，親和98～102%、移速±3%，介面改顯示有效值。
- 舊技能傷害按等級平方根比例轉單位，再走來源／目標共用追加池，爆發2秒DPS、每秒補0.15秒DPS；種族增傷、條件觸發、召喚物、流血、燃燒、破甲、分裂箭一起計。技能基準取角色主攻，不受命中瞬間切手影響。取消退額度、重連不重領，有限容量與閒置清理。AoE仍為每目標預算，沒有新增全場上限；控場／技能不是逐一重設計，需實測。
- 治療共用事件，戰鬥單次25%HP、初始池35%、每秒補4%；汲取看實際扣血，吸血／武器效果在接受傷害後執行。天賦非零耗魔改最大MP10～30%依冷卻；種族及既有武器技能資源入口留存。原飽食回血關閉，脫戰8秒後共享約5%HP/s，登入等待；第二春60秒CD，自製護盾按HP比例。
- MobData血量／攻擊接角色等級固定參考線；精英tag/ID、boss旗標、group-boss tag對應定位。原版Enemy也按首次觀測地區級別套模型，保留原本替換／finishrpg流程。舊怪載入按血量比例遷移，普通成功攻擊間隔至少1.8秒、怪物招式另有短期／持續預算。隊伍Boss首次攻擊固定48格合資格人數1～8，後續不重設HP。
- 直接戰鬥清原版ARMOR/MAGIC/RESISTANCE減傷並套RPG防禦，盾牌／吸收留存；護甲與盾耐久換回原版等價單位。玩家環境傷害按HP/20，怪物環境傷害按普通怪參考HP，玩家燃燒有歸屬／追加池。虛擬HP以最終RPG傷害一比一扣，HIGHEST只投影原生事件，MONITOR確認未取消才寫PDC；不取消原生致死、不合成另一擊，治療同理。傷害數字轉回RPG單位。
- 新增七類48案；完整 **mvn clean verify：415 tests，0 failures／errors／skipped**，168 production Java；紀錄 target/balance-runtime-clean-verify.log，JAR target/CustomRPG-1.0.jar。已檢查原版減傷不雙算、415案與遷移失敗／重試／MP比例、六件預算、角色重連、裝備防禦／耐久／吸收、60級實際監聽器七刀、Boss扣血／致死／取消／同tick多擊。
- 最新操作／相容邊界／回滾見 [BALANCE_RUNTIME.md](BALANCE_RUNTIME.md)。debug開關位於 config/config.yml 的 balance.debug-damage，預設false，舊檔不覆寫；開啟並重啟可看[Balance:v2]。下一步應在測試服測新手到60、三怪群、Boss／多人、盾牌／吸收、變身換裝、商人舊庫存、存檔重啟及保護插件。玩家／天賦自動備份不是世界備份，只換舊JAR不會還原已持久化的生物HP。


## Lv.1～60 初始數值模型（2026-10-18）

- 使用者明確選擇「先完成模型與驗算」，本輪沒有切換既有戰鬥數值、改玩家存檔或覆寫裝備／怪物設定。COMPLETED：模型／驗算；NOT_STARTED：正式套用與資料遷移；NEEDS_VERIFICATION：Paper 實機與遊玩平衡。
- 新增無 Bukkit 依賴、未接插件生命週期的 `balance/InitialBalanceModel`，等級 1～60；每級 3 自由點、每次加 1、取消六項自動 +1 的建議；HP=100+8n+3VIT、DEF/(DEF+60+2L) 上限 60%、攻速正規化八武器與技能預算、怪物固定參考裝備／配點反推五秒 TTK 與六下承傷。當級基準裝備是整套預算，並另外驗算現有七階門檻。
- CSV 由同一 Java 匯出：60 級、480 武器列、12,162 不重複案例；`tools/balance/plot_balance_model.py` 僅讀 CSV 用 matplotlib 輸出 SVG，沒有再抄公式。主文件 [INITIAL_BALANCE_MODEL.md](INITIAL_BALANCE_MODEL.md)。
- Lv.60 參考：HP737、劍白字34.55／非暴擊66.8、普通怪HP438.208／原始傷害167.872；全攻劍TTK3.95s／4.38下等價、坦克6.51s／7.61下等價；七階裝備換裝前最慢6.52s。這些是離線預估，沒有完整技能、怪群、AI、盔甲或隨機暴擊分布模擬。
- 特別記錄待修：目前虛擬 HP 以 virtualMax/1024 放大傷害／治療，與此模型要求的一點傷害扣一點虛擬 HP 不相容；需連同致死、歸屬、掉落、取消與多次命中一起修，不能只砍比例。Stats GUI／近戰／技能／裝備來源也有公式不一致與多重倍率，細節見模型文件，不要只先改怪物 HP。
- 驗證：新模型九項測試；完整 `mvn verify` **367 tests，0 failures／errors／skipped**，紀錄 `target/balance-verify.log`。沒有 Paper 實機測試。Java CLI 可從 `target/classes` 單獨匯出，重建 JAR 不會自動套用此模型。


## 怪物頭頂愛心血條移除（2026-10-18）

- HealthDisplayManager 移除血條實體生成、每 10 ticks 全世界掃描與每 2 ticks 同步；HealthDisplayListener 不再對怪物受傷／回血排程更新。玩家 HUD、怪物名稱／等級與 BossBar 保留。
- 啟動只掃已載入實體，後續 EntitiesLoadEvent 清掉 `mob_health_tag_owner` 的 ArmorStand；`disguise_nametag` 名稱實體保留，只去除本插件怪物／偽裝名稱及儲存基礎名稱的舊血量後綴。未強制載入區塊。
- 驗證：`mvn verify` 通過，358 tests、0 failures／errors／skipped，產出 `target/CustomRPG-1.0.jar`；紀錄 `target/health-display-verify.log`。Paper 實機仍需驗證新怪無愛心、已載入／後載入區塊舊血條清理與偽裝名稱保留。

## 工坊與村莊裝備商 — 最新接手狀態（2026-10-18）

- 使用者要求大量新裝備、打怪掉素材→crafting table 做裝備、每個村莊按地區等級賣裝備；已選綠寶石交易。新增 CraftingModule，十地形×七階（Lv.1/8/18/28/38/48/58）×十四類＝980 件，560 武器／280 護甲／140 飾品；84 素材，987 配方含七階核心→8 纖維拆解。
- CraftingCatalog 嚴格讀 config/crafting_progression.yml、生成純資料；CraftingItems 保存 canonical PDC／版本與原版材質身分，輸出接 WeaponManager／EquipmentManager，原 YAML 不覆寫。registerSupplemental 及既有 reload hook 接回新模板。新增護甲有原版 trim，所有新產品 max stack 1，沒有新資源包或隨機詞綴承諾。
- CraftingRecipes 註冊原版 ExactChoice Shaped／Shapeless，Prepare／Craft 檢查 PDC／階級／鏡像／窄圖平移及 3×3；原版負責批次與扣料交付，不自訂複製交易。拒絕把自訂素材用於普通合成／鍛造素材／放線／餵蛙或悅靈；新產品鍛造換材質失去 ID 的結果被攔截。RecipeChoice adapter 供離線測試，未虛構 RegistryAccess。
- CraftingLootListener 在 HIGHEST 接於既有 HIGH 掉落之後，只加掉落不清原物；自製怪看怪物等級／tags，原版 Enemy 看 WorldZone 基礎等級／環境；預設 essence100%／binding50%／Boss core100%，玩家歸屬／獸寵主人支援，排除 pet、event-only、summon-only 與熔核分裂體，隕石錢包不改。
- CraftingRangedListener 僅為新 BOW／SPEAR 發射檢查等級，按武器／屬性／稀有度／武器暴擊／拉弓力度保存 PDC 傷害快照，NORMAL 命中先套基礎，再交現有 HIGH／HIGHEST 管線；舊弓及技能彈體不改。
- VillageMerchantRegistry 原子保存原版 village 結構 world UUID＋範圍 ID／NPC UUID／最後卸載 chunk，壞檔或寫入失敗停商人而保留其他工坊。VillageMerchantManager 只排程已載入主世界 chunks，先確認 entitiesLoaded；沒有 locate 或加 ticket。Paper 結構 API 自身可能讀相鄰起點資料，不宣稱零 I/O。
- 發現村莊有安全空位就建立靜態不可戰鬥傷害／轉化／搬移的盔甲商，保留其他村民。UUID 在 spawn consumer 對外曝光前保存；卸載佔住身分，載回拒重複。每店當地主題＋特色主題共 28 商品，階級 floor(localBaseLevel)，每項八次、30 分鐘補貨；PDC 補貨時間與原版 offers 保存，開店／重啟管理器不重置庫存。購買時驗身分／級別／價格／售罄，用原版村民消耗實體綠寶石，忽略折扣，無 Vault。村莊消失／人工床鐘村／Waypoint 等未實作。
- /rpgcraft 支援 recipes [theme] [tier] [page]、recipe <id>、materials [page]、status、give <player> <material> [1..64]；give 需 customrpg.crafting.admin，預設 OP；圖鑑以實際 Inventory 身分保護點擊／拖曳／close。工坊／商人不需要 finishrpg；該門檻僅原自然替換保留。
- 新增七類／73 案；完整 **mvn clean verify：358 tests，0 failures／errors／skipped**。159 production Java、50 test 類別、37 YAML；JAR target/CustomRPG-1.0.jar。未啟動 Paper，下一步：實際 ExactChoice／滿包／Shift、多人交易／重啟與卸載、結構 API、trim／遠程攻擊與插件／平衡／效能。新設定改動需重啟，沒有新熱重載。見 [CRAFTING_AND_MERCHANTS.md](CRAFTING_AND_MERCHANTS.md) 與 docs/data/CRAFTING_CATALOG.csv。


## 四種新天賦流派 — 最新接手狀態（2026-10-18）

- 使用者要求更多不同天賦；新增 FROST／STORM／EARTH／LIGHT，各四主動兩被動、每個三級／每級一點。原十流派 140 個定義保留，現十四流派／164 個天賦。新檔 config/talents_additional.yml 自動部署；主檔 talents.yml 同 ID 整個定義優先。
- ExpandedTalentSkills 由 TalentSkillManager 的既有已學／已攜帶／魔力／冷卻管線路由，1～4 主動／5 種族入口保留。八個被動使用 statBonus_ 既有六屬性公式。新招式限定生存／冒險，空手可用，總屬性縮放，不要求施法物品。
- 新招式包含霜線／緩速／碎冰加傷／固定暴風雪，雷電跳躍／扇形推擊／安全水平位移／隨行雷暴，岩甲／震波／地面束縛／同隊守護陣，瞄準隊友或自療／不死加傷／自身淨化／同隊治療領域。傷害走 SkillDamageGuard 與虛擬 HP 實扣判斷，尊重安全區／內建保護區／遮擋／隊伍與 PvP；治療可取消；Boss 緩速半時。
- 共用五 ticks timer，領域全服 64、每人三個，攻擊最多 32 目標、治療最多 16 附近玩家，每秒脈衝不補發延遲。岩甲與守護陣取最高值；新狀態在死亡、離線、傳送、模式、重置、成功重載及停用清除，領域區塊卸載停止。Potion gateway 供離線測試注入，沒有偽造全域 RegistryAccess。
- TalentManager 改嚴格讀兩檔至候選樹後交換；新分支驗證數值／效果接線／等級／GUI 欄位／前置與循環。失敗保留原樹與檔案，不清領域。/talent reload 修正為真重讀，成功重算線上玩家屬性／被動並關閉天賦 GUI、清新領域，保留投點與既有記憶體冷卻。沒有重寫舊技能的所有 timer 或跨檔交易。
- 主選單 54 格支援十四個 enum，實際 Inventory 身分／drag／close 清理。分支命令與 Tab 全接，warrior→weapon、mage→fire；新增效果數值有中文顯示。選單布局有改動，操作仍是點入分支／學習／按 1～4 綁定。
- 新增四類測試與既有施法測試兩案，共 54 項；完整 **mvn clean verify：285 tests，0 failures／errors／skipped**。150 production Java、43 test 類別、36 YAML；JAR target/CustomRPG-1.0.jar。未啟動 Paper，下一步驗收實際按鍵／GUI／粒子、多人隊伍／PvP、位移與卸載、保護插件及數值平衡。見 [TALENT_EXPANSION.md](TALENT_EXPANSION.md)。


## 怪物外觀與地形 Boss — 最新接手狀態（2026-10-18）

- 使用者要求優化每種自訂怪的外觀／機制並擴充地形怪與 Boss，已明確選定原版客戶端即可。沒有資源包／模組要求；未宣稱增加自訂骨架。123 舊 ID＋30 新模板＝153 profiles（含未自動部署的四個舊範例），147 個用新通用招式、六個保留專屬控制器。
- 新 package `com.customrpg.mobs`：MobProfile／MobProfileRegistry（嚴格不可變 overlay、錯誤重載保留舊設定）、MobAbility（17 招）、MobEncounterManager（有界共用 timer、蓄力／路徑衝鋒／射線／取消傷害／保護／生命週期）、WildernessBossManager（共用生成嘗試、先存身分、冷卻／TTL／卸載占額、IO 失敗停止 Boss）。
- MobManager 在 spawn consumer 先貼身分並套尺寸，完整包圍盒確認區塊已載入與碰撞後再做持久預約。新外觀優先於舊 disguise，不建立額外顯示實體；舊數值／掉落留存。既有普通池排除 event-only、summon-only 及熔核分裂體。
- MobListener 以已追蹤的 legacyMobs 取代週期全世界掃描；profile 非 legacy 不再執行舊 special-behavior，避免重複招式。SkillDamageGuard 防止招式被覆寫成普通近戰。舊熔核火／岩漿改純粒子能量點，關聯傷害尊重保護／取消。
- 新資源 `config/mobs/encounters.yml`、`config/mobs/types/biome_expansion.yml`、`config/wilderness_bosses.yml` 自動補建。10 地形各兩普通＋一 Boss；稀有 Boss 預設 1%／每世界二隻／1024 格區域 30 分鐘冷卻／20 分鐘 TTL，持久檔 `data/wilderness_bosses.yml`。過期／停用清除不掉落，故障僅停稀有 Boss。
- 新安裝 disabled-worlds 為空以支援 End；舊部署設定不自動覆寫，需合併。自然生怪仍要 /finishrpg，預設 vanilla 不變。模型 entity 改動只影響新生成，重載不替換舊 UUID；現有裝備不強制換色／替換。模板數值／掉落變更要重啟。
- `/custommob spawn <id> [level]` 支援指定等級，info 顯示模型／招式（legacy 顯示真實原機制）；/mobspawn reload 讀 profile 與荒野 Boss 規則。首領不具 tag 不會因此進稀有池，手動 spawn 不占這套持久區域配額。
- 新增五類 38 案例；完整 **mvn clean verify：231 tests，0 failures／errors／skipped**。149 production Java、39 test 類別、35 YAML，JAR target/CustomRPG-1.0.jar。未啟動 Paper；下一步為 AI、碰撞、真實傷害／插件組合、熱重載／重啟、負載及初始平衡驗收。詳見 [MOB_ENCOUNTERS.md](MOB_ENCOUNTERS.md)。

下方自然生怪排查的「沒有擴增模板」及舊測試數屬前一階段紀錄；最新內容與驗證以上段為準。仍未取得使用者實服 status，不將初始自然生怪問題冒稱為已確認原因。

## 自然生怪排查 — 最新接手狀態（2026-10-18）

- 使用者回報看不到自訂怪自然生成；已請提供 `/mobspawn status`，尚未取得實服輸出，不能宣稱已確認或修好實服原因。
- 原設計每次重啟需 `/finishrpg`，預設 vanilla 僅替換 NATURAL 敵怪；沒有默改為補怪、自動啟動或修改世界規則。`config/mob_spawner.yml` 的 `enabled: true` 不等同啟動。
- DistanceSpawnManager 來源值去空白／轉大寫並驗證，CUSTOM／COMMAND 永不接管。WorldSpawnListener 在 MONITOR 觀測取消數，但僅為未取消且可接管的事件排程到下一 tick；生成成功才移除原怪。
- status 加入獨立自然替換最後結果、全服事件／取消／成功數、跳過原因、世界生怪規則／上限／玩家資格與腳下模板篩選。定時 debug 摘要即使來源事件為 0 或尚未啟動也會輸出。FinishRpgCommand 共用目前阻擋診斷，避免顯示世界已關閉生怪卻仍說環境通過。
- Paper 1.21.11 使用 GameRules.SPAWN_MOBS（指令 minecraft:spawn_mobs，舊 doMobSpawning）；為離線測試注入規則讀取函數，沒有建立假 RegistryAccess。原版 spawn cap 飽和、Paper per-player/simulation settings 及其他插件仍需實服查。
- 新增 2 個 test 類別／14 項案例，完整 `mvn verify` **193 tests／0 failures／0 errors／0 skipped**。JAR `target/CustomRPG-1.0.jar`；production Java 144、test 類別 34、YAML 32。詳見 [MOB_SPAWNING.md](MOB_SPAWNING.md)。

下一步：依使用者實服 status 判讀未啟動、無原版來源、事件取消、環境／時段／等級池或生成被拒；現有模板部分組合確實有空缺，但本輪沒有跨區／跨時段回退或擴增模板。

## 技能快捷鍵與種族主動技能 — 最新接手狀態（2026-10-18）

- 本次需求：移除天賦機制物品，改快捷鍵施放，5 固定種族技能，十二族不同能力；龍裔需翅膀／飛行／火焰攻擊。已完成對應程式、GUI 與操作說明，見 [SKILL_SWITCH_SYSTEM.md](../SKILL_SWITCH_SYSTEM.md)。
- SkillBar 預設開啟，1～4 天賦／5 種族／6～9 手持。偏好 PDC 保存；啟用／登入時若選在保留格，改選第 6 格。取消施法時的欄位切換，保留原手持物。原版無任意鍵盤讀取，滾輪選取也會觸發；文件已交代。
- 移除 SkillSwitchManager／SkillSwitchHintListener，舊 SkillListener 不再註冊；SkillTriggerListener 僅處理武器內建技能。TalentSkillManager 直接入口強制 ACTIVE、已學、已攜帶；移除材料及武器種類閘門，保留傷害來源、魔力／生命／冷卻。舊 YAML mechanism／trigger 保留讀取相容但不判斷；不覆寫部署檔，不清空普通背包物品。
- RacialSkill 定義十二族的單一主動能力；RacialSkillManager 一個 5 tick 共用 task 管持續狀態／40 點粒子雙翼／下降保護。移速只改 customrpg:racial_skill_speed transient modifier。種族單一 PDC 冷卻跨重登／正常重啟並防換族繞過；天賦沿用記憶體冷卻。
- 飛行僅撤回自身授予的 allowFlight；保留既有飛行與創造／旁觀模式。PDC 授權殘留在登入／啟動回收；空中收翼限制下降、一次性摔落保護，最長 60 秒，剩餘時間可跨重登；停用以原版緩降銜接。持續 buff 在死亡、登出、傳送、模式切換、換族／重置、成功種族重載及停用清理。
- 傷害 listener 註冊於 Weapon／Stats 之後、Mob 虛擬 HP 轉換之前；有 SkillDamageGuard 防重入。有效命中才點燃／消耗首擊，取消傷害不吸血／擊退；治療與點燃尊重可取消事件。射線／範圍技能檢查遮擋、安全區與敵我，範圍最多 32 目標；天使同隊以 scoreboard team 成員判定。
- 驗證：新增 3 個 test 類別、41 個案例；完整 `mvn clean verify` **179 tests／0 failures／0 errors／0 skipped**。production Java 144、test 類別 32、YAML 32；JAR target/CustomRPG-1.0.jar。沒有啟動 Paper，鍵盤封包／雙翼外觀／反作弊／多人 PvP 尚待實服。原七族數值未自動改動，不能宣稱已完成戰鬥平衡。

下方「尚未開放種族技能」與較舊測試數為歷史階段，現況以本段與技能快捷鍵文件為準。

## 種族擴充 — 最新接手狀態（2026-10-18）

- 使用者要求新增種族並評估／優化原有種族；本輪新增龍裔、獸裔、暗精靈、半身人、魔像族，共十二族。獨立 `config/races_additional.yml` 自動建立，原 `races.yml` 同 ID 逐欄位優先；不覆寫舊玩家選擇或原七族數值。
- RaceManager 嚴格載入設定與玩家檔，設定驗證後才交換；選族／直接換族／重置先原子儲存，再套用效果。壞檔鎖定寫入與選擇；未知／停用種族 ID 保留而效果歸零。玩家資料可用性可查，管理員指令不再在儲存前刪除原選擇。
- 成長統一從 level - 1 計算；魔力由 PlayerStatsManager.recalculateMana 統一整合等級／種族／裝備／天賦。EquipmentManager、TalentPassiveEffectManager 與 RaceManager 都使用此公式；ManaManager 不再每秒解析 lore 重複加回魔。
- 種族移速採 `customrpg:race_speed` transient modifier，不覆蓋其他來源；一個共用每秒 task 做脫戰回復，預設基礎 0.5 HP/s、傷害後八秒，負種族回復會降低最終速率而不扣血。治療尊重可取消事件；登入／重生重算、登出／停用清理。
- GUI 原本固定七格，已擴為每頁 28 個圖示、支援多頁；驗證實際 Inventory、取消點擊／拖曳、清理關閉狀態。十四個原種族技能 ID 仍只有規劃，UI 明確標示尚未開放。StatCalculator 納入種族屬性／暴擊；WeaponListener 近戰有預設暴擊倍率與種族／裝備敏捷；RaceListener 在原版弓發射時保存種族親和／暴擊，再於 HIGH 優先序命中處理，以先於虛擬 HP 消耗。
- 驗證：新增四個 test 類別共 22 項，完整 `mvn verify` 為 **138 tests／0 failures／0 errors／0 skipped**。144 個 production Java、29 個 test 類別、32 份 YAML。JAR `target/CustomRPG-1.0.jar`；尚未啟動 Paper，實服事件順序／GUI 渲染／多人平衡仍待驗收。
- 詳見 [RACES.md](RACES.md) 的十二族表、七族預算與平衡建議、遷移及限制。天使／不死／矮人存在較高預算，保留舊數值並提出優先測量方向；沒有宣稱已做數值平衡或實作主動種族技能。既有天賦的獨立暴擊與部分技能路徑尚待統一。

下方為世界事件開發的歷史階段與當時測試數；以本段與 RACES.md 的最新驗證數為準。

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
