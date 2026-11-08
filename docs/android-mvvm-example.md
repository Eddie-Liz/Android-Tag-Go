# Tag&Go Android MVVM Architecture Example

Reference: `docs/api.md`

---

## MVVM 架構概述

### 什麼是 MVVM？

MVVM（Model-View-ViewModel）是 Android 官方推薦的架構模式，將應用程式分為三個核心層級，各自職責明確、互不越權。

```
┌─────────────────────────────────────────────────────┐
│                    View (Activity)                   │
│  職責：顯示 UI、接收使用者操作、觀察狀態變化           │
│  規則：不處理任何業務邏輯，不直接呼叫 API              │
│                                                      │
│  觀察 LiveData          觸發操作                      │
│       ▲                     │                        │
│       │                     ▼                        │
│  ┌──────────────────────────────────────────────┐    │
│  │              ViewModel                        │    │
│  │  職責：管理 UI 狀態、協調 View 與 Repository    │    │
│  │  規則：不持有 Activity 引用，不操作 UI 元件      │    │
│  │                                               │    │
│  │  暴露 LiveData        呼叫 Repository           │    │
│  │       ▲                     │                  │    │
│  │       │                     ▼                  │    │
│  │  ┌──────────────────────────────────────┐     │    │
│  │  │          Repository                   │     │    │
│  │  │  職責：資料存取的單一入口               │     │    │
│  │  │  整合 API 呼叫 + 本地資料庫操作         │     │    │
│  │  │  規則：不知道 UI 的存在                │     │    │
│  │  │                                      │     │    │
│  │  │       ┌──────────┐  ┌──────────┐     │     │    │
│  │  │       │ Retrofit │  │   Room   │     │     │    │
│  │  │       │ (API)    │  │   (DB)   │     │     │    │
│  │  │       └──────────┘  └──────────┘     │     │    │
│  │  └──────────────────────────────────────┘     │    │
│  └──────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

### 各模組職責說明

#### View（Activity / Fragment）

| 項目 | 說明 |
|------|------|
| **職責** | 顯示 UI、接收使用者操作（點擊、輸入）、觀察狀態變化並更新畫面 |
| **可以做** | `observe` LiveData、顯示/隱藏 loading、顯示錯誤對話框、畫面導航 |
| **不可以做** | 呼叫 API、存取資料庫、處理業務邏輯、印 log |
| **生命週期** | 隨 Activity 生命週期建立/銷毀，螢幕旋轉會重建 |

```kotlin
// View 的工作就是：觀察 → 根據狀態更新 UI
viewModel.authState.observe(this) { result ->
    when (result) {
        is ApiResult.Loading -> showLoading()
        is ApiResult.Success -> navigateToMain()
        is ApiResult.Error -> showError(result.message)
        is ApiResult.NetworkError -> showError("網路錯誤")
    }
}

// 使用者操作 → 委派給 ViewModel
btnLogin.setOnClickListener {
    viewModel.authPatient(institutionId, patientId)
}
```

#### ViewModel

| 項目 | 說明 |
|------|------|
| **職責** | 管理 UI 狀態、協調 View 與 Repository、處理 UI 相關的業務邏輯 |
| **可以做** | 持有 LiveData、呼叫 Repository、啟動 coroutine、組合多個 API 呼叫的流程 |
| **不可以做** | 持有 Activity/Context 引用、直接操作 UI 元件（TextView、Button 等） |
| **生命週期** | 比 Activity 長壽，螢幕旋轉時不會被銷毀，資料得以保留 |

```kotlin
// ViewModel 的工作就是：接收指令 → 呼叫 Repository → 更新狀態
fun authPatient(institutionId: String, patientId: String) {
    _authState.value = ApiResult.Loading          // 1. 通知 UI 開始載入
    viewModelScope.launch {
        _authState.value = repository.authPatient(  // 2. 呼叫 Repository
            institutionId, patientId                 // 3. 結果自動通知 UI
        )
    }
}
```

**為什麼 ViewModel 不印 log？**
因為 ViewModel 只是狀態的中轉站，它不知道 API 的 raw response 長什麼樣。Log 應該在產生資料的地方（Repository）印出，而非中轉的地方。

#### Repository

| 項目 | 說明 |
|------|------|
| **職責** | 資料存取的**單一入口**，封裝所有資料來源（API + 本地 DB），提供乾淨的資料給 ViewModel |
| **可以做** | 呼叫 Retrofit API、操作 Room DB、解析錯誤、印 log、整合多個資料來源 |
| **不可以做** | 持有 UI 引用、直接更新畫面、知道目前是哪個 Activity 在用 |
| **生命週期** | 通常是單例（singleton），App 存活期間一直存在 |

```kotlin
// Repository 的工作就是：呼叫 API → 解析結果 → 印 log → 回傳統一格式
suspend fun authPatient(institutionId: String, patientId: String): ApiResult<AuthPatientResponse> {
    Log.d(TAG, "-> authPatient($institutionId, $patientId)")      // 印請求 log
    return try {
        val response = apiService.authPatient(institutionId, patientId)
        if (response.isSuccessful) {
            Log.d(TAG, "<- authPatient OK")                        // 印成功 log
            ApiResult.Success(response.body()!!)
        } else {
            val error = parseError(response)
            Log.e(TAG, "<- authPatient [${response.code()}]: ${error?.errorDescription}")  // 印錯誤 log
            ApiResult.Error(response.code(), error?.error ?: "", error?.errorDescription ?: "")
        }
    } catch (e: Exception) {
        Log.e(TAG, "<- authPatient exception", e)                  // 印例外 log
        ApiResult.NetworkError(e)
    }
}
```

#### Model（Data Layer）

| 項目 | 說明 |
|------|------|
| **職責** | 定義資料結構（API response、DB entity、request body） |
| **包含** | data class、enum、Entity、TypeConverter |
| **規則** | 純資料物件，不包含業務邏輯（除了簡單的判斷方法如 `isMeasuring()`） |

### 資料流向

**使用者操作 → API 回應的完整流程：**

```
使用者點擊「登入」
    │
    ▼
