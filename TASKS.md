# NewTube – Task Tracker

## Current Status

| # | Task | Done |
|---|------|------|
| 1 | Project repo created on GitHub (NewTube) | ✅ |
| 2 | Repository skeleton (Gradle, CI, README, SPEC) | ✅ |
| 3 | MainActivity + NavHost + bottom navigation | ✅ |
| 4 | Compose themes (Material3, Colors, Shapes, Typography) | ✅ |
| 5 | Hilt setup (app module) | ✅ |
| 6 | Basic ViewModels for each screen | ✅ |
| 7 | Retrofit + Moshi + InvidiousService interface | ✅ |
| 8 | Domain models: Video, Channel, Short | ✅ |
| 9 | VideoRepository interface | ✅ |
|10| Data layer: Room entity, DAO, DB | ✅ |
|11| Dependency modules (Network, Database, Repository) | ✅ |
|12| VideoRepositoryImpl (mapping & caching stub) | ✅ |
|13| Integration of :domain and :data modules in app | ✅ |

## Next Steps (planned)

1. **Implement proper mapping and caching in VideoRepositoryImpl** – currently inserts into DB but no error handling.
2. **Create and expose screen composables for Home, Shorts, Search and Channel** – list video items, navigation to VideoPlayer.
3. **Implement VideoPlayer screen** with ExoPlayer, quality and audio selectors, fullscreen.
4. **Add Live Streaming support** – parse livestream metadata.
5. **Add persistence for user preferences (theme, default quality)** – DataStore.
6. **Write unit tests** for repository, ViewModels, and UI navigation.
7. **Write UI tests** for navigation flows and player controls.
8. **Finalize UI polish, animations, dark mode toggle**.
9. **Publish version 1.0.0** – sign release build, update CI.
10. **Add feature: channel subscriptions** – database for subscription list.

> All tasks will be tracked in this markdown and updated as they complete.
