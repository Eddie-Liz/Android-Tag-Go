# Review profile
## default-branch
```text
main
```
## verify
```text
./gradlew test
```
## sensitive-paths
```text
app/src/main/java/com/rootilabs/wmeCardiac/data/auth/**
app/src/main/java/com/rootilabs/wmeCardiac/data/api/AuthApi.kt
app/proguard-rules.pro
app/build.gradle.kts
.claude/commands/play_release_production.sh
```
## skip-paths
```text
gradle/wrapper/**
**/build/**
```
## what-failure-looks-like
```text
CLAUDE.md §Key Business Logic — measureRecordId written anywhere but login step 4 or cleared outside explicit logout
CLAUDE.md §Key Business Logic — logout clearing local data when unsubscribePatient fails offline
CLAUDE.md §R8 / ProGuard — keep rule for data.api, Continuation, retrofit2.Response or mlkit removed or narrowed
```