Activity: btnLogin.onClick { viewModel.authPatient(id, pwd) }
    │
    ▼
ViewModel: _authState.value = Loading
           repository.authPatient(id, pwd)
    │
    ▼
Repository: Log.d("-> authPatient")
            apiService.authPatient(id, pwd)   ← Retrofit 發出 HTTP 請求
    │
    ▼
Server 回應 200 / 400 / 409
    │
    ▼
Repository: 解析 response
            Log.d("<- authPatient OK") 或 Log.e("<- authPatient [400]: ...")
            return ApiResult.Success(data) 或 ApiResult.Error(...)
    │
    ▼
ViewModel: _authState.value = result   ← LiveData 自動通知觀察者
    │
    ▼
Activity: observe 收到新值
          Success → navigateToMain()
          Error   → showError(message)
          Loading → showProgressBar()
```

### 為什麼用這個架構？

| 問題 | 沒有 MVVM | 有 MVVM |
|------|-----------|---------|
| 螢幕旋轉 | API 呼叫中斷，資料遺失 | ViewModel 存活，資料保留 |
| Activity 太肥 | 500+ 行混雜 UI、API、DB 邏輯 | 各層 100-200 行，職責清晰 |
| 測試困難 | 要模擬整個 Activity 才能測 API | Repository 可獨立單元測試 |
| 重複代碼 | 每個 Activity 各寫一套 API 呼叫 | Repository 共用，多個 ViewModel 可複用 |
| Debug 困難 | Log 散落各處 | Log 集中在 Repository + Interceptor |

---

## LiveData 完整說明

### 什麼是 LiveData？

LiveData 是 Android 提供的**可觀察資料容器**，核心特性：

| 特性 | 說明 |
|------|------|
| **生命週期感知** | 只在 Activity/Fragment 處於 `STARTED` 或 `RESUMED` 時才通知觀察者，避免在背景時更新 UI 導致 crash |
| **自動取消訂閱** | Activity 銷毀時自動移除觀察者，不會 memory leak |
| **資料保留** | 螢幕旋轉後，重建的 Activity 會立刻收到最新一筆資料 |
| **確保 UI 一致** | 永遠在主執行緒通知觀察者，可安全更新 UI |

### 運作原理

```
ViewModel 裡：
┌──────────────────────────────────────────────┐
│  MutableLiveData (可寫入)                      │
│                                               │
│  _authState.value = ApiResult.Loading    ←寫入 │
│  _authState.value = ApiResult.Success(data)   │
│                                               │
│  對外暴露為 LiveData (唯讀)                     │
│  val authState: LiveData<...> = _authState     │
└──────────────────────────────────────────────┘
         │
         │ 值改變時自動通知
         ▼
Activity 裡：
┌──────────────────────────────────────────────┐
│  viewModel.authState.observe(this) { result → │
│      // 這裡的 code 會在值改變時自動執行          │
│      // 「this」= LifecycleOwner (Activity)    │
│      // Activity 銷毀後自動停止通知              │
│  }                                            │
└──────────────────────────────────────────────┘
```

### LiveData 基本用法

```kotlin
// ============ ViewModel ============

class LoginViewModel : ViewModel() {

    // 1. 建立 MutableLiveData（私有，只有 ViewModel 能寫入）
    private val _authState = MutableLiveData<ApiResult<AuthPatientResponse>>()

    // 2. 對外暴露為 LiveData（唯讀，Activity 只能觀察）
    val authState: LiveData<ApiResult<AuthPatientResponse>> = _authState

    fun authPatient(instId: String, patId: String) {
        // 3. 寫入值 → 所有觀察者自動收到通知
        _authState.value = ApiResult.Loading

        viewModelScope.launch {
            val result = repository.authPatient(instId, patId)
            _authState.value = result   // 再次寫入 → 觀察者再次收到通知
        }
    }
}

// ============ Activity ============

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 4. 觀察 LiveData — 每次值改變都會執行這個 lambda
        viewModel.authState.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> showLoading()
                is ApiResult.Success -> navigateToMain()
                is ApiResult.Error -> showError(result.message)
                is ApiResult.NetworkError -> showError("網路錯誤")
            }
        }
    }
}
```

### MutableLiveData vs LiveData

```kotlin
// MutableLiveData: 可讀可寫（只在 ViewModel 內部使用）
private val _state = MutableLiveData<String>()

// LiveData: 只可讀（暴露給 Activity）
val state: LiveData<String> = _state

// ViewModel 內部：可以寫入
_state.value = "Hello"

// Activity 裡：只能觀察，不能寫入
viewModel.state.observe(this) { value -> /* 收到 "Hello" */ }
// viewModel.state.value = "World"  ← 編譯錯誤！LiveData 不能寫入
```

### LiveData 生命週期行為

```
Activity 狀態         LiveData 行為
─────────────────     ──────────────────────────
onCreate              observe() 註冊觀察者
onStart               ✓ 開始接收通知
onResume              ✓ 持續接收通知
                      ← ViewModel 更新值，Activity 立刻收到
onPause               ✓ 仍然接收通知
onStop                ✗ 停止接收通知（避免更新不可見的 UI）
onDestroy             自動移除觀察者（不會 memory leak）

螢幕旋轉：
onDestroy → onCreate  LiveData 會把最新一筆值重新發送給新的觀察者
                      ViewModel 不會被銷毀，資料完整保留
