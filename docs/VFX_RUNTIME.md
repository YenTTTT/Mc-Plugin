# 技能 VFX 首版：預覽、引擎與驗收

> **2026-10-24 養雞第二版**：雞蛋生物命中不再播自訂Shell；FEED中心HAPPY_VILLAGER、HEALER愛心HEART；NEST實戰真COMPOSTER、預覽BlockDisplay僅本人可見。GOD651tick動畫，Runtime最大長度660tick，其餘粒子／發送預算不增。真小雞不限量、粒子裝飾仍受預算。43 DNA／240盤點同步，詳細 [CHICKEN_RUNTIME.md](CHICKEN_RUNTIME.md)，實服畫面及原生實體負載待驗收。

> **養雞新增（2026-10-24）**：新增8套獨立動畫及預覽，現43 DNA／240盤點。`/vfx preview chicken_god` 與 `chicken_rooster` 使用真正原版雞，預覽只對本人可見、無傷且不孵化；`/vfx stop`同步清除。實戰雞神為SCALE10真實戰鬥實體，粒子品質不改傷害與生命；既有粒子／發送預算不增加。`/vfx stats`新增雞、飛蛋、原生預覽與場域計數。見 [CHICKEN_RUNTIME.md](CHICKEN_RUNTIME.md) 的量測及人工驗收；實際模型與多人負載仍待驗收。

> **2026-10-24 尖牙更新**：骨脊裂矛依使用者要求改為原版EvokerFangs視覺，原技能傷害判定與數值保留；預覽同樣顯示無傷害尖牙。這招使用有數量上限的暫時原版實體，舊版全粒子敘述對此招已不適用。見 [NECROMANCER_RUNTIME.md](NECROMANCER_RUNTIME.md) 的尖牙更新及人工驗收。

> **亡者君臨更新（2026-10-23）**：新增八套死靈獨立動畫與無傷預覽，`/vfx preview necro_gate`。目前35 DNA、232盤點；冥門矩形警示優先、三排粒子軍團與受控真實僕從分離，動態面向於30tick鎖定。動畫最長260tick，其餘粒子／發送預算不增加。見 [NECROMANCER_RUNTIME.md](NECROMANCER_RUNTIME.md)，實際視覺仍待人工驗收。

> **永夜禁術更新（2026-10-23）**：新增永夜禁術 8 套獨立實戰動畫，`/vfx preview dark_eclipse` 可預覽。當前 27 份 DNA、224 筆盤點，原暗黑 12 招以別名對應新 8 招；傷害、回聲與預覽隔離。詳見 [DARK_RUNTIME.md](DARK_RUNTIME.md)，遊戲畫面仍待人工驗收。

> **現行大地更新（2026-10-23）**：新增大地 8 套獨立動畫及實戰接線，`/vfx preview earth_titan` 可預覽；現共 19 份 DNA、228 筆定義。大地與冰霜仍待遊戲畫面驗收，操作、預算與新量測見 [EARTH_RUNTIME.md](EARTH_RUNTIME.md)。下方先前批次數量為歷史。

**最新更新（2026-10-23）**：新增 8 套冰霜獨立動畫，已接實際施法／命中；`/vfx` 是 `/rpgvfx` 別名，OP 可用 `/vfx preview frost_crown` 逐招驗收。原 3 展示仍保留，共 11 份 DNA，現行清單為 224 筆。新增 SNOWFLAKE／END_ROD／BLOCK(PACKED_ICE)／CLOUD、動態錨點及獨立動畫範圍，王權優先 100、護鏡 60、其他冰霜 40；每人／全域預算不變。8 招機制、預覽 ID、651 項測試與 headless 量測見 [FROST_RUNTIME.md](FROST_RUNTIME.md)。**實際遊戲視覺仍 NEEDS_VERIFICATION**。下方三展示首版的數量／僅預覽敘述保留作為歷史紀錄，不代表冰霜目前狀態。

