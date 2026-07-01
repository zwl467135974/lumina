<!-- AI 开发规则同步说明：本文件与 .cursorrules / CLAUDE.md / AGENTS.md / .github/copilot-instructions.md 保持一致，修改规则时请同步所有文件 -->
# Lumina 椤圭洰寮€鍙戣鍒?

鏈枃浠跺畾涔変簡 Lumina 妗嗘灦椤圭洰鐨勫紑鍙戣鑼冨拰 AI 鍔╂墜搴旈伒寰殑瑙勫垯銆?

## 椤圭洰鎶€鑳藉寘 (Skills)

鏈」鐩寘鍚涓紑鍙戞妧鑳藉寘锛屼綅浜?`skills/` 鐩綍涓嬨€侫I 鍔╂墜搴旀牴鎹笂涓嬫枃鑷姩璇嗗埆骞朵娇鐢ㄧ浉鍏虫妧鑳藉寘锛?

### 1. lumina_code_style
- **鐢ㄩ€?*: 浠ｇ爜椋庢牸鍜屽懡鍚嶈鑼?
- **璺緞**: `skills/lumina_code_style/SKILL.md`
- **瑙﹀彂鏉′欢**: 缂栧啓 Java 浠ｇ爜銆佸垱寤烘柊绫汇€佷唬鐮佸鏌ユ椂

### 2. lumina_architecture
- **鐢ㄩ€?*: 绠€鍖栧垎灞傛灦鏋勮鑼?
- **璺緞**: `skills/lumina_architecture/SKILL.md`
- **瑙﹀彂鏉′欢**: 璁捐妯″潡缁撴瀯銆佸垱寤烘柊妯″潡銆佺粍缁囦唬鐮佸眰娆℃椂

### 3. lumina_mybatis_plus
- **鐢ㄩ€?*: MyBatis-Plus 浣跨敤瑙勮寖
- **璺緞**: `skills/lumina_mybatis_plus/SKILL.md`
- **瑙﹀彂鏉′欢**: 缂栧啓鏁版嵁搴撹闂唬鐮併€佸垱寤?Mapper銆佺紪鍐?SQL 鏃?

### 4. lumina_api_design
- **鐢ㄩ€?*: API 鎺ュ彛璁捐瑙勮寖
- **璺緞**: `skills/lumina_api_design/SKILL.md`
- **瑙﹀彂鏉′欢**: 璁捐 REST API銆佸垱寤?Controller銆佸畾涔?DTO 鏃?

### 5. lumina_domain_model
- **鐢ㄩ€?*: 棰嗗煙妯″瀷瀹炶返瑙勮寖
- **璺緞**: `skills/lumina_domain_model/SKILL.md`
- **瑙﹀彂鏉′欢**: 璁捐棰嗗煙瀹炰綋銆佸垱寤轰笟鍔￠€昏緫銆佸疄鐜伴鍩熸柟娉曟椂

### 6. lumina_json_serialization
- **鐢ㄩ€?*: JSON 搴忓垪鍖栬鑼?
- **璺緞**: `skills/lumina_json_serialization/SKILL.md`
- **瑙﹀彂鏉′欢**: 澶勭悊 JSON 搴忓垪鍖栥€佸垱寤?DTO銆侀厤缃?Jackson 鏃?

### 7. lumina_git_commit
- **鐢ㄩ€?*: Git Commit 淇℃伅鐢熸垚瑙勮寖
- **璺緞**: `skills/lumina_git_commit/SKILL.md`
- **瑙﹀彂鏉′欢**: 鐢熸垚 Git 鏆傚瓨鍖哄彉鏇寸殑鎻愪氦淇℃伅鏃?

## 鎶€鑳藉寘浣跨敤璇存槑

1. **鑷姩璇嗗埆**: AI 鍔╂墜搴旀牴鎹敤鎴疯姹傚拰浠ｇ爜涓婁笅鏂囪嚜鍔ㄨ瘑鍒渶瑕佷娇鐢ㄧ殑鎶€鑳藉寘
2. **鎸夐渶鍔犺浇**: 浼樺厛鍔犺浇鎶€鑳藉寘鐨勫厓鏁版嵁锛岄渶瑕佹椂鍐嶅姞杞藉畬鏁村唴瀹?
3. **寮曠敤瑙勮寖**: 浣跨敤 `@skills/lumina_xxx/SKILL.md` 鎴?`skills/lumina_xxx/SKILL.md` 鏉ュ紩鐢ㄧ壒瀹氭妧鑳藉寘

## 椤圭洰缁撴瀯瑙勮寖

- **鍩虹鍖呭悕**: `io.lumina`
- **妯″潡鍖呭悕**: `io.lumina.{domain}`
- **妯″潡鍒掑垎**: 
  - `lumina-common`: 鍏叡宸ュ叿绫?
  - `lumina-framework`: 妗嗘灦閰嶇疆
  - `lumina-agent-core`: Agent 鏍稿績
  - `lumina-gateway`: 缃戝叧妯″潡
  - `lumina-business-base`: 涓氬姟鍩虹妯″潡
  - `lumina-business-agent`: Agent 涓氬姟妯″潡
  - `lumina-frontend`: 鍓嶇妯″潡

## 浠ｇ爜瑙勮寖瑕佺偣

1. **鍛藉悕瑙勮寖**: 閬靛惊 Lumina 浠ｇ爜椋庢牸瑙勮寖锛堣 `lumina_code_style` skill锛?
2. **鏋舵瀯瑙勮寖**: 閬靛惊绠€鍖栧垎灞傛灦鏋勶紙瑙?`lumina_architecture` skill锛?
3. **API 璁捐**: 閬靛惊 RESTful 瑙勮寖锛堣 `lumina_api_design` skill锛?
4. **鏁版嵁搴撴搷浣?*: 浣跨敤 MyBatis-Plus锛堣 `lumina_mybatis_plus` skill锛?
5. **JSON 澶勭悊**: 浣跨敤 Jackson锛堣 `lumina_json_serialization` skill锛?
6. **棰嗗煙寤烘ā**: 閬靛惊棰嗗煙妯″瀷瀹炶返锛堣 `lumina_domain_model` skill锛?

## Git Commit 瑙勮寖

鐢熸垚 Git Commit 淇℃伅鏃讹紝閬靛惊 `lumina_git_commit` skill 涓殑瑙勮寖锛?

- 鏍煎紡: `<type>(<scope>): <subject>`
- 绫诲瀷: feat, fix, docs, style, refactor, perf, test, chore, build
- 浣跨敤涓枃鎻忚堪
- 璇︾粏鎻忚堪澶у瀷鍙樻洿

## 寮€鍙戝伐浣滄祦

1. 鍒涘缓鏂板姛鑳芥椂锛屽厛妫€鏌ョ浉鍏虫妧鑳藉寘
2. 缂栧啓浠ｇ爜鏃讹紝鑷姩搴旂敤鐩稿叧瑙勮寖
3. 鎻愪氦浠ｇ爜鍓嶏紝鐢熸垚绗﹀悎瑙勮寖鐨?Commit 淇℃伅
4. 浠ｇ爜瀹℃煡鏃讹紝妫€鏌ユ槸鍚︾鍚堥」鐩鑼?