```

---

## 判斷邏輯應該寫在哪？

### 原則

| 邏輯類型 | 放在哪 | 原因 |
|----------|--------|------|
| **資料判斷**（API response 是否合法） | Repository | 貼近資料來源，可重複使用 |
| **業務流程判斷**（是否錄製中 → 下一步做什麼） | ViewModel | 協調多步驟流程是 ViewModel 的職責 |
| **UI 判斷**（顯示哪個錯誤文字、哪個畫面） | Activity | 純 UI 表現層 |
| **資料模型自身判斷**（isMeasuring） | Model (data class) | 與資料本身相關的簡單運算 |

### 實際範例：判斷是否錄製中

根據 `docs/api.md` 的 API #3，登入流程需要：
1. 呼叫 `getCurrentMeasurement` 取得量測資訊
2. 判斷是否錄製中（state == 0 且未過期）
3. 判斷錄製模式是否為 VirtualTag
4. 不符合條件 → 顯示錯誤並登出

**各層的分工：**

#### Model — 定義判斷方法（純運算，不含副作用）

```kotlin
data class MeasurementInfo(
    val measureRecordOid: Long,
    val measureRecordId: String,
    val mode: Int,
    val state: Int,
    val launchTime: Long,
    val expectedEndTime: Long,
    val deviceId: String
) {
    // 資料模型自己知道「什麼算錄製中」
    fun isMeasuring(): Boolean {
        return state == STATE_MEASURING
            && expectedEndTime != 0L
            && System.currentTimeMillis() < expectedEndTime
    }

    // 資料模型自己知道「什麼算 VirtualTag 模式」
    fun isVirtualTagMode(): Boolean = mode == MODE_VIRTUAL_TAG

    companion object {
        const val STATE_MEASURING = 0
        const val MODE_VIRTUAL_TAG = 0
    }
}
```

#### Repository — 呼叫 API、印 log、回傳結果（不做流程判斷）

```kotlin
// Repository 只管「拿到資料」，不管「拿到資料之後要幹嘛」
suspend fun getCurrentMeasurement(
    institutionId: String,
    patientId: String
): ApiResult<MeasurementInfo> {
    Log.d(TAG, "-> getCurrentMeasurement")
    return try {
        val response = apiService.getCurrentMeasurementInfo(institutionId, patientId)
        if (response.isSuccessful) {
            val body = response.body()!!
            Log.d(TAG, "<- getCurrentMeasurement OK: mode=${body.mode}, state=${body.state}")
            ApiResult.Success(body)
        } else {
            val error = parseError(response)
            Log.e(TAG, "<- getCurrentMeasurement [${response.code()}]: ${error?.errorDescription}")
            ApiResult.Error(response.code(), error?.error ?: "", error?.errorDescription ?: "")
        }
    } catch (e: Exception) {
        Log.e(TAG, "<- getCurrentMeasurement exception", e)
        ApiResult.NetworkError(e)
    }
}
```

#### ViewModel — 業務流程判斷（核心邏輯在這裡）

```kotlin
class LoginViewModel(private val repository: PatientRepository) : ViewModel() {

    // UI 狀態：用 sealed class 表達所有可能的登入結果
    sealed class LoginUiState {
        object Idle : LoginUiState()
        data class Loading(val step: String) : LoginUiState()
        data class Success(val measureId: String) : LoginUiState()
        data class Error(val message: String, val shouldLogout: Boolean) : LoginUiState()
    }

    private val _loginState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val loginState: LiveData<LoginUiState> = _loginState

    fun startLoginFlow(institutionId: String, patientId: String) {
        viewModelScope.launch {
            // Step 1: Auth Patient
            _loginState.value = LoginUiState.Loading("驗證病患中...")
            val authResult = repository.authPatient(institutionId, patientId)
            if (authResult !is ApiResult.Success) {
                _loginState.value = handleApiError(authResult, shouldLogout = false)
                return@launch
            }

            // Step 2: Get Current Measurement
            _loginState.value = LoginUiState.Loading("檢查量測狀態...")
            val measureResult = repository.getCurrentMeasurement(institutionId, patientId)
            if (measureResult !is ApiResult.Success) {
                _loginState.value = handleApiError(measureResult, shouldLogout = true)
                return@launch
            }

            // ★★★ 判斷邏輯寫在 ViewModel ★★★
            val info = measureResult.data

            // 判斷 1：是否錄製中
            if (!info.isMeasuring()) {
                _loginState.value = LoginUiState.Error(
                    message = "目前非錄製中狀態，無法使用",
                    shouldLogout = true
                )
                return@launch
            }

            // 判斷 2：是否為 VirtualTag 模式
            if (!info.isVirtualTagMode()) {
                _loginState.value = LoginUiState.Error(
                    message = "不支援的錄製模式 (mode=${info.mode})",
                    shouldLogout = true
                )
                return@launch
            }

            // Step 3: Fetch History
            _loginState.value = LoginUiState.Loading("下載歷史紀錄...")
            val historyResult = repository.fetchAllHistory(
                institutionId, patientId, info.measureRecordId
            )
            if (historyResult !is ApiResult.Success) {
                _loginState.value = handleApiError(historyResult, shouldLogout = false)
                return@launch
            }

            // 全部通過 → 登入成功
            _loginState.value = LoginUiState.Success(info.measureRecordId)
        }
    }

