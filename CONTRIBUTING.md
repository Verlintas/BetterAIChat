# Contributing to BetterAIChat

Thanks for your interest! This project is a single-developer app built iteratively; any help is welcome.

## Ways to contribute

- **Bug reports & feature ideas**: open an [issue](https://github.com/Verlintas/BetterAIChat/issues) with your device model, Android version, and steps to reproduce
- **Translations**: the UI ships with `values/` (Chinese) and `values-en/` (English) string resources. Reviewing/improving the English translations or localizing into new languages is high-value work
- **New device tools**: implement a `DeviceTool` in `:skills` and register it in `BetterAIChatApp.kt`
- **New providers**: add an adapter in `:providers` implementing `ChatProvider`
- **Code review**: the codebase is compact (~150 source files); comments and suggestions welcome

## Development setup

```bash
# JDK 17, Android SDK with compileSdk 36
./gradlew assembleFullDebug
```

Run all four modules' debug build before opening a PR:

```bash
./gradlew assembleFullDebug assembleLiteDebug
```

## Style

- Kotlin, no comments unless needed for clarity
- Keep the module boundaries: `core` (no UI), `providers` (network only), `skills` (device actions), `app` (UI/ViewModel)
- Follow existing patterns (e.g., `DeviceTool` interface for tools, `SettingsRepository` for preferences)

## Releases

Versioned via GitHub Releases with signed APKs (`-full` and `-lite` variants). Release notes are bilingual (Chinese + English).
