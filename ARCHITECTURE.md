# Android Modern Architecture Refactoring

## 개요

QGen 앱을 Google 권장 Android 모던 아키텍처로 리팩토링했습니다.
간결성을 위해 **2-Layer Architecture** (Presentation + Data)를 채택했습니다.

## 아키텍처 계층

```
┌─────────────────────────────────────┐
│         Presentation Layer          │
│  (UI + ViewModel)                   │
│  - GenerationScreen                 │
│  - QuizScreen                       │
│  - ResultScreen                     │
│  - GenerationViewModel              │
│  - QuizViewModel                    │
│  - ResultViewModel                  │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Data Layer                  │
│  (Repository + DataSource)          │
│  - QuestionRepository               │
│  - QuestionRemoteDataSource         │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│         Network Layer               │
│  (Retrofit API)                     │
│  - QGenApi                          │
└─────────────────────────────────────┘
```

## 아키텍처 선택 이유

### 2-Layer vs 3-Layer

**3-Layer (Presentation + Domain + Data):**
- UseCase 계층 추가
- 복잡한 비즈니스 로직이 많을 때 유용
- 더 많은 보일러플레이트 코드

**2-Layer (Presentation + Data):** ✅ 선택
- ViewModel이 Repository를 직접 호출
- 간단한 앱에 적합
- 코드가 더 간결하고 이해하기 쉬움
- 필요시 나중에 Domain 계층 추가 가능

## 새로 추가된 파일

### 1. Data Layer

#### `QuestionRemoteDataSource.kt`
```
core/data/source/remote/QuestionRemoteDataSource.kt
```
- **역할**: API 호출을 캡슐화하는 데이터 소스
- **책임**:
  - Retrofit API 인터페이스 호출
  - 네트워크 응답을 도메인 모델로 변환
  - 네트워크 에러 처리

#### `QuestionRepository.kt`
```
core/data/repository/QuestionRepository.kt
```
- **역할**: 데이터의 단일 진실 공급원 (Single Source of Truth)
- **책임**:
  - 여러 데이터 소스 조율 (현재는 Remote만 있지만 확장 가능)
  - 비즈니스 로직에 맞는 데이터 변환
  - Flow를 사용한 반응형 데이터 스트림 제공
  - ResultWrapper로 성공/실패/로딩 상태 관리

### 2. DI Module

#### `DataModule.kt`
```
core/di/DataModule.kt
```
- Repository와 DataSource의 의존성 주입 설정

## 변경된 파일

### `GenerationViewModel.kt`
**Before:**
```kotlin
class GenerationViewModel(
    private val api: QGenApi,
    private val sessionViewModel: QGenSessionViewModel
)
```

**After:**
```kotlin
class GenerationViewModel(
    private val questionRepository: QuestionRepository,
    private val sessionViewModel: QGenSessionViewModel
)
```

**변경 사항:**
- 직접 API를 호출하는 대신 Repository를 통해 호출
- Flow를 사용한 반응형 데이터 처리
- ResultWrapper를 통한 명확한 상태 관리

### `QGenApplication.kt`
**변경 사항:**
- `dataModule` 추가
- Koin 모듈 초기화 순서: networkModule → dataModule → viewModelModule

## 아키텍처 이점

### 1. 관심사의 분리 (Separation of Concerns)
- **Presentation**: UI 로직과 상태 관리
- **Data**: 데이터 소스 관리 및 비즈니스 로직

### 2. 테스트 용이성
- 각 계층을 독립적으로 테스트 가능
- Mock/Fake 구현체로 쉽게 교체 가능
- Repository 패턴으로 데이터 소스 추상화

### 3. 확장성
- 새로운 데이터 소스 추가 용이 (Local DB, Cache 등)
- 비즈니스 로직이 복잡해지면 Domain 계층 추가 가능
- UI 변경 시 다른 계층에 영향 없음

### 4. 유지보수성
- 명확한 책임 분리
- 단방향 데이터 흐름
- 의존성 역전 원칙 (DIP) 적용

### 5. 간결성
- UseCase 계층 없이 ViewModel이 Repository 직접 호출
- 보일러플레이트 코드 최소화
- 빠른 개발 속도

## 데이터 흐름

### 문제 생성 플로우
```
1. User Action (GenerationScreen)
   ↓
2. ViewModel.onGenerateClicked()
   ↓
3. QuestionRepository.generateQuestions()
   ↓
4. QuestionRemoteDataSource.generateQuestions()
   ↓
5. QGenApi.generateQuestions()
   ↓
6. Network Response
   ↓
7. Flow<ResultWrapper<Pair<Questions, Metadata>>>
   ↓
8. ViewModel updates UI State
   ↓
9. UI recomposes
```