2026-10-23（八大主題撤除後更新），Paper 1.21.11／Java 21。**COMPLETED：審計、獨立VFX Runtime、三種獨立無傷害演出、管理員預覽及自動化建置。NEEDS_VERIFICATION：Minecraft實際觀感、音量、多人TPS／封包與第三方插件。** 沒有用編譯或合成測試冒充畫面驗收。

審計見 [VFX_AUDIT.md](VFX_AUDIT.md)；現行220筆定義（200舊天賦＋20其他定義）見 [VFX_SKILL_INVENTORY.csv](data/VFX_SKILL_INVENTORY.csv)；首批三招的獨立視覺資料見 [VFX_DNA.csv](data/VFX_DNA.csv)。其他技能尚未批次遷移。

## 直接進遊戲預覽

更新JAR並完整啟動伺服器後，使用管理員／OP帳號，在有空間的已載入區域執行：

```text
/rpgvfx preview w01
/rpgvfx preview demo_thunder_judgment
/rpgvfx preview demo_void_collapse
/rpgvfx showcase
/rpgvfx quality high
/rpgvfx stats
/rpgvfx stop
```

2026-10-23已依要求撤除八大主題與80核心；本引擎與三個展示保留，W01實戰接線一併撤除。當前運行版本與更新暫存方式見 [舊版恢復說明](TALENT_LEGACY_RUNTIME.md)。

指令採專案前綴避免 `/vfx` 與其他插件衝突，權限 `customrpg.vfx.admin` 預設op。預覽只發給操作者本人，不執行技能、不學習／綁定天賦、不判傷、不施加狀態，不扣HP、MP或冷卻。Console可查stats；其他操作需要玩家。使用生存／冒險／創造模式；旁觀模式不啟動。

`w01` 與完整ID `v3_weapon_crescent_slash` 都可使用；另外接受 `thunder_judgment`、`void_collapse` 短名。`showcase` 依序排入斬擊、雷霆、虛空，延遲0／30／80ticks；仍共用單一Runtime任務。預覽固定seed=42，可在同一位置比較品質與重播。原版粒子的自然壽命不受伺服器控制；stop取消後續幀，已發送的粒子自行消散。

品質預設MEDIUM，指令調整自己的新引擎特效觀察品質，登出後恢復預設。目前三招皆僅供預覽，不接入舊天賦的戰鬥與命中。質量與預覽都不使用材質包、Display、自訂模型、ModelEngine或MythicMobs。

## 三招的實際狀態

| 技能 | 造型及時間 | 本次接線 |
| --- | --- | --- |
| W01 弧月斬 | 100度非均勻厚度刃帶，0～4tick快速掃出；斷裂刃尖、錯位殘影於2～11tick消散。可隨完整面向／俯仰旋轉。 | 獨立純預覽。原核心W01／W09及其實戰接線已撤除；完整ID僅作預覽相容別名。 |
| 雷霆審判・預覽 | seed折線主幹＋側枝；0～5tick角形預示，6tick落雷高潮，14／22tick跳接；38tick結束。 | 純預覽，端點為合成展示座標；不能宣稱它已依E03或獵人真實命中鏈播放。這一步待該技能的戰鬥接線與視覺驗收後再做。 |
| 虛空崩解・預覽 | 七片不同半徑、角速度及高度的三維多邊形向心收束；29～35tick停頓／留白，36～57tick向外翻碎。 | 純預覽，未替換A04、舊 `void_eruption` 或其他戰鬥技能。 |

W01不再是可學習天賦，預覽不執行扇區選敵、判傷、MP或CD。原設計DNA中的近戰定位與成功命中切口描述，是效果的設計來源；目前沒有戰鬥執行器觸發它們。

## 模組與實際修改

