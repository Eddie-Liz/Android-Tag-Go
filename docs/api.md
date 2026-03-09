# Tag&Go API Reference

---

## Base Configuration

| Item | Value |
|------|-------|
| Base URL (Production) | `https://api.rooticare.com` |
| Base URL (SIT) | `https://192.168.103.17` |
| Content-Type | `application/json` |
| Protocol | HTTPS |

### Authentication

API 使用 OAuth 2.0 Client Credentials 流程取得 token，再以 Bearer Token 存取資料 API。

**Step 1 - Get Access Token**

```
POST /oauth/token
```

Headers:
```
Authorization: Basic dVlqeHgzbGU0enM3U2lqQ3M1VW5tZHpHWGRZSmhKUEo6dVZNbVB4eE5MYzF2ckJUREtuU1daa2g1cmlJTHh2dEw=
```

Body:
```json
{
  "grant_type": "client_credentials"
}
```

Response:
```json
{
  "access_token": "eyJhbGciOi..."
}
```

**Step 2 - Use Bearer Token**

所有資料 API 皆使用 Step 1 取得的 token：

```
Authorization: Bearer {access_token}
```

**Authentication Types Summary:**

| Type | Headers | Usage |
|------|---------|-------|
| GetToken | `Authorization: Basic {base64(client_id:client_secret)}` | 取得 access token (`POST /oauth/token`) |
| CommonData | `Authorization: Bearer {token}` | 所有資料 API |

**Retrofit Setup Example:**

```kotlin
// OkHttp Interceptor for Bearer Token
class AuthInterceptor(private val tokenProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer ${tokenProvider()}")
            .build()
        return chain.proceed(request)
    }
}

// Retrofit instance
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(AuthInterceptor { savedToken })
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.rooticare.com")
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService = retrofit.create(RootiApiService::class.java)
```

---

## API Endpoints

### 1. Get Token

取得 OAuth 2.0 access token，所有後續 API 皆需使用此 token。

| Item | Value |
|------|-------|
| **Method** | `POST` |
| **Path** | `/oauth/token` |
| **Authentication** | Basic Auth (Client Credentials) |

**Request Headers:**

```
Authorization: Basic dVlqeHgzbGU0enM3U2lqQ3M1VW5tZHpHWGRZSmhKUEo6dVZNbVB4eE5MYzF2ckJUREtuU1daa2g1cmlJTHh2dEw=
Content-Type: application/json
```

**Request Body:**

```json
{
  "grant_type": "client_credentials"
}
```

**Response:**

```
HTTP 200 OK
Content-Type: application/json
```

```json
{
  "access_token": "eyJhbGciOi..."
}
```

**Error Response:**

回傳 `access_token` 為 null 或缺少該欄位時視為失敗。

**Retrofit Example:**

```kotlin
interface RootiAuthService {

    @POST("/oauth/token")
    suspend fun getToken(
        @Header("Authorization") basicAuth: String,
        @Body body: Map<String, String>
    ): Response<JsonObject>
}

// Usage
val basicAuth = "Basic dVlqeHgzbGU0enM3U2lqQ3M1VW5tZHpHWGRZSmhKUEo6dVZNbVB4eE5MYzF2ckJUREtuU1daa2g1cmlJTHh2dEw="
val response = authService.getToken(
    basicAuth = basicAuth,
    body = mapOf("grant_type" to "client_credentials")
)
val token = response.body()?.get("access_token")?.asString
```

---

### 2. Auth Patient (Subscribe Patient MCT Events)

驗證並訂閱病患，取得病患資訊。用於登入流程。

| Item | Value |
|------|-------|
| **Method** | `GET` |
| **Path** | `/oauth/vendors/{institutionId}/patients/{patientId}` |
| **Authentication** | Bearer Token |
| **Request Body** | None |

**Request Headers:**