## ResultWrapper 패턴

```kotlin
sealed class ResultWrapper<out T> {
    data class Success<out T>(val value: T) : ResultWrapper<T>()
    data class Error(
        val code: Int? = null,
        val message: String? = null,
        val throwable: Throwable? = null
    ) : ResultWrapper<Nothing>()
    object Loading : ResultWrapper<Nothing>()
}
```

**사용 예시:**
```kotlin
questionRepository.generateQuestions(request, useMockApi)
    .collect { result ->
        when (result) {
            is ResultWrapper.Loading -> {
                // Show loading UI
            }
            is ResultWrapper.Success -> {
                val (questions, metadata) = result.value
                // Update UI with data
            }
            is ResultWrapper.Error -> {
                // Show error message
            }
        }
    }
```

## 향후 개선 사항

### 1. Local Data Source 추가
```kotlin
interface QuestionLocalDataSource {
    suspend fun saveQuestions(questions: List<Question>)
    suspend fun getQuestions(): List<Question>
    suspend fun clearQuestions()
}
```

### 2. Caching 전략
```kotlin
class QuestionRepositoryImpl(
    private val remoteDataSource: QuestionRemoteDataSource,
    private val localDataSource: QuestionLocalDataSource
) : QuestionRepository {
    override fun generateQuestions(...) = flow {
        // Try local first
        val cached = localDataSource.getQuestions()
        if (cached.isNotEmpty()) {
            emit(ResultWrapper.Success(cached))
        }
        
        // Then fetch from remote
        val remote = remoteDataSource.generateQuestions(request)
        localDataSource.saveQuestions(remote)
        emit(ResultWrapper.Success(remote))
    }
}
```

### 3. Domain Layer 추가 (필요시)
복잡한 비즈니스 로직이 필요할 경우:
```kotlin
// UseCase 예시
class GenerateQuestionsUseCase(
    private val questionRepository: QuestionRepository
) {
    operator fun invoke(
        request: GenerateQuestionsRequest,
        useMockApi: Boolean = false
    ): Flow<ResultWrapper<Pair<List<Question>, QuestionSetMetadata>>> {
        // 복잡한 비즈니스 로직 추가
        return questionRepository.generateQuestions(request, useMockApi)
    }
}
```

### 4. Error Handling 개선
```kotlin
sealed class DomainError {
    data class NetworkError(val message: String) : DomainError()
    data class ValidationError(val field: String) : DomainError()
    object UnknownError : DomainError()
}
```

### 5. Paging 지원
```kotlin
fun getQuestionsPaged(): Flow<PagingData<Question>>
```

## 마이그레이션 체크리스트

- [x] RemoteDataSource 생성
- [x] Repository 생성
- [x] ViewModel 리팩토링
- [x] DI 모듈 설정
- [x] 빌드 성공 확인
- [ ] Unit 테스트 작성
- [ ] Integration 테스트 작성
- [ ] Local DataSource 추가 (향후)
- [ ] Caching 전략 구현 (향후)

## 디렉토리 구조

```
app/src/main/java/co/kr/qgen/
├── core/
│   ├── data/
│   │   ├── repository/
│   │   │   └── QuestionRepository.kt
│   │   └── source/
│   │       └── remote/
│   │           └── QuestionRemoteDataSource.kt
│   ├── di/
│   │   └── DataModule.kt
│   ├── model/
│   │   ├── Question.kt
│   │   ├── GenerateQuestionsRequest.kt
│   │   ├── GenerateQuestionsResponse.kt
│   │   ├── ResultWrapper.kt
│   │   └── ...
│   ├── network/
│   │   ├── QGenApi.kt
│   │   └── NetworkModule.kt
│   └── ui/
│       ├── theme/
│       └── components/
├── feature/
│   ├── generation/
│   │   ├── GenerationScreen.kt
│   │   └── GenerationViewModel.kt
│   ├── quiz/
│   │   ├── QuizScreen.kt
│   │   └── QuizViewModel.kt
│   └── result/
│       ├── ResultScreen.kt
│       └── ResultViewModel.kt
├── MainActivity.kt
└── QGenApplication.kt
```

## 참고 자료

- [Android App Architecture Guide](https://developer.android.com/topic/architecture)
- [Guide to app architecture](https://developer.android.com/jetpack/guide)
- [Repository Pattern](https://developer.android.com/codelabs/basic-android-kotlin-training-repository-pattern)
- [Now in Android App](https://github.com/android/nowinandroid) - Google의 공식 샘플 앱