| 檔案 | 職責 |
| --- | --- |
| [VfxMath.java](../src/main/java/com/customrpg/vfx/VfxMath.java) | 不可變向量、穩定局部座標、Rodrigues旋轉、3D Bezier／Catmull-Rom、線段、參數曲面／刃帶、非均勻螺旋、seed多邊形／分叉折線。只算座標。 |
| [VfxTimeline.java](../src/main/java/com/customrpg/vfx/VfxTimeline.java) | 各視覺層獨立Track、Linear／Cubic In／Out／InOut、Scale／Rotation／Translation。沒有所有技能必用的共同五段模板。 |
| [VfxRuntime.java](../src/main/java/com/customrpg/vfx/VfxRuntime.java) | 單執行緒動畫實例、延遲、到期／取消、品質／LOD、按層與技能優先級分配取樣和發送預算、實際送出計數。Transport只提供生命週期、觀眾、粒子與聲音。 |
| [VfxEffects.java](../src/main/java/com/customrpg/vfx/VfxEffects.java) | 三份獨立DNA與三套動畫程式，另有只在成功斬擊命中時啟動的切口。每招自己的時間分支、輪廓及取樣上限。 |
| [BukkitVfxService.java](../src/main/java/com/customrpg/vfx/BukkitVfxService.java) | 唯一Bukkit適配器：一個1tick排程、玩家／世界／目標有效性、可見性、已載入區塊、typed Dust與DustTransition、count=0方向性CRIT、定點音效。 |
| [VfxCommand.java](../src/main/java/com/customrpg/vfx/VfxCommand.java) | `/rpgvfx` 權限、預覽、串場、品質、停止及統計。沒有戰鬥服務引用。 |
| [CustomRPG.java](../src/main/java/com/customrpg/CustomRPG.java)、[plugin.yml](../src/main/resources/plugin.yml) | 引擎啟停、事件與指令／權限註冊。 |

VFX原任務未重寫既有天賦數值；後續依要求撤除八大主題、核心YAML與戰鬥執行器。武器等級提示保留，玩家資料未批次修改。工具 `python tools/vfx/audit_inventory.py` 重新核對ID／輸出清單；`python tools/vfx/export_dna.py` 匯出三份Java DNA常數，CSV不會自行生成技能程式。

## 品質與預算

目前預設值集中於 `VfxRuntime.Limits.defaults()`；這版沒有新增可任意放大特效的YAML DSL或熱重載框架。

| 限制 | 數值／處理 |
| --- | --- |
| 活躍實例 | 全服64、每持有者6，包含延遲展示；高優先主形可取代較低優先切口／預覽 |
| 角色優先 | 實戰主形30、成功命中切口20、預覽10；預警先，輪廓與命中為關鍵組，組內依角色優先，再處理主體及裝飾 |
| 每技能取樣／tick | W01最多192、雷霆256、虛空256、命中切口32；Runtime硬檢查，任何效果至多384 |
| 全域取樣／tick | 8192；一半保留給預警／命中／輪廓，另一半給主體／裝飾；每輪實例在同優先級內輪替處理 |
| 每觀看者發送／tick | LOW 96、MEDIUM 160、HIGH 240，上限跨新引擎所有實例累計 |
| 品質取樣 | LOW移除裝飾、輪廓隔點取樣、主體1/4；MEDIUM保留輪廓、主體1/2、裝飾1/4；HIGH完整。預警／命中不因品質主動減樣，仍受硬預算 |
| 發送與觀看範圍 | 全服每tick4000粒子送出、16000送出檢查、24聲音送出；每實例至多檢查64個候選觀看者；16～32格再降低取樣，32格外不送 |
| 粒子資料 | Dust／DustTransition各有正確資料；初始位置offset=0；方向性火星count=0、offset作方向、extra作速度；一個點一顆，不用大count繞預算 |
| 低TPS | 每次Runtime更新只畫當下tick幀，不補打積欠畫面；單調時間另有時限，超時直接終止，不觸發傷害 |

上述全域預算只涵蓋已接入新Runtime的效果。尚未遷移的CoreVisuals、HunterVisuals、Expanded或舊TalentSkillManager不會自動納入；不能把新引擎數字當作整個插件的總粒子上限。