```
Content-Type: application/json
Authorization: Bearer {token}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `institutionId` | String | Yes | Institution/vendor identifier |
| `patientId` | String | Yes | Patient identifier (ID number) |

**Response (200 OK):**

```json
{
  "vendorName": "{institutionName}",
  "subscribedBefore": "true",
  "subscribeTime": "{subscribeTime}"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `vendorName` | String | Institution name |
| `subscribedBefore` | String | 是否曾經訂閱過 (`"true"` / `"false"`) |
| `subscribeTime` | String | 訂閱時間 |

**Error Responses:**

**400 - institutionId 不存在：**

Client Message: `Invalid Institution ID Patient`

```json
{
  "error": "invalid_institution_id",
  "error_description": "institution: [ {institutionId} ] is invalid."
}
```

**400 - patientId 不存在：**

Client Message: `Invalid Patient`

```json
{
  "error": "invalid_patient",
  "error_description": "patient: [ {patientId} ] is invalid."
}
```

**409 - 病患已存在訂閱紀錄：**

Client Message: `This patient is already logged in.`

```json
{
  "error": "patient_already_subscribed",
  "error_description": "this patient : [ {patientId} ] is already subscribed at {subscribeTime}."
}
```

**Retrofit Example:**

```kotlin
interface RootiApiService {

    @GET("/oauth/vendors/{institutionId}/patients/{patientId}")
    suspend fun authPatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<AuthPatientResponse>
}

data class AuthPatientResponse(
    val vendorName: String,
    val subscribedBefore: String,
    val subscribeTime: String
)

data class ApiError(
    val error: String,
    @SerializedName("error_description")
    val errorDescription: String
)

// Error handling
fun handleAuthPatientError(errorBody: ResponseBody?): String {
    val error = Gson().fromJson(errorBody?.string(), ApiError::class.java)
    return when (error?.error) {
        "patient_already_subscribed" -> context.getString(R.string.patient_already_logged_in)
        "invalid_patient" -> context.getString(R.string.invalid_patient)
        "invalid_institution_id" -> context.getString(R.string.invalid_institution_id)
        else -> context.getString(R.string.unknown_error)
    }
}
```

---

### 3. Get Current Measurement Info

取得病患目前的量測資訊（Virtual Tag）。登入成功後呼叫，用於判斷是否正在錄製中及錄製模式。

| Item | Value |
|------|-------|
| **Method** | `GET` |
| **Path** | `/api/v1/institutions/{institutionId}/patients/{patientId}/measures/currentMeasurement` |
| **Authentication** | Bearer Token |
| **Request Body** | None |

**Request Headers:**

```
Content-Type: application/json
Authorization: Bearer {token}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `institutionId` | String | Yes | Institution/vendor identifier |
| `patientId` | String | Yes | Patient identifier (ID number) |

**Response (200 OK):**

```json
{
  "measureRecordOid": 12345,
  "measureRecordId": "MR-20230808-001",
  "mode": 0,
  "state": 0,
  "launchTime": 1691481600000,
  "expectedEndTime": 1691568000000,
  "deviceId": "DEVICE-UUID-001"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `measureRecordOid` | Integer | Measurement record OID |
| `measureRecordId` | String | Measurement record ID |
| `mode` | Integer | 錄製模式 (see MeasureMode) |
| `state` | Integer | 量測狀態 (see MeasurementState) |
| `launchTime` | Long | 開始錄製時間 (Unix timestamp ms) |
| `expectedEndTime` | Long | 預計結束時間 (Unix timestamp ms) |
| `deviceId` | String | Device UUID |

**MeasureMode enum:**

| Value | Name | Description |
|-------|------|-------------|
| 0 | `VirtualTag` | Holter 模式 |
| 1 | `Monitor` | Monitor 模式 |
| 2 | `MctTag` | MCT 模式 |

> **Android Note:** Android 版本只允許 `VirtualTag` (0) 模式，收到其他模式應視為錯誤並登出。

**MeasurementState enum:**

| Value | Name | Description |
|-------|------|-------------|
| 0 | `Measuring` | 錄製中 |
| 1 | `Uploading` | 上傳中 |
| 2 | `Uploaded` | 已上傳 |
| 3 | `Transmitting` | 傳輸中 |
| 4 | `Transmitted` | 已傳輸 |
| 5 | `Analyzing` | 分析中 |
| 6 | `Downloading` | 下載中 |
| 7 | `Analyzed` | 已分析 |
| 8 | `Recompute` | 重新計算 |
| 10 | `Abandoned` | 已放棄 |
| 11 | `Overdue` | 已過期 |
| 12 | `FailedAnalyzed` | 分析失敗 |

**Client-Side Logic:**

登入流程中，呼叫此 API 後需依序執行以下判斷：

**Step 1 - 判斷是否正在錄製中：**

需同時滿足三個條件才算「錄製中」：
1. `state == 0` (Measuring)
2. `expectedEndTime != 0`
3. `currentTime < expectedEndTime` (未過期)

若不在錄製中，顯示錯誤訊息並登出。

**Step 2 - 判斷錄製模式：**

| mode | Action |
|------|--------|
| `0` (VirtualTag) | 進入主頁，下載歷史紀錄 |
| `2` (MctTag) | *iOS only* - 進入主頁 |
| Other | *Android: 不允許，應登出* |

**Step 3 - `measureRecordId` 儲存規則（Immutable After Login）：**

`measureRecordId` 儲存於 SharedPreferences（`TokenManager`），採用**登入後不可變**策略：

| 事件 | 行為 | 說明 |
|------|------|------|
| 登入（本機為 null） | 寫入 API 回傳值 | 唯一的寫入時機 |
| 登入（本機已有值） | 跳過寫入，僅 log 警告 | 防禦性保護，正常流程不應觸發 |
| 定期狀態檢查（server ID 不同） | 僅 log，不更新 | 後端重新錄製不影響本機 ID |
| 定期狀態檢查（server 無 session） | 僅 log，不更新 | session 被刪除不影響本機 ID |
| 登出（`clearAll()`） | 清除 | 唯一的清除時機 |

> **設計理由：** 防止定期狀態檢查時 server 端 session 變動（後端重新錄製、session 被刪除等）意外覆寫本機 `measureRecordId`，導致已建立的 Event Tag 與錯誤的 session 關聯。`measureRecordId` 一旦於登入時寫入，僅在明確登出時清除。

**Retrofit Example:**

```kotlin
interface RootiApiService {

    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/currentMeasurement")
    suspend fun getCurrentMeasurementInfo(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<MeasurementInfo>
}

data class MeasurementInfo(
    val measureRecordOid: Long,
    val measureRecordId: String,
    val mode: Int,
    val state: Int,
    val launchTime: Long,
    val expectedEndTime: Long,
    val deviceId: String
) {
    fun isMeasuring(): Boolean {
        val now = System.currentTimeMillis()
        return state == STATE_MEASURING
            && expectedEndTime != 0L
            && now < expectedEndTime
    }

    fun isVirtualTagMode(): Boolean = mode == MODE_VIRTUAL_TAG

    companion object {
        const val STATE_MEASURING = 0
        const val MODE_VIRTUAL_TAG = 0  // Holter
        const val MODE_MONITOR = 1
        const val MODE_MCT_TAG = 2
    }
}

// Usage in login flow
val response = apiService.getCurrentMeasurementInfo(institutionId, patientId)
val info = response.body() ?: run { handleError(); return }

// Step 1: Check measuring
if (!info.isMeasuring()) {
    showError("Not in measuring state")
    logout()
    return
}

// Step 2: Check mode (Android only allows VirtualTag)
if (!info.isVirtualTagMode()) {
    showError("Unsupported measure mode")
    logout()
    return
}

// Proceed to download history
loadEventTagHistory()
```

---

### 4. Get Total History Event Tag Count

取得歷史 Event Tag 總筆數。用於登入後判斷是否需要下載歷史紀錄。

| Item | Value |
|------|-------|
| **Method** | `GET` |
| **Path (VirtualTag)** | `/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/totalHistory` |
| **Path (MctTag)** | `/api/v1/institutions/{institutionId}/patients/{patientId}/eventTags/totalHistory` |
| **Authentication** | Bearer Token |
| **Request Body** | None |

> **Android Note:** Android 只使用 VirtualTag 路徑。

**Request Headers:**

```
Content-Type: application/json
Authorization: Bearer {token}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `institutionId` | String | Yes | Institution/vendor identifier |
| `patientId` | String | Yes | Patient identifier |
| `measureId` | String | Yes (VirtualTag) | Measurement record ID |

**Response (200 OK):**

```json
{
  "totalRow": 15
}
```

| Field | Type | Description |
|-------|------|-------------|
| `totalRow` | Integer | 歷史 Event Tag 總筆數 |

**Client-Side Logic:**

```
if totalRow > 0:
    開始分頁下載歷史紀錄 (API #5)
else:
    直接進入主頁
```

**Error Response:**

無特殊錯誤碼，HTTP error 時回傳標準錯誤格式。

**Retrofit Example:**

```kotlin
interface RootiApiService {

    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/totalHistory")
    suspend fun getTotalHistoryCount(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String
    ): Response<TotalHistoryResponse>
}

data class TotalHistoryResponse(
    val totalRow: Int
)

// Usage
val response = apiService.getTotalHistoryCount(institutionId, patientId, measureId)
val totalRow = response.body()?.totalRow ?: 0
if (totalRow > 0) {
    fetchEventTagHistory(pageNumber = 1)
}
```

---

### 5. Get Event Tag History (Paginated)

分頁取得歷史 Event Tag 紀錄。

| Item | Value |
|------|-------|
| **Method** | `GET` |
| **Path (VirtualTag)** | `/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/history?pageSize={pageSize}&pageNumber={pageNumber}` |
| **Path (MctTag)** | `/api/v1/institutions/{institutionId}/patients/{patientId}/eventTags/history?pageNumber={pageNumber}` |
| **Authentication** | Bearer Token |
| **Request Body** | None |

> **Android Note:** Android 只使用 VirtualTag 路徑。

**Request Headers:**

```
Content-Type: application/json
Authorization: Bearer {token}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `institutionId` | String | Yes | Institution/vendor identifier |
| `patientId` | String | Yes | Patient identifier |
| `measureId` | String | Yes (VirtualTag) | Measurement record ID |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `pageNumber` | Integer | Yes | 1 | 頁碼 (1-based) |
| `pageSize` | Integer | Yes (VirtualTag) | 5 | 每頁筆數 |

**Response (200 OK):**

```json
{
  "pageNumber": 1,
  "pageSize": 5,
  "totalPage": 3,
  "totalRow": 15,
  "rows": [
    {
      "tagId": "TAG-001",
      "tagTime": 1691481600000,
      "exerciseIntensity": 0,
      "symptomTypes": {
        "symptomTypes": [1, 3, 5],
        "others": "custom symptom text"
      }
    }
  ]
}
```

**Response Model:**

| Field | Type | Description |
|-------|------|-------------|
| `pageNumber` | Integer | 目前頁碼 |
| `pageSize` | Integer | 每頁筆數 |
| `totalPage` | Integer | 總頁數 |
| `totalRow` | Integer | 總筆數 |
| `rows` | Array | Event Tag 資料陣列 |

**rows[] item (VirtualTagEntity):**

| Field | Type | Description |
|-------|------|-------------|
| `tagId` | String | Tag ID |
| `tagTime` | Long | Tag 時間 (Unix timestamp ms) |
| `exerciseIntensity` | Integer | 運動強度 (see ExerciseIntensity) |
| `symptomTypes` | Object | 症狀資訊 |
| `symptomTypes.symptomTypes` | Array\<Integer\> | 症狀類型 ID 陣列 |
| `symptomTypes.others` | String? | 自訂症狀文字 (optional) |

**ExerciseIntensity enum:**

| Value | Name | Description |
|-------|------|-------------|
| 0 | `Sedentary` | 靜態活動 |
| 1 | `LowIntensityExercise` | 輕度運動 |
| 2 | `ModerateIntensityExercise` | 中強度運動 |
| 3 | `HighIntensityExercise` | 高強度運動 |

**Client-Side Logic:**

遞迴分頁下載，直到所有頁面下載完畢：

```
currentPage = 1
while currentPage <= totalPage:
    fetch page(currentPage)
    parse rows and save to local DB
    currentPage++
```

**Error Response:**

無特殊錯誤碼，HTTP error 時回傳標準錯誤格式。

**Retrofit Example:**

```kotlin
interface RootiApiService {

    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/history")
    suspend fun getEventTagHistory(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String,
        @Query("pageSize") pageSize: Int = 5,
        @Query("pageNumber") pageNumber: Int
    ): Response<EventTagHistoryResponse>
}

data class EventTagHistoryResponse(
    val pageNumber: Int,
    val pageSize: Int,
    val totalPage: Int,
    val totalRow: Int,
    val rows: List<VirtualTagEntity>
)

data class VirtualTagEntity(
    val tagId: String,
    val tagTime: Long,
    val exerciseIntensity: Int,
    val symptomTypes: SymptomTypes?
)

data class SymptomTypes(
    val symptomTypes: List<Int>,
    val others: String?
)

// Usage: recursive pagination
suspend fun fetchAllHistory(institutionId: String, patientId: String, measureId: String) {
    var currentPage = 1
    while (true) {
        val response = apiService.getEventTagHistory(
            institutionId = institutionId,
            patientId = patientId,
            measureId = measureId,
            pageNumber = currentPage
        )
        val body = response.body() ?: break

        // Save rows to local DB
        saveToDatabase(body.rows, measureId, MeasureMode.VIRTUAL_TAG)

        if (currentPage >= body.totalPage) break
        currentPage++
    }
}
```

**Room Database Example:**

```kotlin
// ---- Entity ----
@Entity(tableName = "event_tags")
data class EventTagDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "tag_time") val tagTime: Long,
    @ColumnInfo(name = "tag_local_time") val tagLocalTime: String,
    @ColumnInfo(name = "measure_mode") val measureMode: Int,
    @ColumnInfo(name = "measure_record_id") val measureRecordId: String,
    @ColumnInfo(name = "event_type") val eventType: List<Int>,     // symptomTypes
    @ColumnInfo(name = "others") val others: String?,               // symptomTypes.others
    @ColumnInfo(name = "exercise_intensity") val exerciseIntensity: Int,
    @ColumnInfo(name = "is_read") val isRead: Boolean = true,
    @ColumnInfo(name = "is_edit") val isEdit: Boolean = false
)

// ---- TypeConverter for List<Int> ----
class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? =
        value?.joinToString(",")

    @TypeConverter
    fun toIntList(value: String?): List<Int>? =
        value?.split(",")?.mapNotNull { it.toIntOrNull() }
}

// ---- DAO ----
@Dao
interface EventTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<EventTagDbEntity>)

    @Query("SELECT * FROM event_tags WHERE measure_mode = :measureMode ORDER BY tag_time DESC")
    suspend fun getAllByMeasureMode(measureMode: Int): List<EventTagDbEntity>

    @Query("SELECT COUNT(*) FROM event_tags")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM event_tags")
    suspend fun clearAll()
}

// ---- Database ----
@Database(entities = [EventTagDbEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventTagDao(): EventTagDao
}

// ---- Mapping: API VirtualTagEntity → DB EventTagDbEntity ----
fun VirtualTagEntity.toDbEntity(measureId: String, measureMode: Int): EventTagDbEntity {
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault())
    return EventTagDbEntity(
        id = tagId,
        tagTime = tagTime,
        tagLocalTime = formatter.format(Date(tagTime)),
        measureMode = measureMode,
        measureRecordId = measureId,
        eventType = symptomTypes?.symptomTypes ?: emptyList(),
        others = symptomTypes?.others,
        exerciseIntensity = exerciseIntensity,
        isRead = true,
        isEdit = false
    )
}

// ---- Usage: Save API response to Room ----
suspend fun saveToDatabase(
    rows: List<VirtualTagEntity>,
    measureId: String,
    measureMode: Int
) {
    val dbEntities = rows.mapNotNull { tag ->
        try {
            tag.toDbEntity(measureId, measureMode)
        } catch (e: Exception) {
            Log.e("DB", "parseVirtualEventTag error: ${e.message}")
            null
        }
    }
    database.eventTagDao().insertAll(dbEntities)
}
```

---

### 6. Unsubscribe Patient

取消訂閱病患，用於登出流程。呼叫後無論成功或失敗，Client 端皆應清除本地資料並登出。

| Item | Value |
|------|-------|
| **Method** | `POST` |
| **Path** | `/oauth/vendors/{institutionId}/patients/{patientId}/unsubscribe` |
| **Authentication** | Bearer Token |
| **Request Body** | None |

**Request Headers:**

```
Content-Type: application/json
Authorization: Bearer {token}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `institutionId` | String | Yes | Institution/vendor identifier |
| `patientId` | String | Yes | Patient identifier |

**Response (200 OK):**

```json
{
  "status": "200",
  "message": "Patient unsubscribed successfully",
  "unsubscribeTime": "{unsubscribeTime}"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `status` | String | HTTP status code |
| `message` | String | 成功訊息 |
| `unsubscribeTime` | String | 取消訂閱時間 |

**Error Responses:**

**400 - institutionId 不存在：**

```json
{
  "error": "invalid_institution_id",
  "error_description": "institution: [ {institutionId} ] is invalid."
}
```

**400 - patientId 不存在：**

```json
{
  "error": "invalid_patient",
  "error_description": "patient: [ {patientId} ] is invalid."
}
```

**Client-Side Logic:**

無論成功或失敗，皆執行以下步驟：

```
1. 清除本地資料庫所有 Event Tag 紀錄
2. 清除 measurementId
3. 設定登入狀態為 false
4. 導航至登入頁面
```

**Retrofit Example:**

```kotlin
interface RootiApiService {

    @POST("/oauth/vendors/{institutionId}/patients/{patientId}/unsubscribe")
    suspend fun unsubscribePatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<UnsubscribeResponse>
}

data class UnsubscribeResponse(
    val status: String,
    val message: String,
    val unsubscribeTime: String
)

// Usage: Logout flow
suspend fun logout() {
    try {
        apiService.unsubscribePatient(institutionId, patientId)
    } catch (e: Exception) {
        Log.e("Logout", "Unsubscribe failed: ${e.message}")
    } finally {
        // Always clear local data regardless of API result
        database.eventTagDao().clearAll()
        preferences.clearMeasurementId()
        preferences.setLoggedIn(false)
        navigateToLogin()
    }
}
```

---

### 7. Add Virtual Event Tags

上傳 Virtual Event Tag 紀錄至伺服器。

| Item | Value |
|------|-------|
| **Method** | `POST` |
| **Path** | `/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags` |
| **Authentication** | Bearer Token |

**Request Headers:**

```
Content-Type: application/json
Authorization: Bearer {token}
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `institutionId` | String | Yes | Institution/vendor identifier |
| `patientId` | String | Yes | Patient identifier |
| `measureId` | String | Yes | Measurement record ID |

**Request Body:**

```json
{
  "deviceUUID": "DEVICE-UUID-001",
  "appVersion": "1.0.0",
  "appType": 1,
  "tags": [
    {
      "tagTime": 1691481600000,
      "exerciseIntensity": 0,
      "symptomTypes": {
        "symptomTypes": [1, 3, 5],
        "others": "custom symptom text"
      }
    }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `deviceUUID` | String | Yes | Device UUID |
| `appVersion` | String | Yes | App version |
| `appType` | Integer | Yes | `0` = iOS, `1` = Android |
| `tags` | Array | Yes | Tag 資料陣列 |

**tags[] item:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `tagTime` | Long | Yes | Tag 時間 (Unix timestamp ms) |
| `exerciseIntensity` | Integer | Yes | 運動強度 (see ExerciseIntensity in API #5) |
| `symptomTypes` | Object | Yes | 症狀資訊 |
| `symptomTypes.symptomTypes` | Array\<Integer\> | Yes | 症狀類型 ID 陣列 |
| `symptomTypes.others` | String? | No | 自訂症狀文字 |

**Response (200 OK):**

```json
{
  "institutionId": "rootilabs",
  "patientId": "VT01",
  "measureRecordId": "56d0aecc-d93a-416d-8408-3b7cc058047e",
  "addedSize": 2,
  "updatedSize": 0,
  "failedSize": 0,
  "failedAdding": []
}
```

| Field | Type | Description |
|-------|------|-------------|
| `institutionId` | String | Institution ID |
| `patientId` | String | Patient ID |
| `measureRecordId` | String | Measurement record ID |
| `addedSize` | Integer | 成功新增筆數 |
| `updatedSize` | Integer | 成功更新筆數 |
| `failedSize` | Integer | 失敗筆數 |
| `failedAdding` | Array | 失敗的 tag 詳情 (optional) |

**failedAdding[] item:**

| Field | Type | Description |
|-------|------|-------------|
| `tagTime` | Long | 失敗的 tag 時間 |
| `exerciseIntensity` | Integer | 運動強度 |
| `symptomTypes` | Array\<Integer\> | 症狀類型 ID 陣列 |

**Error Response:**

HTTP error 時嘗試解析 response body，傳入 error callback：

```json
{
  "error": "{error_code}",
  "error_description": "{description}"
}
```

**Client-Side Logic:**

上傳成功後依序執行：

```
1. 解析 response 為 VirtualTagAddResponse
2. 檢查 failedSize：
   - if failedSize > 0 AND (addedSize + updatedSize) != 預期上傳數量（tags.count） → 視為錯誤
3. 若無錯誤：
   - 更新本地 DB：將 tag 的 isEdit 設為 false（標記已上傳）
   - 發送 UI 通知刷新列表
```

**Retrofit Example:**

```kotlin
interface RootiApiService {

    @POST("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags")
    suspend fun addVirtualEventTags(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String,
        @Body body: AddVirtualTagsRequest
    ): Response<AddVirtualTagsResponse>
}

// ---- Request Models ----
data class AddVirtualTagsRequest(
    val deviceUUID: String,
    val appVersion: String,
    val appType: Int = 1, // Android = 1
    val tags: List<VirtualTagRequest>
)

data class VirtualTagRequest(
    val tagTime: Long,
    val exerciseIntensity: Int,
    val symptomTypes: SymptomTypes
)

// SymptomTypes already defined in API #5

// ---- Response Models ----
data class AddVirtualTagsResponse(
    val institutionId: String,
    val patientId: String,
    val measureRecordId: String,
    val addedSize: Int,
    val updatedSize: Int,
    val failedSize: Int,
    val failedAdding: List<FailedTag>?
)

data class FailedTag(
    val tagTime: Long,
    val exerciseIntensity: Int,
    val symptomTypes: List<Int>
)

// ---- Mapping: DB Entity → API Request ----
fun EventTagDbEntity.toVirtualTagRequest(): VirtualTagRequest {
    return VirtualTagRequest(
        tagTime = tagTime,
        exerciseIntensity = exerciseIntensity,
        symptomTypes = SymptomTypes(
            symptomTypes = eventType,
            others = others
        )
    )
}

// ---- Usage: Upload tags ----
suspend fun uploadVirtualEventTags(tags: List<EventTagDbEntity>) {
    val request = AddVirtualTagsRequest(
        deviceUUID = getDeviceUUID(),
        appVersion = getAppVersion(),
        appType = 1, // Android
        tags = tags.map { it.toVirtualTagRequest() }
    )

    val result = apiService.addVirtualEventTags(
        institutionId = institutionId,
        patientId = patientId,
        measureId = measureId,
        body = request
    )

    if (result.isSuccessful) {
        val response = result.body()!!

        // Check for partial failure
        if (response.failedSize > 0 &&
            (response.addedSize + response.updatedSize) != tags.size) {
            Log.e("Upload", "Partial failure: ${response.failedSize} tags failed")
            throw UploadException("Upload partially failed")
        }

        // Mark as uploaded in local DB
        val updatedTags = tags.map { it.copy(isEdit = false) }
        database.eventTagDao().insertAll(updatedTags)
    } else {
        val errorBody = result.errorBody()?.string()
        val apiError = Gson().fromJson(errorBody, ApiError::class.java)
        Log.e("Upload", "Upload failed: ${apiError?.errorDescription}")
        throw UploadException(apiError?.errorDescription ?: "Unknown error")
    }
}
```
