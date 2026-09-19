# How WatchCalc works

Notes for anyone changing the code. The [README](../README.md) covers what the
app does; this covers how.

| | |
|---|---|
| Release APK | ~2 MB |
| Cold start | ~540 ms |
| Memory | ~20 MB PSS |
| Unit tests | 37 |
| minSdk / targetSdk | 30 (Wear OS 3) / 36 (Wear OS 6) |

---

## The round-screen layout

A 450 px round screen is not a 450 px square: near the top and bottom of the
display the usable width collapses, so a key row that is merely "on screen" can
still have its corners hidden under the bezel.

Every size in the app is a multiple of `--u`, one percent of the smaller screen
side, and each key row is clamped to the circle's chord at its own narrowest
edge:

| row | vertical band | chord available | width used |
|-----|---------------|-----------------|------------|
| 1   | 30–44 %       | 91.7 %          | 90 %       |
| 2   | 45–59 %       | 98.4 %          | 96 %       |
| 3   | 60–74 %       | 87.7 %          | 85 %       |
| 4   | 75–89 %       | 62.6 %          | 60 %       |

That yields eighteen keys per page (5/5/5/3). `LayoutGeometryTest` asserts it
rather than trusting it — every key rectangle is checked against the inscribed
circle at 396, 450, 454 and 480 px:

```
396px: tightest row clears the bezel by 3.0 px; key ~76 x 55 px
480px: tightest row clears the bezel by 3.6 px; key ~92 x 67 px
```

Text is sized off the same geometric unit rather than the system font scale,
because the layout is packed to within 3 px of the bezel and a font scale of
1.3 would push keys off the screen.

## Why Compose, and no Material

The UI is ninety keys laid out by arithmetic, not a tree of stock widgets, so
Compose's ability to make layout a function of screen size is worth more here
than any component library. Every key is a `Box` with a `BasicText`, so the app
pulls in `compose-foundation` and nothing else from the design libraries.

The one Wear-specific component is `ScalingLazyColumn`, used for the constants
and history lists. It scales and fades rows toward the top and bottom of the
circle, which is exactly what a long list on a round screen needs.

## The calculator engine

`engine/` is plain Kotlin with no Android dependencies: a tokenizer, a
shunting-yard parser and an RPN evaluator, about 250 lines.

Two deliberate quirks, both there because the app should behave like the
calculator people already trained on:

- **Implicit multiplication binds tighter than `*` and `/`**, so `1/2π` is
  `1/(2π)`, the way a Casio fx-991 reads it.
- **A two-argument function written between its operands** (`10nCr3`) is an
  infix operator sitting between `×`/`÷` and implicit multiplication.

Trigonometry snaps at quadrant boundaries, so `sin 180` returns exactly `0`
rather than `1.22×10⁻¹⁶`, and `tan 90` is an error rather than a huge number.

`Format.kt` also looks for a closed form for each result — a fraction, a
rational multiple of π, or a surd — by continued-fraction approximation of `x`,
`x/π` and `x²`.

### It was ported from a web app, and pinned to it

