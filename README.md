<div align="center">

<img src="Logo.png" alt="BitChord app icon" width="140" />

# BitChord, customized

### A personalized fork of the BitChord YouTube Music client

[![Latest release](https://img.shields.io/github/v/release/steeben/BitChord?style=for-the-badge&labelColor=0d1117)](https://github.com/steeben/BitChord/releases)
[![License](https://img.shields.io/github/license/steeben/BitChord?style=for-the-badge&labelColor=0d1117)](LICENSE)

[**Screenshots**](#screenshots) · [**What's different**](#whats-different-in-this-fork) · [**Install**](#install-and-updates) · [**Credits**](#credits)

</div>

> [!NOTE]
> This is a fork of [BitChord](https://github.com/kushagrasinghx/BitChord) by [kushagrasinghx](https://github.com/kushagrasinghx). Everything BitChord does (Hi-Res audio, crossfade, word-synced lyrics, Spotify, scrobbling and the rest) is still here. This page covers only what the fork adds or changes. For the full upstream feature list, see the [original README](https://github.com/kushagrasinghx/BitChord#features).

> [!IMPORTANT]
> Not affiliated with, endorsed by, or connected to YouTube or Google in any way. Use it at your own discretion.

---

## Screenshots

<div align="center">
<table>
  <tr>
    <td align="center"><img src="docs/screenshots/home.jpg" width="220" /><br/><sub>Speed dial and a custom nav bar</sub></td>
    <td align="center"><img src="docs/screenshots/library.jpg" width="220" /><br/><sub>YouTube Music library</sub></td>
    <td align="center"><img src="docs/screenshots/player.jpg" width="220" /><br/><sub>Now playing</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/canvas.jpg" width="220" /><br/><sub>Compact controls over Spotify Canvas</sub></td>
    <td align="center"><img src="docs/screenshots/queue.jpg" width="220" /><br/><sub>Queue with compact controls</sub></td>
    <td align="center"><img src="docs/screenshots/lyrics.jpg" width="220" /><br/><sub>Lyrics</sub></td>
  </tr>
</table>
</div>

---

## What's different in this fork

### Your own music
- **Play your uploaded YouTube Music songs.** Tracks you uploaded to your YouTube Music library now play like any other song.
- **A "YouTube Music" category in the Library tab** that shows your whole YouTube Music library (Songs, Artists, Albums), cached so it doesn't reload every time you open it.
- **Pick a default library.** Long-press a library, or choose it in Settings, and tapping Library on the nav bar goes straight there. You can also choose which page it opens on. The back arrow in the top-left returns to the main library page.
- **List or grid per library tab**, and a **Last played** sort.

### Home
- **Speed dial.** Pin songs, albums, playlists and more to a speed dial on Home, as a 3 × 4 grid (or 3 × 3, in Settings) that fits above the miniplayer. Hold a pin and drag it to reorder, like a phone's home screen, and hold at the edge to move it to another page. Hold the "Speed Dial" heading to reorder pins from a list.
- **Reorder the Home layout** and **hide sections** you don't use.
- **A logo made of squares.** It stays pinned in the top left as you scroll, and pulling down to refresh lights the squares up in a spin, then settles them back into the logo.

### Navigation
- **Customize the nav bar.** Reorder the tabs, or swap one out for a **Playlists** shortcut.
- **Keep the miniplayer and navigation bar expanded** instead of collapsing as you scroll.
- Choose which screen the app **starts on**, and whether the miniplayer is **circular or a rounded square**.

### Appearance
- **Ambient background.** Soft, slowly drifting colour blobs sit behind the app, with frosted surfaces on top. The colour can **match the current song's album art**, or stay on a fixed colour (green in Spotify libraries, red in YouTube Music).

### Player
- **Spotify Canvas with controls that stay.** When a Canvas video is playing, the player controls shrink and stay visible instead of fading out. You can turn this off again in the Canvas settings.
- **A cleaner queue.** The redundant entries are gone: the current song is at the top and the next songs follow below. Scroll up to see what you already played. The player controls shrink to make room for more of the queue, and shrink the same way on the lyrics screen.
- **Gestures.** Swipe up on the controls area to open the queue, and swipe down to close it. Swipe down on the player screen to minimize it. The system back gesture plays the same slide-down.

### Under the hood
- Album art loads a small copy first, and prefetches the next tracks, so the player and its blur keep up on mobile data.
- The Home feed and library pages are cached on disk and in memory for a faster start.
- Builds are optimized for speed.

---

## Install and updates

Download the latest APK from the [Releases](https://github.com/steeben/BitChord/releases) page. Sideloading requires enabling "Install unknown apps" for whichever app you download it with.

> [!TIP]
> I suggest using [ObtainX](https://github.com/bikram-agarwal/ObtainX) ([F-Droid](https://f-droid.org/packages/dev.bikram.obtainx/)) for automatic updates. Add this repository to it and it will pick up new releases as they are published.

The app installs as **BitChord Dial**, a separate app from the official BitChord, so the two can sit side by side and neither touches the other's data. You'll need to sign in again the first time.

Each release is signed with the same key, so a new version installs over the old one and keeps your sign-in and settings. The app also checks this repository for updates when it launches.

To build it yourself, see [CONTRIBUTING.md](CONTRIBUTING.md) and, for the desktop app, [DESKTOP.md](DESKTOP.md).

---

## Credits

This project exists because of [BitChord](https://github.com/kushagrasinghx/BitChord) and its contributors. The lyrics providers, audio sources and everything else it builds on are credited in the [upstream README](https://github.com/kushagrasinghx/BitChord#features). The uploaded-songs fix draws on how [Metrolist](https://github.com/MetrolistGroup/Metrolist) and its InnerTube library handle uploads.

If you like BitChord, consider [supporting its author](https://github.com/kushagrasinghx/BitChord#support).

## Disclaimer

BitChord is an independent, community-driven third-party audio player and client. It is **not** associated with Google LLC, YouTube Music, Deezer, Telegram, or any of their parent companies. It does not host, upload, or store copyrighted music files, and it is provided for personal, educational and fair-use purposes. You are responsible for ensuring your use follows your local copyright laws and the YouTube Terms of Service.

## License

Licensed under the **GNU General Public License v3.0**. See [LICENSE](LICENSE).