    private fun handleApiError(result: ApiResult<*>, shouldLogout: Boolean): LoginUiState.Error {
        val message = when (result) {
            is ApiResult.Error -> result.message
            is ApiResult.NetworkError -> "網路連線失敗: ${result.exception.message}"
            else -> "未知錯誤"
        }
        return LoginUiState.Error(message = message, shouldLogout = shouldLogout)
    }
}
```

#### Activity — 純 UI 反應（只根據狀態更新畫面）

```kotlin
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Activity 只做一件事：觀察狀態 → 更新 UI
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginUiState.Idle -> {
                    progressBar.isVisible = false
                    btnLogin.isEnabled = true
                }
                is LoginViewModel.LoginUiState.Loading -> {
                    progressBar.isVisible = true
                    tvStatus.text = state.step        // 顯示「驗證病患中...」等
                    btnLogin.isEnabled = false
                }
                is LoginViewModel.LoginUiState.Success -> {
                    progressBar.isVisible = false
                    navigateToMain(state.measureId)
                }
                is LoginViewModel.LoginUiState.Error -> {
                    progressBar.isVisible = false
                    btnLogin.isEnabled = true

                    // Activity 只負責「顯示」錯誤，不負責「判斷」是什麼錯誤
                    showErrorDialog(state.message)

                    // ViewModel 告訴 Activity 是否需要登出
                    if (state.shouldLogout) {
                        clearLocalDataAndReset()
                    }
                }
            }
        }

        btnLogin.setOnClickListener {
            val instId = etInstitutionId.text.toString()
            val patId = etPatientId.text.toString()
            viewModel.startLoginFlow(instId, patId)
        }
    }
}
```

### 判斷邏輯分層總覽

```
「是否錄製中？」這個判斷涉及三層：

Model (MeasurementInfo):
    fun isMeasuring(): Boolean
    → 定義「什麼條件算錄製中」（純計算）
    → state == 0 && expectedEndTime != 0 && now < expectedEndTime

ViewModel (LoginViewModel):
    if (!info.isMeasuring()) { ... }
    → 決定「不在錄製中的話要做什麼」（業務流程）
    → 設定 Error 狀態 + shouldLogout = true

Activity (LoginActivity):
    is LoginUiState.Error -> showErrorDialog(state.message)
    → 決定「錯誤要怎麼顯示」（UI 表現）
    → 彈出對話框、如果需要登出就清資料
```

### 常見錯誤：把判斷寫在 Activity

```kotlin
// ✗ 錯誤做法：Activity 裡做業務判斷
viewModel.measureState.observe(this) { result ->
    if (result is ApiResult.Success) {
        val info = result.data
        if (!info.isMeasuring()) {           // ← 業務邏輯跑進 Activity 了
            showError("非錄製中")
            logout()
        } else if (!info.isVirtualTagMode()) { // ← 越來越肥
            showError("不支援的模式")
            logout()
        } else {
            viewModel.fetchHistory(info.measureRecordId)  // ← Activity 在控制流程
        }
    }
}

// ✓ 正確做法：Activity 只觀察最終狀態
viewModel.loginState.observe(this) { state ->
    when (state) {
        is Loading -> showLoading(state.step)
        is Success -> navigateToMain(state.measureId)
        is Error -> showError(state.message)     // ← 簡潔，不管為什麼錯
    }
}
```

---

## Project Structure

```
app/src/main/java/com/rooti/tagandgo/
├── data/
│   ├── api/
│   │   ├── RootiApiService.kt          # Retrofit interface
│   │   ├── RootiAuthService.kt         # Auth token API
│   │   ├── AuthInterceptor.kt          # Bearer token interceptor
│   │   └── ApiLoggingInterceptor.kt    # HTTP logging interceptor
│   ├── model/
│   │   ├── ApiResult.kt                # Sealed class for API state
│   │   ├── ApiError.kt                 # Error response model
│   │   ├── AuthPatientResponse.kt
│   │   ├── MeasurementInfo.kt
│   │   ├── EventTagHistoryResponse.kt
│   │   ├── AddVirtualTagsRequest.kt
│   │   ├── AddVirtualTagsResponse.kt
│   │   └── UnsubscribeResponse.kt
│   ├── db/
│   │   ├── AppDatabase.kt
│   │   ├── EventTagDao.kt
│   │   ├── EventTagDbEntity.kt
│   │   └── Converters.kt
│   └── repository/
│       └── PatientRepository.kt        # API + DB operations
├── ui/
│   ├── login/
│   │   ├── LoginActivity.kt
│   │   └── LoginViewModel.kt
│   └── main/
│       ├── MainActivity.kt
│       └── MainViewModel.kt
└── di/
    └── NetworkModule.kt                # OkHttp + Retrofit setup
```

---

## 1. Network Layer

### NetworkModule.kt

```kotlin
object NetworkModule {

    private const val BASE_URL = "https://mct-api.rooticare.com"

    private var accessToken: String = ""

    fun setToken(token: String) {
        accessToken = token
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { accessToken })
            .addInterceptor(ApiLoggingInterceptor())
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val authService: RootiAuthService by lazy {
        retrofit.create(RootiAuthService::class.java)
    }

