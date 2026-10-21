# Uzzu IPTV

Uzzu is a TV app for watching your **uzzu.tv** subscription on an Amazon Fire TV Stick or a Google TV.
It shows today's live games and live channels in a layout built for the TV remote.

- **Live Games**: today's games grouped by league (MLB, NHL, NBA, NFL, NCAAF), with start times and
  the current inning, period or quarter. It never shows scores, so nothing gets spoiled.
- **Home or Away broadcast**: you pick which team's feed to watch each time you open a game.
- **Live Channels**: all your uzzu.tv channels, sorted into groups.
- **Quick channel switching**: press OK while you watch to open the channel list. The video keeps
  playing behind it.
- **Automatic updates**: the app tells you when a new version is out and installs it for you.

> **You need a uzzu.tv subscription.** Uzzu is only a player. Your uzzu.tv account provides the
> games and channels. Have your **playlist URL** ready. It's the web link uzzu.tv gives you for
> "M3U" players, and it starts with `https://`.

## Will it work on my TV?

| Device | Works? |
| :--- | :--- |
| Fire TV Stick, Fire TV Stick 4K, Fire TV Cube or a Fire TV smart TV running **Fire OS 7 or newer** | Yes |
| Chromecast with Google TV, Google TV Streamer, or a smart TV with Google TV | Yes |
| Android TV box or TV running **Android 9 or newer** | Yes |
| Fire TV devices on Fire OS 5 or 6 (older models that haven't been updated) | No |
| Fire TV devices running **Vega OS** (for example the Fire TV Stick 4K Select) | No |
| Roku, Apple TV, Samsung (Tizen) or LG (webOS) TVs | No |

**How to check your Fire TV:** go to **Settings → My Fire TV → About** and look at **Software Version**.
It needs to say Fire OS 7 or higher.

## Install on a Fire TV Stick

This takes about 10 minutes. Uzzu isn't in the Amazon Appstore, so you install it with a free app called
**Downloader**.

### 1. Install Downloader

1. On the Fire TV home screen, go to **Find → Search** (the magnifying glass).
2. Type **Downloader** and pick the app with the orange icon (made by AFTVnews).
3. Select **Get** or **Download**, then wait for it to install.

### 2. Turn on Developer Options

1. Go to **Settings → My Fire TV → About**.
2. Highlight your device name (the first line, such as *Fire TV Stick*) and press **OK** on the remote
   **7 times**. A message says *"No need, you are already a developer"* or *"You are now a developer."*
3. Press **Back** once. **Developer Options** now shows up in the **My Fire TV** menu.

### 3. Let Downloader install apps

1. Go to **Settings → My Fire TV → Developer Options**.
2. Open **Install unknown apps** and switch **Downloader** to **ON**.
   On some older Fire TVs this setting is called **Apps from Unknown Sources**. Turn it **ON**.

### 4. Download and install Uzzu

1. Open **Downloader**. Allow it to access your files if it asks.
2. Select the address box and type this, exactly as shown:

   ```
   github.com/BrandonMiller18/uzzu-iptv/releases/latest
   ```

3. Select **Go**. The download page for the newest version opens.
4. Scroll down to the **Assets** section. Select the file whose name **ends in `.apk`**
   (it starts with `uzzu-iptv-`). Skip the files that end in `.sha256` or `.txt`.
5. When the download finishes, select **Install**.
   - If the **Install** button isn't highlighted, press **Down** (then **Right**) on the remote until
     it is, then press **OK**.
6. Select **Done**. Downloader offers to delete the downloaded file. Select **Delete** twice to free up
   space. The app stays installed.

Go to [First-time setup](#first-time-setup).

## Install on a Google TV

This takes about 10 minutes. Uzzu isn't in the Google Play Store, so you install it with a free app
called **Downloader**.

### 1. Install Downloader

1. On the home screen, go to **Search** and type **Downloader**.
2. Pick **Downloader by AFTVnews** (orange icon) and select **Install**.

### 2. Download and install Uzzu

1. Open **Downloader**. Allow it to access your files if it asks.
2. Select the address box and type this, exactly as shown:

   ```
   github.com/BrandonMiller18/uzzu-iptv/releases/latest
   ```

3. Select **Go**. The download page for the newest version opens.
4. Scroll down to the **Assets** section. Select the file whose name **ends in `.apk`**
   (it starts with `uzzu-iptv-`). Skip the files that end in `.sha256` or `.txt`.
5. The TV says it isn't allowed to install apps from this source. Select **Settings** on that message,
   switch **Downloader** to **ON**, then press **Back**.
   - If you don't see that message, go to **Settings → Apps → Security & restrictions → Unknown sources**
     and switch **Downloader** to **ON**. Then go back to Downloader and select the `.apk` file again.
6. Select **Install**, then **Done**. You can delete the downloaded file when Downloader asks. The app
   stays installed.

## First-time setup

1. Open **Uzzu**.
   - **Fire TV:** go to **Settings → Applications → Manage Installed Applications → Uzzu → Launch application**,
     or open the **Apps** icon (three squares) on the home screen.
   - **Google TV:** go to **Apps** on the home screen and scroll to **Your apps**.
2. On the **Playlist** screen, press **OK** to open the keyboard and type your uzzu.tv playlist URL.
3. Select **Save**. Uzzu checks the link and then opens the home screen.

Your playlist URL contains your uzzu.tv login, so treat it like a password. Uzzu stores it on your TV
only.

**Tip:** to put Uzzu on your home screen, highlight its icon, press the **Menu** button (☰) on a Fire TV
remote or hold **OK** on a Google TV remote, and choose **Move** or **Add to favorites**.

## Using Uzzu

| Remote button | What it does |
| :--- | :--- |
| Arrows and **OK** | Move around and pick a game or channel |
| **OK** while watching | Opens the channel list on top of the video |
| **Channel up / down** (if your remote has them) | Next or previous channel in the same group |
| **Back** while watching | Jumps back to the last channel you watched |
| Hold **Back** while watching | Leaves the video and goes back to the menus |

The home screen also has **Refresh** (reloads today's games) and **Settings**. Open **Settings** to
change your playlist URL or check for updates.

## Updating

You only install Downloader and Uzzu once. After that, Uzzu checks for a new version each day.

1. When a new version is out, the home screen says **Update available**. Select it, then select
   **Install update**.
2. The first time you do this, your TV asks you to allow Uzzu to **install unknown apps**. Switch
   **Uzzu** to **ON**, press **Back**, and select **Install update** again.
3. Select **Install** on the system screen. On a Fire TV, press **Down** (then **Right**) until **Install**
   is highlighted, then press **OK**.

Your playlist URL is kept when you update.

## Something not working?

- **"Could not load the playlist"**: check that your TV is online and that your uzzu.tv subscription is
  active. Then select **Retry**, or go to **Settings → Playlist URL** and enter the link again.
- **"Can't play this stream"**: the channel may be off the air right now. Select **Retry** or try another
  channel.
- **"App not installed"** during an update: uninstall Uzzu, then install it again with the steps above.
  You'll need to enter your playlist URL again.
- **Can't find Developer Options on a Fire TV**: repeat step 2 of the Fire TV install. Press OK on the
  device name 7 times in a row without pausing.

---

Uzzu is a personal project and isn't made by, or affiliated with, uzzu.tv, Amazon or Google.
Fonts: Roboto and Roboto Mono, SIL Open Font License 1.1.
Developers: see [docs/index.md](docs/index.md) and [docs/guides/releasing.md](docs/guides/releasing.md).
