# Theming: light and dark

The app follows the user's choice in **Account → App Appearance** (System default,
Light, Dark), stored per device in `ThemePreferences` and applied at the root by
`AppTheme(mode)`. `SystemBarsEffect` keeps the status bar icons readable.

Screens must not hard-code `Color.White`, `Color.Black`, `Color.Gray`,
`Color.LightGray` or hex colours: those stay white-on-white or black-on-black when
the theme changes. Use these instead.

## Where colours come from

- `MaterialTheme.colorScheme`: surfaces and text.
- `AppTheme.colors` (`com.elozino.splitfy.ui.theme.AppTheme`): meanings Material has
  no slot for: brand yellow, money owed/owing, raised fields, skeletons.

## Mapping

| Was | Use | Notes |
|---|---|---|
| `Color.White` as a screen, top bar, Scaffold `containerColor` or bottom bar | `colorScheme.background` (screens), `colorScheme.surface` (bars, sheets, dialogs, cards) | |
| `Color.Black` text/icons on a screen | `colorScheme.onBackground` / `onSurface` | Or simply omit `color`: `Text` already uses `LocalContentColor` |
| `Color.Black` text/icons **on the brand yellow** (buttons, home header, FAB, badges on yellow) | `AppTheme.colors.onBrand` | Stays dark in both themes, because yellow needs dark content |
| `Color.Gray` secondary text, hint icons, chevrons | `colorScheme.onSurfaceVariant` | |
| `Color.LightGray` placeholders, avatar circles, image boxes | `colorScheme.surfaceVariant` | |
| `Color.LightGray.copy(alpha = …)` dividers and borders | `colorScheme.outlineVariant` | |
| `Color(0xFFF5F5F5)` / `0xFFF0F0F0` fields, chips, summary cards | `AppTheme.colors.field` | |
| `Color(0xFFFEBB1B)`, `primaryLight` | `AppTheme.colors.brand` | `primaryLight` still exists for old code; prefer the token |
| Greens (`0xFF4CAF50`, `0xFF2E7D32`): owed to you, settled, success | `AppTheme.colors.positive` | |
| Reds (`Color.Red`, `0xFFE57373`, `0xFFC62828`, `0xFFD32F2F`): you owe, destructive | `AppTheme.colors.negative` | Form errors: `colorScheme.error` |
| `Color.Black.copy(alpha = x)` overlays, scrims | `colorScheme.scrim.copy(alpha = x)` | |
| `Color.White` text on a dark photo or scrim | keep `Color.White` | Legitimate: it's on an image, not the theme |
| `Color.Transparent` | keep | |

Buttons: brand buttons use
`ButtonDefaults.buttonColors(containerColor = AppTheme.colors.brand, contentColor = AppTheme.colors.onBrand)`.
Outlined buttons on a screen use `colorScheme.onSurface` text with a brand border.

## Checking a screen

1. Grep it: `grep -nE 'Color\.(White|Black|Gray|LightGray|Red)|Color\(0x' <file>`.
   What's left should be in the "keep" rows above.
2. Look at it in both themes: switch in App Appearance, or flip the emulator's
   dark theme with System default selected.
3. Watch for text on the yellow: it must stay dark in dark mode.

## The status bar

Its icons follow whatever is drawn behind it, not just the theme. A screen that
puts something other than `colorScheme.background` under the status bar (an app
bar in `AppTheme.colors.brand`, a full-screen camera) marks it:

```kotlin
CenterAlignedTopAppBar(
    modifier = Modifier.statusBarBackground(AppTheme.colors.brand),
    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppTheme.colors.brand),
    …
)
```

The icons turn dark over light colours and light over dark ones, and go back to
the theme once the marked element leaves the top of the screen. Unmarked screens
need nothing.
