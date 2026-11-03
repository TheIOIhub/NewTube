# NewTube – Специфікація та План Розробки

## 1. Загальний опис

**Назва:** *NewTube* (версія 1.0.0)

**Мета проекту:** Створити Android‑додаток, що дозволяє переглядати відео, шортси і пряму трансляцію із YouTube без реклами, використовуючи відкритий API Invidious.

## 2. Функціональні вимоги
| Вкладка | Функція | Опис |
|---------|---------|------|
| **Головна** | Відображення популярних і рекомендованих відео | Пагінований список, infinite scroll |
| **Шортси** | Перегляд шортсів | Список з плавним скролом |
| **Пошук** | Пошук по ключовим словам, каналам, трансляціям | Автокомпліт, історія пошуку |
| **Канали** | Перегляд списку відео, шортсів, стрімів | Показ аватарки, назви, кількість підписників |
| **Плеєр** | ExoPlayer з підтримкою HLS/DASH, вибір якості, аудіо, fullscreen | Позиція збережена, можливий PIP |
| **Кешування** | Метадані, тумб наліси | Room, DataStore |
| **Тема** | Чорна/біла | Material3, підтримка dark‑mode |

## 3. Нефункціональні вимоги
- Підтримка Android API 24+.
- Мульти‑платформний код (Compose).
- Тестованість (JUnit, Compose‑Test, MockK).
- CI/CD (GitHub Actions).
- Open‑source ліцензія MIT.

## 4. Архітектура
*Domain* → *Data* → *Presentation* (MVVM). DI через Hilt. Плейєр на ExoPlayer. API через Retrofit + Moshi. Пам’ять: Room + DataStore.

## 5. Технічний стек
| Компонент | Версія |
|-----------|--------|
| Kotlin | 2.0.0 |
| Android Gradle Plugin | 8.0.0 |
| Jetpack Compose | 1.5.3 |
| Hilt | 2.47 |
| Retrofit | 2.11.0 |
| Moshi | 1.15.1 |
| ExoPlayer | 2.19.0 |
| Room | 2.6.1 |
| DataStore | 1.1.0 |
| Kotlinx Coroutines | 1.8.1 |
| ktlint | 12.0.1 |

## 6. План розробки (milestones)
1. **0‑1 місяць** – Створення проекту, налаштування CI, базова UI (MainActivity, NavHost).
2. **1‑2 місяць** – Реалізація Invidious API (Retrofit, Moshi), Repository, RepositoryImpl.
3. **2‑3 місяць** – ViewModels і UI‑компоненти для Home/Shorts/Search.
4. **3‑4 місяць** – Плейєр (ExoPlayer) з вибором якості, аудіо, fullscreen.
5. **4‑5 місяць** – Тестування, кешування, Dark‑Mode, баг‑фікси.
6. **5‑6 місяць** – Підготовка релізу 1.0, документація, підписка на канали.

## 7. Ключові вузли і посилання
- **Invidious API** – https://github.com/iv-org/invidious
- **ExoPlayer** – https://exoplayer.dev/
- **Hilt** – https://dagger.dev/hilt/
- **Room** – https://developer.android.com/topic/libraries/architecture/room

## 8. Додаткові ресурси
- CI Workflow (GitHub Actions) – `.github/workflows/android.yml`
- Тема та UI – `ui/theme`
- Основний модуль – `app` (`MainActivity.kt`, `NavGraph.kt`)

---

Цей файл `SPEC.md` містить повну специфікацію та план розробки.
