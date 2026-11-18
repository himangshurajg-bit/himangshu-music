# Himangshu fork notices

This public fork is derived from [EchoMusicApp/Echo-Music](https://github.com/EchoMusicApp/Echo-Music), distributed under GNU GPL version 3; see [`LICENSE`](LICENSE). Upstream source and attribution notices are retained. This modification is not affiliated with or endorsed by the upstream author.

Modified on 2026-11-18:

- `app/build.gradle.kts`: read Last.fm credentials from local properties or environment variables rather than committing the upstream values; deleted the upstream Firebase configuration (`app/google-services.json`) and ignored local copies.
- `app/src/main/res/values/app_name.xml`: changed the launcher label to “Himangshu.”
- `WelcomeDialog.kt`, `AboutScreen.kt`, `WrappedScreen.kt`, and `WrappedShareHelper.kt`: changed selected product-name and share-text displays to “Himangshu.”
- `README.md`, `AGENT.md`, `.gitignore`, and `upcomingupdate.json`: document the fork, attribution/license and configuration boundary; ignore local Firebase configuration.

The original package/application ID and other upstream product functionality remain. This is a branding/configuration fork, not a claim of complete original authorship.