This started as a [browser calculator](https://calc.bunnyorg.in). Rewriting a
working calculator is mostly an opportunity to introduce subtle arithmetic
differences, so the JavaScript engine was made the specification: 604
expressions were run through it — every function, all three angle modes,
formatting edge cases, exact forms, error messages and factorisations — and its
exact output written to `app/src/test/resources/*.tsv`.

`GoldenTest` replays all of them and demands character-for-character agreement.
Anything a change breaks shows up in `./gradlew test` rather than on your wrist.

Two things needed deliberate work during that port:

- **Number formatting.** The JS relies on `Number.prototype.toPrecision` and
  `String(aNumber)`, neither of which Kotlin has. `Format.kt` rebuilds both with
  `BigDecimal` plus an explicit reimplementation of JavaScript's
  decimal/exponential switchover, so `0.5` prints as `0.5` and not
  `0.5000000000`.
- **JavaScript truthiness in a loop.** `gcd`'s Euclid loop is `while (b)` in JS,
  where a NaN quietly ends the loop. A literal translation to `while (b != 0.0)`
  spins forever — reachable by typing `1E400 gcd 2`. There's a regression test.

## State

`CalcState` holds all behaviour and has no Compose imports, over a `Store`
interface, so the whole interaction model — Casio-style behaviour after `=`,
whole-token backspace, the smart bracket key, the hop back to the number pad
after a function key — is covered by plain JVM tests in `CalcStateTest`.

Wear OS is aggressive about killing background apps, so the in-progress
expression, current page, `Ans`, `M`, angle mode, significant-figure setting and
history all go to `SharedPreferences` as they change. A `force-stop` reopens on
the same page with the same half-typed expression.

## The widgets

Wear OS's Tiles API has no size concept — a tile is a whole screen. One UI adds
one, as manifest meta-data on an ordinary `TileService`:

```xml
<meta-data
    android:name="com.samsung.android.wearable.tiles.LAYOUT_TYPE"
    android:value="2x1" />
```

`2x2` is a full screen and `2x1` is half, stacking two per screen. This is the
same mechanism Samsung's stock Reminder and Calendar widgets use —
`WidgetService2x1` next to `WidgetService` — which is where the attribute name
came from. On a non-Samsung watch the meta-data is ignored and every widget
renders full-screen, which is harmless.

Sizes inside each layout come from the reported `screenWidthDp`, so they hold on
a 40 mm and a 44 mm watch. Two things learned from a real device: a corner
radius of exactly half the side makes the renderer notch a circle, and a pill
much wider than ~84 % of the screen has its own corners clipped by the rounded
widget slot around it.

Each button carries an `EXTRA_PAGE` int. `MainActivity` is `singleTask` and
handles it in both `onCreate` and `onNewIntent`, so tapping a shortcut while the
app is already open jumps straight to that keypad.

To put a widget on screen over ADB, which is how Android Studio does it:

```bash
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation add-tile \
  --ecn component com.bunnypranav.watchcalc/com.bunnypranav.watchcalc.tile.CompactTileService
```

The operation is `add-tile`; the widely-cited `set-tile` is rejected by current
Wear OS.

## Layout of the code

```
engine/                  no Android dependencies, pure Kotlin
  Engine.kt              tokenizer, shunting-yard, RPN evaluator
  Format.kt              number formatting, exact forms, pretty-printing
  NumberTheory.kt        factorisation, Miller-Rabin, Pollard rho
  Consts.kt              the 30 CODATA constants
state/
  Store.kt               persistence interface (+ in-memory impl for tests)
  Prefs.kt               SharedPreferences implementation
  CalcState.kt           all behaviour; no Compose imports
ui/
  Theme.kt               palette and the round-screen unit arithmetic
  Keys.kt                the five keypads, as data
  Keypad.kt              chord-width rows, tap and long-press
  Display.kt             the four display lines, with fit-to-width text
  Sheets.kt              constants and history, on ScalingLazyColumn
tile/
  TileCommon.kt          shared widget pieces
  CalcTileService.kt     full-screen widget
  CompactTileService.kt  half-height widget, all three shortcuts
  PageTileService.kt     half-height widgets, one keypad each
MainActivity.kt          pager, haptics, back handling, widget deep-links
```

## Toolchain

Pinned in `gradle/libs.versions.toml` to AGP 9.3.0, Kotlin 2.2.10 and Gradle
9.5.0. `compileSdk` is 37 because current AndroidX requires it; `targetSdk`
stays at 36 for Wear OS 6.

Note AGP 9 supplies **built-in Kotlin support**: applying
`org.jetbrains.kotlin.android` on top of it fails with a `kotlin` extension
clash, so the app module applies only `com.android.application` and the Compose
compiler plugin.

Don't commit `gradle/gradle-daemon-jvm.properties` if Gradle generates it — it
pins the daemon to a specific JDK with download URLs, which breaks on build
servers that disable toolchain downloads.

## Other Wear OS details

- **Standalone.** Declared `com.google.android.wearable.standalone`, so it
  installs and runs with no phone companion.
- **Back gesture.** A right-swipe closes an open sheet; from the calculator it
  leaves the app, which is the Wear convention. Sheets replace the keypad rather
  than floating over it, so a drag can never leak through to the pager.
- **Haptics.** 10 ms on tap, 25 ms on long-press.
- **Screen timeout.** `KEEP_SCREEN_ON` at the top of `MainActivity.kt` is
  `true`: a calculator you pause over to read a problem is unusable with the
  ~15 s default. Set it to `false` if you'd rather have the battery.
- **No rotary input.** The Galaxy Watch 7 has neither a rotating bezel nor a
  crown. On a Classic or Ultra, add `Modifier.onRotaryScrollEvent { … }` to the
  pager to page by bezel.