    val apiService: RootiApiService by lazy {
        retrofit.create(RootiApiService::class.java)
    }
}
```

### AuthInterceptor.kt

```kotlin
class AuthInterceptor(private val tokenProvider: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = if (token.isNotEmpty()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
```

### ApiLoggingInterceptor.kt

```kotlin
class ApiLoggingInterceptor : Interceptor {

    companion object {
        private const val TAG = "HTTP"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "-> ${request.method} ${request.url}")

        val response = chain.proceed(request)
        val duration = System.currentTimeMillis() - startTime

        if (response.isSuccessful) {
            Log.d(TAG, "<- ${response.code} ${request.url} (${duration}ms)")
        } else {
            val body = response.peekBody(Long.MAX_VALUE).string()
            Log.e(TAG, "<- ${response.code} ${request.url} (${duration}ms)\n$body")
        }

        return response
    }
}
```

---

## 2. Data Models

### ApiResult.kt

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: Int, val error: String, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val exception: Throwable) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}
```

### ApiError.kt

```kotlin
data class ApiError(
    val error: String,
    @SerializedName("error_description")
    val errorDescription: String
)
```

### AuthPatientResponse.kt

```kotlin
data class AuthPatientResponse(
    val vendorName: String,
    val subscribedBefore: String,
    val subscribeTime: String
)
```

### MeasurementInfo.kt

```kotlin
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
        return state == STATE_MEASURING
            && expectedEndTime != 0L
            && System.currentTimeMillis() < expectedEndTime
    }

    fun isVirtualTagMode(): Boolean = mode == MODE_VIRTUAL_TAG

    companion object {
        const val STATE_MEASURING = 0
        const val MODE_VIRTUAL_TAG = 0
        const val MODE_MONITOR = 1
        const val MODE_MCT_TAG = 2
    }
}
```

### EventTagHistoryResponse.kt

```kotlin
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
```

### AddVirtualTagsRequest.kt

```kotlin
data class AddVirtualTagsRequest(
    val deviceUUID: String,
    val appVersion: String,
    val appType: Int = 1,
    val tags: List<VirtualTagRequest>
)

data class VirtualTagRequest(
    val tagTime: Long,
    val exerciseIntensity: Int,
    val symptomTypes: SymptomTypes
)
```

### AddVirtualTagsResponse.kt

```kotlin
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
```

### UnsubscribeResponse.kt

```kotlin
data class UnsubscribeResponse(
    val status: String,
    val message: String,
    val unsubscribeTime: String
)
```

---

## 3. Retrofit API Interfaces

### RootiAuthService.kt

```kotlin
interface RootiAuthService {

    @POST("/oauth/token")
    suspend fun getToken(
        @Header("Authorization") basicAuth: String,
        @Body body: Map<String, String>
    ): Response<JsonObject>
}
```

### RootiApiService.kt

```kotlin
interface RootiApiService {

    // API #2 - Auth Patient
    @GET("/oauth/vendors/{institutionId}/patients/{patientId}")
    suspend fun authPatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<AuthPatientResponse>

    // API #3 - Get Current Measurement Info
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/currentMeasurement")
    suspend fun getCurrentMeasurementInfo(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<MeasurementInfo>

    // API #4 - Get Total History Count
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/totalHistory")
    suspend fun getTotalHistoryCount(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String
    ): Response<TotalHistoryResponse>

    // API #5 - Get Event Tag History (Paginated)
    @GET("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags/history")
    suspend fun getEventTagHistory(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String,
        @Query("pageSize") pageSize: Int = 5,
        @Query("pageNumber") pageNumber: Int
    ): Response<EventTagHistoryResponse>

    // API #6 - Unsubscribe Patient
    @POST("/oauth/vendors/{institutionId}/patients/{patientId}/unsubscribe")
    suspend fun unsubscribePatient(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String
    ): Response<UnsubscribeResponse>

    // API #7 - Add Virtual Event Tags
    @POST("/api/v1/institutions/{institutionId}/patients/{patientId}/measures/{measureId}/virtualTags")
    suspend fun addVirtualEventTags(
        @Path("institutionId") institutionId: String,
        @Path("patientId") patientId: String,
        @Path("measureId") measureId: String,
        @Body body: AddVirtualTagsRequest
    ): Response<AddVirtualTagsResponse>
}

data class TotalHistoryResponse(
    val totalRow: Int
)
```

---

## 4. Database Layer

### EventTagDbEntity.kt

```kotlin
@Entity(tableName = "event_tags")
data class EventTagDbEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "tag_time") val tagTime: Long,
    @ColumnInfo(name = "tag_local_time") val tagLocalTime: String,
    @ColumnInfo(name = "measure_mode") val measureMode: Int,
    @ColumnInfo(name = "measure_record_id") val measureRecordId: String,
    @ColumnInfo(name = "event_type") val eventType: List<Int>,
    @ColumnInfo(name = "others") val others: String?,
    @ColumnInfo(name = "exercise_intensity") val exerciseIntensity: Int,
    @ColumnInfo(name = "is_read") val isRead: Boolean = true,
    @ColumnInfo(name = "is_edit") val isEdit: Boolean = false
)
```

### Converters.kt

```kotlin
class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? =
        value?.joinToString(",")

    @TypeConverter
    fun toIntList(value: String?): List<Int>? =
        value?.split(",")?.mapNotNull { it.toIntOrNull() }
}
```

### EventTagDao.kt

```kotlin
@Dao
interface EventTagDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<EventTagDbEntity>)

    @Query("SELECT * FROM event_tags WHERE measure_mode = :measureMode ORDER BY tag_time DESC")
    suspend fun getAllByMeasureMode(measureMode: Int): List<EventTagDbEntity>

    @Query("SELECT * FROM event_tags WHERE is_edit = 1")
    suspend fun getPendingUpload(): List<EventTagDbEntity>

    @Query("SELECT COUNT(*) FROM event_tags")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM event_tags")
    suspend fun clearAll()
}
```

### AppDatabase.kt

```kotlin
@Database(entities = [EventTagDbEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventTagDao(): EventTagDao
}
```

### Mapping Extensions

```kotlin
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
```

---

## 5. Repository Layer (with Logging)

### PatientRepository.kt

```kotlin
class PatientRepository(
    private val authService: RootiAuthService,
    private val apiService: RootiApiService,
    private val eventTagDao: EventTagDao
) {
    companion object {
        private const val TAG = "PatientRepo"
        private const val BASIC_AUTH = Constants.BASIC_AUTH
    }

    // ---- Helper: parse error body ----
    private fun parseError(response: Response<*>): ApiError? {
        return try {
            val raw = response.errorBody()?.string()
            Gson().fromJson(raw, ApiError::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // ---- API #1: Get Token ----
    suspend fun getToken(): ApiResult<String> {
        Log.d(TAG, "-> getToken()")
        return try {
            val response = authService.getToken(
                basicAuth = BASIC_AUTH,
                body = mapOf("grant_type" to "client_credentials")
            )
            if (response.isSuccessful) {
                val token = response.body()?.get("access_token")?.asString
                if (token != null) {
                    Log.d(TAG, "<- getToken OK")
                    ApiResult.Success(token)
                } else {
                    Log.e(TAG, "<- getToken failed: access_token is null")
                    ApiResult.Error(200, "null_token", "access_token is null")
                }
            } else {
                val error = parseError(response)
                Log.e(TAG, "<- getToken [${response.code()}]: ${error?.errorDescription}")
                ApiResult.Error(response.code(), error?.error ?: "", error?.errorDescription ?: "Token request failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "<- getToken exception", e)
            ApiResult.NetworkError(e)
        }
    }

    // ---- API #2: Auth Patient ----
    suspend fun authPatient(institutionId: String, patientId: String): ApiResult<AuthPatientResponse> {
        Log.d(TAG, "-> authPatient($institutionId, $patientId)")
        return try {
            val response = apiService.authPatient(institutionId, patientId)
            if (response.isSuccessful) {
                val body = response.body()!!
                Log.d(TAG, "<- authPatient OK: vendorName=${body.vendorName}, subscribedBefore=${body.subscribedBefore}")
                ApiResult.Success(body)
            } else {
                val error = parseError(response)
                Log.e(TAG, "<- authPatient [${response.code()}]: ${error?.error} - ${error?.errorDescription}")
                ApiResult.Error(response.code(), error?.error ?: "", error?.errorDescription ?: "Auth failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "<- authPatient exception", e)
            ApiResult.NetworkError(e)
        }
    }

    // ---- API #3: Get Current Measurement ----
    suspend fun getCurrentMeasurement(institutionId: String, patientId: String): ApiResult<MeasurementInfo> {
        Log.d(TAG, "-> getCurrentMeasurement($institutionId, $patientId)")
        return try {
            val response = apiService.getCurrentMeasurementInfo(institutionId, patientId)
            if (response.isSuccessful) {
                val body = response.body()!!
                Log.d(TAG, "<- getCurrentMeasurement OK: mode=${body.mode}, state=${body.state}, measureId=${body.measureRecordId}")
                ApiResult.Success(body)
            } else {
                val error = parseError(response)
                Log.e(TAG, "<- getCurrentMeasurement [${response.code()}]: ${error?.errorDescription}")
                ApiResult.Error(response.code(), error?.error ?: "", error?.errorDescription ?: "Failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "<- getCurrentMeasurement exception", e)
            ApiResult.NetworkError(e)
        }
    }

    // ---- API #4 + #5: Fetch All History ----
    suspend fun fetchAllHistory(
        institutionId: String,
        patientId: String,
        measureId: String
    ): ApiResult<Int> {
        Log.d(TAG, "-> fetchAllHistory($measureId)")

        // Step 1: Get total count
        return try {
            val countResponse = apiService.getTotalHistoryCount(institutionId, patientId, measureId)
            if (!countResponse.isSuccessful) {
                val error = parseError(countResponse)
                Log.e(TAG, "<- fetchAllHistory totalCount [${countResponse.code()}]: ${error?.errorDescription}")
                return ApiResult.Error(countResponse.code(), error?.error ?: "", error?.errorDescription ?: "Failed")
            }

            val totalRow = countResponse.body()?.totalRow ?: 0
            Log.d(TAG, "   fetchAllHistory totalRow=$totalRow")

            if (totalRow == 0) {
                return ApiResult.Success(0)
            }

            // Step 2: Paginated download
            var currentPage = 1
            var savedCount = 0
            while (true) {
                val historyResponse = apiService.getEventTagHistory(
                    institutionId = institutionId,
                    patientId = patientId,
                    measureId = measureId,
                    pageNumber = currentPage
                )
                val body = historyResponse.body() ?: break

                Log.d(TAG, "   fetchAllHistory page $currentPage/${body.totalPage}, rows=${body.rows.size}")

                val dbEntities = body.rows.mapNotNull { tag ->
                    try {
                        tag.toDbEntity(measureId, MeasurementInfo.MODE_VIRTUAL_TAG)
                    } catch (e: Exception) {
                        Log.e(TAG, "   fetchAllHistory parse error: ${e.message}")
                        null
                    }
                }
                eventTagDao.insertAll(dbEntities)
                savedCount += dbEntities.size

                if (currentPage >= body.totalPage) break
                currentPage++
            }

            Log.d(TAG, "<- fetchAllHistory OK: saved $savedCount tags")
            ApiResult.Success(savedCount)

        } catch (e: Exception) {
            Log.e(TAG, "<- fetchAllHistory exception", e)
            ApiResult.NetworkError(e)
        }
    }

    // ---- API #6: Unsubscribe (Logout) ----
    suspend fun unsubscribePatient(institutionId: String, patientId: String): ApiResult<UnsubscribeResponse> {
        Log.d(TAG, "-> unsubscribePatient($institutionId, $patientId)")
        return try {
            val response = apiService.unsubscribePatient(institutionId, patientId)
            if (response.isSuccessful) {
                val body = response.body()!!
                Log.d(TAG, "<- unsubscribePatient OK: ${body.message}")
                ApiResult.Success(body)
            } else {
                val error = parseError(response)
                Log.e(TAG, "<- unsubscribePatient [${response.code()}]: ${error?.errorDescription}")
                ApiResult.Error(response.code(), error?.error ?: "", error?.errorDescription ?: "Unsubscribe failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "<- unsubscribePatient exception", e)
            ApiResult.NetworkError(e)
        }
    }

    // ---- API #7: Upload Virtual Event Tags ----
    suspend fun uploadVirtualEventTags(
        institutionId: String,
        patientId: String,
        measureId: String,
        tags: List<EventTagDbEntity>
    ): ApiResult<AddVirtualTagsResponse> {
        Log.d(TAG, "-> uploadVirtualEventTags(${tags.size} tags)")

        val request = AddVirtualTagsRequest(
            deviceUUID = getDeviceUUID(),
            appVersion = getAppVersion(),
            appType = 1,
            tags = tags.map { it.toVirtualTagRequest() }
        )

        return try {
            val response = apiService.addVirtualEventTags(institutionId, patientId, measureId, request)
            if (response.isSuccessful) {
                val body = response.body()!!
                Log.d(TAG, "<- uploadVirtualEventTags OK: added=${body.addedSize}, updated=${body.updatedSize}, failed=${body.failedSize}")

                if (body.failedSize > 0 && (body.addedSize + body.updatedSize) != tags.size) {
                    Log.e(TAG, "   uploadVirtualEventTags partial failure: ${body.failedAdding}")
                    return ApiResult.Error(200, "partial_failure", "Upload partially failed: ${body.failedSize} tags failed")
                }

                // Mark as uploaded
                val updatedTags = tags.map { it.copy(isEdit = false) }
                eventTagDao.insertAll(updatedTags)

                ApiResult.Success(body)
            } else {
                val error = parseError(response)
                Log.e(TAG, "<- uploadVirtualEventTags [${response.code()}]: ${error?.errorDescription}")
                ApiResult.Error(response.code(), error?.error ?: "", error?.errorDescription ?: "Upload failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "<- uploadVirtualEventTags exception", e)
            ApiResult.NetworkError(e)
        }
    }

    // ---- Local: Clear data on logout ----
    suspend fun clearLocalData() {
        Log.d(TAG, "-> clearLocalData()")
        eventTagDao.clearAll()
        Log.d(TAG, "<- clearLocalData OK")
    }

    private fun getDeviceUUID(): String {
        // TODO: implement
        return ""
    }

    private fun getAppVersion(): String {
        // TODO: implement
        return ""
    }
}
```

---

## 6. ViewModel Layer

### LoginViewModel.kt

```kotlin
class LoginViewModel(private val repository: PatientRepository) : ViewModel() {

    private val _tokenState = MutableLiveData<ApiResult<String>>()
    val tokenState: LiveData<ApiResult<String>> = _tokenState

    private val _authState = MutableLiveData<ApiResult<AuthPatientResponse>>()
    val authState: LiveData<ApiResult<AuthPatientResponse>> = _authState

    private val _measureState = MutableLiveData<ApiResult<MeasurementInfo>>()
    val measureState: LiveData<ApiResult<MeasurementInfo>> = _measureState

    private val _historyState = MutableLiveData<ApiResult<Int>>()
    val historyState: LiveData<ApiResult<Int>> = _historyState

    private var institutionId: String = ""
    private var patientId: String = ""
    private var measureId: String = ""

    // Step 1: Get Token
    fun getToken() {
        _tokenState.value = ApiResult.Loading
        viewModelScope.launch {
            _tokenState.value = repository.getToken()
        }
    }

    // Step 2: Auth Patient
    fun authPatient(instId: String, patId: String) {
        institutionId = instId
        patientId = patId
        _authState.value = ApiResult.Loading
        viewModelScope.launch {
            _authState.value = repository.authPatient(instId, patId)
        }
    }

    // Step 3: Get Current Measurement
    fun getCurrentMeasurement() {
        _measureState.value = ApiResult.Loading
        viewModelScope.launch {
            _measureState.value = repository.getCurrentMeasurement(institutionId, patientId)
        }
    }

    // Step 4: Fetch History
    fun fetchHistory(measureRecordId: String) {
        measureId = measureRecordId
        _historyState.value = ApiResult.Loading
        viewModelScope.launch {
            _historyState.value = repository.fetchAllHistory(institutionId, patientId, measureRecordId)
        }
    }
}
```

### MainViewModel.kt

```kotlin
class MainViewModel(private val repository: PatientRepository) : ViewModel() {

    private val _uploadState = MutableLiveData<ApiResult<AddVirtualTagsResponse>>()
    val uploadState: LiveData<ApiResult<AddVirtualTagsResponse>> = _uploadState

    private val _logoutState = MutableLiveData<ApiResult<Unit>>()
    val logoutState: LiveData<ApiResult<Unit>> = _logoutState

    fun uploadTags(
        institutionId: String,
        patientId: String,
        measureId: String,
        tags: List<EventTagDbEntity>
    ) {
        _uploadState.value = ApiResult.Loading
        viewModelScope.launch {
            _uploadState.value = repository.uploadVirtualEventTags(
                institutionId, patientId, measureId, tags
            )
        }
    }

    fun logout(institutionId: String, patientId: String) {
        _logoutState.value = ApiResult.Loading
        viewModelScope.launch {
            // Unsubscribe regardless of result
            repository.unsubscribePatient(institutionId, patientId)
            repository.clearLocalData()
            _logoutState.value = ApiResult.Success(Unit)
        }
    }
}
```

---

## 7. Activity Layer (UI only)

### LoginActivity.kt

```kotlin
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        observeToken()
        observeAuth()
        observeMeasurement()
        observeHistory()

        // Start login flow
        btnLogin.setOnClickListener {
            viewModel.getToken()
        }
    }

    private fun observeToken() {
        viewModel.tokenState.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> showLoading("Getting token...")
                is ApiResult.Success -> {
                    NetworkModule.setToken(result.data)
                    val instId = etInstitutionId.text.toString()
                    val patId = etPatientId.text.toString()
                    viewModel.authPatient(instId, patId)
                }
                is ApiResult.Error -> {
                    hideLoading()
                    showError("Token failed: ${result.message}")
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error: ${result.exception.message}")
                }
            }
        }
    }

    private fun observeAuth() {
        viewModel.authState.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> showLoading("Authenticating...")
                is ApiResult.Success -> {
                    viewModel.getCurrentMeasurement()
                }
                is ApiResult.Error -> {
                    hideLoading()
                    val msg = when (result.error) {
                        "patient_already_subscribed" -> getString(R.string.patient_already_logged_in)
                        "invalid_patient" -> getString(R.string.invalid_patient)
                        "invalid_institution_id" -> getString(R.string.invalid_institution_id)
                        else -> result.message
                    }
                    showError(msg)
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error: ${result.exception.message}")
                }
            }
        }
    }

    private fun observeMeasurement() {
        viewModel.measureState.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> showLoading("Checking measurement...")
                is ApiResult.Success -> {
                    val info = result.data
                    if (!info.isMeasuring()) {
                        hideLoading()
                        showError("Not in measuring state")
                        return@observe
                    }
                    if (!info.isVirtualTagMode()) {
                        hideLoading()
                        showError("Unsupported measure mode")
                        return@observe
                    }
                    viewModel.fetchHistory(info.measureRecordId)
                }
                is ApiResult.Error -> {
                    hideLoading()
                    showError(result.message)
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error: ${result.exception.message}")
                }
            }
        }
    }

    private fun observeHistory() {
        viewModel.historyState.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> showLoading("Downloading history...")
                is ApiResult.Success -> {
                    hideLoading()
                    navigateToMain()
                }
                is ApiResult.Error -> {
                    hideLoading()
                    showError(result.message)
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error: ${result.exception.message}")
                }
            }
        }
    }

    private fun showLoading(message: String) {
        progressBar.isVisible = true
        tvStatus.text = message
        btnLogin.isEnabled = false
    }

    private fun hideLoading() {
        progressBar.isVisible = false
        tvStatus.text = ""
        btnLogin.isEnabled = true
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
```

### MainActivity.kt

```kotlin
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        observeUpload()
        observeLogout()

        btnUpload.setOnClickListener { uploadPendingTags() }
        btnLogout.setOnClickListener { logout() }
    }

    private fun observeUpload() {
        viewModel.uploadState.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> showLoading()
                is ApiResult.Success -> {
                    hideLoading()
                    val data = result.data
                    Toast.makeText(this, "Uploaded: ${data.addedSize} added, ${data.updatedSize} updated", Toast.LENGTH_SHORT).show()
                    refreshTagList()
                }
                is ApiResult.Error -> {
                    hideLoading()
                    showError(result.message)
                }
                is ApiResult.NetworkError -> {
                    hideLoading()
                    showError("Network error: ${result.exception.message}")
                }
            }
        }
    }

    private fun observeLogout() {
        viewModel.logoutState.observe(this) { result ->
            when (result) {
                is ApiResult.Loading -> showLoading()
                is ApiResult.Success -> {
                    navigateToLogin()
                }
                else -> {
                    // Logout always clears local data and navigates
                    navigateToLogin()
                }
            }
        }
    }

    private fun uploadPendingTags() {
        // TODO: get pending tags from DB and call viewModel.uploadTags(...)
    }

    private fun refreshTagList() {
        // TODO: reload tag list from DB
    }

    private fun showLoading() { progressBar.isVisible = true }
    private fun hideLoading() { progressBar.isVisible = false }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
```

---

## Log Output Examples

### Success Flow

```
D/HTTP:         -> POST https://mct-api.rooticare.com/oauth/token
D/HTTP:         <- 200 https://mct-api.rooticare.com/oauth/token (120ms)
D/PatientRepo:  -> getToken()
D/PatientRepo:  <- getToken OK

D/HTTP:         -> GET https://mct-api.rooticare.com/oauth/vendors/rootilabs/patients/VT01
D/HTTP:         <- 200 https://mct-api.rooticare.com/oauth/vendors/rootilabs/patients/VT01 (85ms)
D/PatientRepo:  -> authPatient(rootilabs, VT01)
D/PatientRepo:  <- authPatient OK: vendorName=RootiLabs, subscribedBefore=false

D/HTTP:         -> GET .../measures/currentMeasurement
D/HTTP:         <- 200 .../measures/currentMeasurement (90ms)
D/PatientRepo:  -> getCurrentMeasurement(rootilabs, VT01)
D/PatientRepo:  <- getCurrentMeasurement OK: mode=0, state=0, measureId=MR-001

D/PatientRepo:  -> fetchAllHistory(MR-001)
D/PatientRepo:     fetchAllHistory totalRow=12
D/PatientRepo:     fetchAllHistory page 1/3, rows=5
D/PatientRepo:     fetchAllHistory page 2/3, rows=5
D/PatientRepo:     fetchAllHistory page 3/3, rows=2
D/PatientRepo:  <- fetchAllHistory OK: saved 12 tags
```

### Error Flow

```
D/HTTP:         -> GET https://mct-api.rooticare.com/oauth/vendors/rootilabs/patients/INVALID
E/HTTP:         <- 400 https://mct-api.rooticare.com/oauth/vendors/rootilabs/patients/INVALID (65ms)
                   {"error":"invalid_patient","error_description":"patient: [ INVALID ] is invalid."}
D/PatientRepo:  -> authPatient(rootilabs, INVALID)
E/PatientRepo:  <- authPatient [400]: invalid_patient - patient: [ INVALID ] is invalid.
```

### Network Error Flow

```
D/PatientRepo:  -> authPatient(rootilabs, VT01)
E/PatientRepo:  <- authPatient exception
                   java.net.UnknownHostException: Unable to resolve host "mct-api.rooticare.com"
```

---

## Summary: Where Each Layer Logs

| Layer | What | Tag |
|-------|------|-----|
| **ApiLoggingInterceptor** | HTTP method, URL, status code, raw error body | `HTTP` |
| **PatientRepository** | Business operation name, parsed fields, error details | `PatientRepo` |
| **ViewModel** | Nothing (just state relay) | - |
| **Activity** | Nothing (just UI update) | - |
