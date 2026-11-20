# RajTune Design Guidelines

RajTune follows a **custom, modern aesthetic** that blends some Material Design principles with unique, iOS-inspired patterns.

This document is the definitive guide for designing and implementing UI in the RajTune codebase. All new UI work and refactors must follow these custom principles rather than strictly adhering to Google's Material Design 3 spec.

---

## 1. Color System & Theming

We use a dynamic color system, but apply it in a custom way to achieve a unique look.

### Dynamic Color & Seed
*   **Dynamic First:** Colors must come from `MaterialTheme.colorScheme`. For devices running Android 12+, dynamic system colors are extracted. On older devices, the default theme seed `0xFFED5564` is used to generate the palette using `materialKolor`.
*   **Translucency:** A core part of the RajTune look is translucent surfaces. For example, cards often use `surfaceVariant.copy(alpha = 0.3f)` rather than solid M3 container colors.

### Semantic Color Roles
Use the correct semantic color roles as defined by our theme:
*   **Primary (`primary` / `onPrimary`):** Used for the most prominent components across the app, active states, and filled buttons.
*   **Surface (`surface` / `onSurface`):** Backgrounds for the app and solid menus.
*   **Translucent Surfaces:** Custom translucent backgrounds (like `surfaceVariant.copy(alpha = 0.3f)`) are heavily used for cards, segmented buttons, and grouped lists to create a softer, layered aesthetic.

---

## 2. Surfaces and Effects

Use the app’s Material color roles and existing component-specific surfaces. The custom Liquid Glass/backdrop-refraction system is no longer part of RajTune; do not reintroduce it. Keep player and navigation surfaces on their standard Material styling, and use only the non-glass player backgrounds already supported by the app.

## 3. Components in Detail

Do NOT strictly force Material 3 components if they break the app's custom aesthetic. Match the existing components found in the app.

### Buttons & Controls
*   **Segmented Controls:** We frequently use custom segmented buttons (e.g., Row with rounded buttons) rather than M3 standard tabs or segmented buttons.
*   **Rounded Shapes:** Elements heavily lean towards large corner radii (`RoundedCornerShape(24.dp)` or `CircleShape`), providing a friendlier, softer UI.
*   **Settings Groups:** We use a grouped-list style (similar to iOS Settings) by encapsulating lists within a `surfaceContainerHighest` card with `24.dp` corners, rather than separate pill cards.

### Cards & Surfaces
*   **Custom Cards:** Unlike standard M3 cards (which use solid `surfaceContainer` colors), RajTune cards typically use:
    *   *Container:* `surfaceVariant.copy(alpha = 0.3f)`
    *   *Shape:* `RoundedCornerShape(24.dp)` or `28.dp`
    *   *Elevation:* 0.dp (flat, translucent look).
*   Grouped items within cards are a common pattern.

### Navigation & Headers
*   **Top App Bars:** We often use custom implementations or standard `TopAppBar` rather than `LargeTopAppBar`. Headers are sometimes manually placed over scrolling content with custom fade-in animations rather than using standard M3 `Scaffold` scroll behaviors.
*   **Bottom Navigation Bar:** Custom floating tab bars (`ui/component/floatingtabbar/`) are preferred over the standard M3 `NavigationBar`.

---

## 4. Typography & Iconography

*   **Typography:** Always use `MaterialTheme.typography` but respect the app's established font weights and sizes. RajTune leans towards bold, expressive headers and softer, highly legible body text.
*   **Iconography:** We use a mix of Material Symbols/Icons Extended (`androidx.compose.material.material-icons-extended`) and custom SVG drawables. Check `scripts/compose_svg_drawable.py` and existing drawables before importing new vector assets to avoid duplication.

---

## 5. Extending the Design System

Before adding a brand new UI component, always check `ui/component/` to see if an existing one already implements our conventions.

**Key Rule:** When working on UI, **look at the existing screens** (like the original Listen Together or Settings screens) and copy their specific visual style, spacing, and modifier chains. Do NOT refactor existing screens to match standard Material 3 unless explicitly requested. Our custom aesthetic takes precedence over M3 guidelines.


## Music Flow

Vertical swipe discovery, laid out like a Resso-style "For You" page (interaction pattern only — no copied assets).

*   **Full-bleed artwork:** Black base, the artwork blurred (`blur(80.dp)` on API 31+) as a tinted backdrop, and the sharp artwork as a centred square with 24dp side padding and 24dp rounded corners. A page-wide scrim darkens the top (0.45) and bottom (0.8 → 0.95) for text contrast. Muted Canvas replaces the square on the active playing page only.
*   **Lyrics first:** Under the transparent top bar, the current synced line in `headlineMedium` ExtraBold white, the next line in `titleLarge` at 45% white. The pair advances at the next lyric timestamp with a 280ms upward slide/fade (24dp incoming travel, 12dp outgoing travel, 180ms outgoing fade) and smooth height changes; backward seeks reverse the slide. Reduced motion disables transitions. Position is sampled every 50ms, including paused seeks, with the saved per-song timing offset applied. Intros and timestamped instrumental gaps keep the upcoming line dimmed until its timestamp. Hidden when no synced lyrics exist.
*   **Metadata:** Bottom-left title (`headlineSmall`, bold), artist (`titleMedium`, 75% white) followed by an outlined "View artist" pill (1dp 40% white border, 24dp radius).
*   **Action row:** Icon-only 48dp touch targets with spoken labels — like, lyrics, share on the left; download, add to playlist, more on the right. No counts (we have no real numbers to show). Disabled actions drop to 35% alpha.
*   **Playback:** A 56dp play glyph at 65% white appears centered inside the album artwork only while playback is paused, with a transparent 72dp touch target and no filled button background. The glyph hides while playing; tapping the target toggles the shared player. The 2dp progress line with an 8dp white thumb stays at the bottom, with 16dp bottom spacing. Tapping anywhere on the page also toggles playback; double-tap likes; long-press opens `YouTubeSongMenu`.
*   **Chrome:** Top bar is just back, "Music Flow" and an overflow (Previous/Next for people who cannot swipe, Open player, Restore previous queue, feedback, Settings). Previous/Next are also TalkBack custom actions on the pager. A translucent status pill (`White 14%`, 24dp radius) reports loading, casting/Listen Together blocks and "playing elsewhere".
*   **Home entry:** `MusicFlowEntryCard` follows the card rule above — `surfaceVariant.copy(alpha = 0.3f)`, 24dp corners, flat — with an inverted (onSurface) circular icon.

## Ambient Mode Canvas

Ambient Mode may layer muted Canvas video artwork inside the existing album-art square. Keep the original square size, rounded clipping, and interaction surface unchanged; Canvas is a non-interactive visual layer above the normal album art and follows playback state. Do not add an album-art ambient-glow background layer.
