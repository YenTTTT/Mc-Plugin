# 刺客系命中與傷害訊息修復

**COMPLETED（程式碼）／NEEDS_VERIFICATION（Paper 遊戲驗收）**，2026-10-24。

## 問題與修正

- **要害打擊、背刺強化**：原本方向點積 `> 0.5` 實際判定正面，改為背面 `< -0.5`。以水平面判定，目標抬頭、低頭或小幅高低差不影響背刺；同位置不算背刺。維持原本加成、學習等級及被動類型。學習後持武器從敵人背後普通攻擊即可自動觸發，不用裝備到快捷槽。
- **剛學會／升級／重置後效果不同步**：`TalentManager.applyTalentEffects` 現在同時刷新被動戰鬥快取；學習、升級、重置與重新套用即時生效。移除 `/talent reload` 重複的刷新呼叫，不必重新登入。
- **刺客技能打玩家／連擊吞段**：影襲、鬼哭神嚎、快速斬擊、十字刃、幻影連斬、弧形斬、次月影、死神降臨共用既有合法敵人判定，允許 PvP 開啟時攻擊有效的敵對生存／冒險玩家。每次已判定命中的技能攻擊獨立結算，避免近期普通攻擊及前一段留下的原版受傷保護時間吞掉技能。僅在同步傷害呼叫期間暫時清除受傷幀，`finally` 恢復保護；不是移除 `isInvulnerable` 或取消保護插件。
- **保護與生命週期**：保留世界 PvP 開關、同隊、安全區、保護區、視線、有效目標與召喚物規則。持續攻擊在施法者登出、死亡、換世界、進入保護區時停止。影襲不會在瞬移遭取消時宣稱命中，也不能傳進安全區；瞬移後重新驗證敵人。鬼哭神嚎在中斷或插件停用時還原原本無敵旗標，原先已無敵者不會被改成可受傷。
- **整合傷害訊息**：不再獨立發送「背刺！」「收割！」訊息，影襲也移除使用請求傷害值的額外傷害宣告。加成資料依傷害事件保存並消費，避免多人或同 Tick 多次傷害串到別招。正式傷害提示同時支援玩家與怪物，使用最終傷害及虛擬生命換算；被取消、零傷害或延後取消的事件不顯示成功命中。

範例（數值僅展示格式）：

`玩家 對 敵人 造成了 22.5 傷害 + 背刺(+80%) + 收割(+25%)（加成已計入） 敵人剩餘血量 77.5/100 HP`

加成百分比為該次實際觸發的既有天賦倍率；前面的傷害已是減傷、共用傷害預算等流程處理後的事件結果，不能再把百分比或標籤當成額外扣血。實際公式、魔力、冷卻、技能段數、傷害預算上限及回復率都沒有調整；只修正原本被受傷幀抑制的技能段數。沒有重置玩家天賦、修改部署設定或開關世界 PvP。

## 修改與測試

正式修改：`TalentPassiveEffectManager`、`TalentManager`、`TalentSkillManager`、`DamageDisplayManager`、`DamageDisplayListener`、`TalentCommand`、`CustomRPG`。刺客原有音效改用從本機 Paper 1.21.11 API sources 核對過的相同 sound key，聲音種類與參數不變。

新增測試：`AssassinPassiveTest`、`AssassinLearningTest`、`AssassinSkillTest`、`DamageDisplayListenerTest`。涵蓋背面／正面／側面／重疊位置、目標俯仰與轉向、背刺與收割疊加、事件隔離、快取即時更新與重置、實際主動技能執行路徑對玩家的傷害、近期武器受傷幀、連擊段數、取消傷害、PvP 關閉、隊伍、視線、安全區、無效玩家、登出／死亡／換世界、傳送失敗及無敵旗標清理。

**NEEDS_VERIFICATION**：這些是 Java／Mockito 測試，不等於已登入 Paper。仍需實服以兩名玩家驗收全部八招，尤其涉及藥水效果的幻影連斬和影襲成功路徑、鬼哭神嚎結束隱身、第三方保護插件及多人／高血量怪物。測試替身的受傷幀模型刻意簡化，不能視為原版傷害引擎或 TPS 實測。

最後建置與部署收據見本檔下方。

## 本批驗證與安裝

**COMPLETED**：最終 `mvn verify`，**860 tests，0 failures／errors／skipped**，日誌 `target/assassin-verify.log`。JAR ZIP CRC 正常，633 個 class／resource 與 `target/classes` 逐項一致；詳見 `target/assassin-artifact.json`。本批新增 38 項回歸測試。

於 **2026-10-24 12:09:53 Asia/Taipei** 安裝。部署前再次偵測伺服器已停止，備份後直接更新 `D:/下載/McServer/plugins/CustomRPG-1.0.jar`；當時沒有 `plugins/update` 暫存檔，本次亦未新增暫存版本。沒有開／停伺服器，沒有修改玩家資料或既有設定；下次完整啟動載入，`/talent reload` 不會重新載入 Java 類別。

- 產物：`target/CustomRPG-1.0.jar`，1,785,221 bytes。
- SHA-256：`37fb4e47274e8c0294914687ca15fe0eac7f6f269bc68d8f5c1a2419150f53c4`，來源與安裝檔一致。
- 備份：`D:\下載\McServer\backups\CustomRPG\assassin-20261024-040953Z/previous-live.jar`。
- 收據：`target/assassin-deployment.json` 與備份目錄內 `deployment.json`。
- **NEEDS_VERIFICATION**：Paper 實服完整施放／第三方保護相容性／遊戲畫面；本批沒有進行登入實測。