死亡、離線、成功傳送、換世界、切模式、W01卸下／重置／成功天賦重載及插件停用會取消對應動畫。Runtime也檢查綁定的可選目標失效；目前首招固定取樣與預覽不生成目標實體。停用取消唯一排程、清理實例／品質／粒子資料快取。已送至客戶端的原版粒子僅自然消散，沒有遺留展示實體或方塊。

## 驗證與量測

完整 `mvn verify`：**679 tests，0 failures／errors／skipped，BUILD SUCCESS**。紀錄：[vfx-verify.log](../target/vfx-verify.log)，產物：[CustomRPG-1.0.jar](../target/CustomRPG-1.0.jar)。

- `VfxMathTest`：八種朝向含垂直上下、正交／手性、曲線端點、非均勻縮放／旋轉、seed再現、曲面取樣與整數溢位上限、時間軸及Ease。
- `VfxRuntimeTest`：到期／延遲／取消／停止、牆鐘逾時不追幀、角色或目標失效、效果錯誤隔離、全域／個人／聲音／工作量限制、品質／LOD、灰階下的幾何跨度與不同時間、第二段主形優先、逐技能預算。
- `BukkitVfxServiceTest`：原版typed Dust／DustTransition、count=0方向性火星、預覽僅本人、傳送／登出／換世界、未載入區塊不載入。
- `VfxCommandTest`：非管理員拒絕、ID解析、三段展示與僅停止自己的預覽、唯一BukkitTask及shutdown。
- 核心執行器及其測試已隨八大主題撤除；現行607項測試包含舊版恢復／退休技能拒絕施放，見 `TalentLegacyRuntimeTest`。

`VfxLoadTest` 實際執行了各160次Runtime更新的單人與32施法者／32觀看者合成測試，記錄當次JVM的本地Runtime CPU時間及計數，輸出 [vfx-load-report.csv](../target/vfx-load-report.csv)。本輪計數峰值：單人最多2實例、248取樣／240送出；32人最多64實例、5669取樣／4000送出，送出檢查峰值4024，均未超過預設上限。

**這是headless計數／本地CPU測試**：模擬Transport不做真實Bukkit世界查詢、Minecraft粒子序列化、封包傳輸或客戶端繪製，表內毫秒不能用來推估實服TPS、FPS或玩家容量。CPU時間每次執行會變，請以CSV當次結果為準；實服需另量測。

## 人工差異與品質驗收表

| 檢查 | W01 | 雷霆樣本 | 虛空樣本 |
| --- | --- | --- | --- |
| 不看顏色仍能辨識 | 待看畫面：刃帶 | 待看畫面：直向折線 | 待看畫面：多面碎片 |
| 獨特輪廓／至少三維度有差異 | 程式已獨立；觀感待驗 | 程式已獨立；觀感待驗 | 程式已獨立；觀感待驗 |
| 運動與時間辨識 | 快掃／錯位殘影，待驗 | 突現／停閃／跳接，待驗 | 向心／停頓／外翻，待驗 |
| 招牌時刻集中，非每tick同亮度 | 待驗刃尖高潮 | 待驗第6tick主幹 | 待驗第36tick釋放 |
| 節奏符合判傷 | 純預覽，無實戰判傷 | 純預覽，尚未驗實戰 | 純預覽，尚未驗實戰 |
| 沒有只換色／放大 | 不同幾何與分支程式已驗 | 不同幾何與分支程式已驗 | 不同幾何與分支程式已驗 |
| 不遮蔽敵人／範圍／環境 | LOW/MEDIUM/HIGH逐一待驗 | 同左 | 同左 |
| 多人執行與場景負載 | 合成計數通過；實服待驗 | 同左 | 同左 |

建議在平地、狹窄通道、斜坡與直視上下時各跑一次，繞到側面看刃帶厚度及碎片深度；同位置用三種品質重播，確認裝飾減少仍看得懂主形。接著在測試服安排多人同時施放，量測 `/rpgvfx stats` 與Paper profiler／封包、收集不同客戶端粒子設定的畫面。**人工驗收前，不宣告視覺品質合格，不繼續批次替換其他戰鬥技能。** 暫停、單tick步進與特效全量遷移未包含在首版。
