# Changelog

Everything that's changed in TwinnDroid, newest first. You can grab any version's APK from the [Releases](../../releases) page.

## 1.1.5

- **TwinnDroid speaks 23 languages.** Pick one from the new **Settings → Language** menu. That covers the 20 most spoken languages in the world (English, Mandarin, Hindi, Spanish, French, Arabic, Bengali, Portuguese, Russian, Urdu, Indonesian, German, Japanese, Nigerian Pidgin, Egyptian Arabic, Marathi, Telugu, Turkish, Tamil and Cantonese) plus Italian, Vietnamese and Korean. Arabic and Urdu flip the whole layout right to left. It only changes TwinnDroid, not your clones. Some translations might be a bit off, so fixes are very welcome.
- **Update pop-up.** When a new version is out, TwinnDroid tells you when you open it, with the version and what's new. **Update now** opens the latest release here on GitHub. You can turn this off or check by hand in **Settings → Updates**.
- **Official build check.** **Settings → About** now shows whether your copy is an official build signed with my key. TwinnDroid is only officially available from this GitHub page for now, so if it says unofficial, it came from somewhere else.
- **New license.** TwinnDroid is now under the GNU GPL v3 with a few extra terms: forks have to stay open source, keep the credit, say they're modified, and use their own name and icon. 1.1.4 and older stay under MIT.

## 1.1.4

- **Fixed clones getting stuck on "grant file access".** Managers and patchers ask for All files access or Install unknown apps, but their "Open settings" button took you to the original app's page, so the clone could never get it. TwinnDroid now flags any clone that's missing one of these ("Needs … · tap to grant") and lets you grant it in one tap with Shizuku, or opens the clone's own Settings page instead.
- Added **Special access** to each clone's ⋮ menu so you can check and change these any time.
- Tested with Revenge Manager, Shiggy Manager and Morphe.

## 1.1.3

- **Warns you before cloning apps that won't run.** Some apps (Clash Royale and Clash of Clans, for example, plus a lot of banking apps) use anti-tamper protection and close straight away when cloned. There's no way around that without root, so the editor now spots them and tells you up front.
- **Icon editing has its own screen**, and the preview stays pinned at the top while you change the badge, colours and tint.
- **The light theme is easier on the eyes.** The background isn't blinding white anymore, cards stand out, and the animated backgrounds actually show.

## 1.1.2

- **New logo:** the "split droid", one droid head split in two with the clone half lifting off. It's on every app icon, the themed icon, the home header and the README.
- **New cloning animation:** the droid splits apart while your app is copied and turns into two whole droids when the clone is ready.

## 1.1.1

- **Reworked backgrounds**, inspired by [Morphe Patcher](https://github.com/MorpheApp/morphe-manager). Space is now a warp starfield, Circles, Mesh, Shapes, Snow and Lava lamp got redone, and Aurora looks like an actual aurora now.
- **New backgrounds:** Waves, Fireflies, Bokeh and Meteors, plus a **Preview background** button.
- **Customisable home header:** Gradient, Glass, Solid or None, with opacity, real blur, roundness and a floating style, and optional frosted pills behind the icon, title and buttons.
- **5 new presets** (Lavender, Cyberpunk, Nord, Dracula, Matcha), each with a matching app icon, plus a **Match app icon** option.
- Background & effects and Home header got their own menus.

## 1.1.0

- **Animated backgrounds**, theme presets, custom colours, Material You, glass cards and text size.
- **New bottom tab bar**: swipe or tap between Clones, New clone and Extract.
- **Extract APKs** from any installed app as `.apk`, `.apks`, `.xapk`, `.apkm`, or one merged `.apk`.
- **Icon tweaks for clones**: hue, saturation, brightness, contrast, tint, invert, flip and rotate.
- **Animated cloning screen** that shows each step as it happens.
- **Backup & restore** for your clones, signing key and settings.
- Switched to a proper release signing key, and the app is now about 8 MB instead of 26 MB.
- Fixed Discord clones hanging on the loading screen.

## 1.0.0

- First release. Clone installed apps (split APKs included) or `.apk` / `.apks` / `.xapk` / `.apkm` files into separate copies, each with its own login.
- Give each copy its own name, icon (original, number badge or your own picture) and permissions.
- Get told when the original updates, and update the clone in one tap without losing its data.
- Optional prompt-free installs with Shizuku.
