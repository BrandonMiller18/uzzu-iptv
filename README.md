<div align="center">

# Uzzu IPTV

**Watch your uzzu.tv live games and channels on a Fire TV Stick or Google TV.**

[Fire TV Stick](#-fire-tv-stick) · [Google TV](#-google-tv) · [Using Uzzu](#-using-uzzu) · [Updates](#-updates) · [Help](#-help)

</div>

---

## What is Uzzu?

Uzzu is a TV app that plays your **uzzu.tv** subscription. It's built for the TV remote.

- 🏟️ **Live Games** grouped by league (MLB, NHL, NBA, NFL, NCAAF), with start times and the current
  inning, period or quarter
- 🚫 **No scores anywhere**, so nothing gets spoiled
- 🏠 **Home or Away broadcast**: you choose which team's feed to watch
- 📺 **Live Channels** sorted into groups
- ⚡ **Fast channel switching** while the video keeps playing
- 🔄 **Automatic updates** from inside the app

## Before you start

You need:

- ✅ An active **uzzu.tv subscription**
- ✅ Your **uzzu.tv playlist URL**: the `https://` link uzzu.tv gives you for M3U players
- ✅ A **compatible TV device** (see below)
- ✅ About **10 minutes**

> [!IMPORTANT]
> Your playlist URL contains your uzzu.tv login. Treat it like a password and don't share it.

### Compatible devices

| ✅ Works | ❌ Doesn't work |
| :--- | :--- |
| Fire TV Stick, Fire TV Stick 4K, Fire TV Cube, Fire TV smart TVs on **Fire OS 7 or newer** | Fire TV devices on Fire OS 5 or 6 |
| Chromecast with Google TV, Google TV Streamer, smart TVs with Google TV | Fire TV devices on **Vega OS** (such as Fire TV Stick 4K Select) |
| Android TV boxes and TVs on **Android 9 or newer** | Roku, Apple TV, Samsung and LG TVs |

> [!TIP]
> **Check your Fire TV version:** go to **Settings → My Fire TV → About** and look at **Software Version**.

---

## 🔥 Fire TV Stick

Uzzu isn't in the Amazon Appstore. You install it with a free app called **Downloader**.

### Step 1: Install Downloader

- From the home screen, open **Find → Search** (magnifying glass)
- Type **Downloader**
- Pick **Downloader by AFTVnews** (orange icon)
- Select **Get** and wait for it to install

### Step 2: Turn on Developer Options

- Go to **Settings → My Fire TV → About**
- Highlight your device name (the first line, such as *Fire TV Stick*)
- Press **OK** on the remote **7 times** in a row
- Wait for the message *"You are now a developer"*
- Press **Back**. **Developer Options** now appears in the **My Fire TV** menu

> [!NOTE]
> If the message says *"No need, you are already a developer"*, Developer Options is already on. Move to Step 3.

### Step 3: Allow Downloader to install apps

- Go to **Settings → My Fire TV → Developer Options**
- Open **Install unknown apps**
- Switch **Downloader** to **ON**

> [!NOTE]
> On older Fire TVs this setting is called **Apps from Unknown Sources**. Switch it **ON**.

### Step 4: Download Uzzu

- Open **Downloader**
- Select **Allow** if it asks for access to your files
- Select the address box and type:

  ```
  github.com/BrandonMiller18/uzzu-iptv/releases/latest
  ```

- Select **Go**
- Scroll down to **Assets**
- Select the file that **starts with `uzzu-iptv-` and ends in `.apk`**
- Skip the files ending in `.sha256` or `.txt`

### Step 5: Install Uzzu

- When the download finishes, select **Install**
- If **Install** isn't highlighted, press **Down** (then **Right**) until it is, then press **OK**
- Select **Done**
- When Downloader asks, select **Delete** twice to remove the download file. The app stays installed

### Step 6: Open Uzzu and add your playlist

- Go to the **Apps** icon (three squares) on the home screen and open **Uzzu**
- Press **OK** on the **Playlist URL** box to open the keyboard
- Type your uzzu.tv playlist URL
- Select **Save**
- Uzzu checks the link and opens the home screen. You're done 🎉

> [!TIP]
> To pin Uzzu to your home screen, highlight it in **Apps**, press the **Menu** button (☰), and choose **Move to front**.

---

## 📺 Google TV

Uzzu isn't in the Google Play Store. You install it with a free app called **Downloader**.

### Step 1: Install Downloader

- From the home screen, open **Search**
- Type **Downloader**
- Pick **Downloader by AFTVnews** (orange icon)
- Select **Install** and wait for it to finish

### Step 2: Download Uzzu

- Open **Downloader**
- Select **Allow** if it asks for access to your files
- Select the address box and type:

  ```
  github.com/BrandonMiller18/uzzu-iptv/releases/latest
  ```

- Select **Go**
- Scroll down to **Assets**
- Select the file that **starts with `uzzu-iptv-` and ends in `.apk`**
- Skip the files ending in `.sha256` or `.txt`

### Step 3: Allow Downloader to install apps

- A message says the TV can't install apps from this source
- Select **Settings** on that message
- Switch **Downloader** to **ON**
- Press **Back** to return to Downloader

> [!NOTE]
> No message? Go to **Settings → Apps → Security & restrictions → Unknown sources**, switch **Downloader**
> to **ON**, then go back to Downloader and select the `.apk` file again.

### Step 4: Install Uzzu

- Select **Install**
- Select **Done**
- When Downloader asks, select **Delete** to remove the download file. The app stays installed

### Step 5: Open Uzzu and add your playlist

- Go to **Apps** on the home screen and scroll to **Your apps**
- Open **Uzzu**
- Press **OK** on the **Playlist URL** box to open the keyboard
- Type your uzzu.tv playlist URL
- Select **Save**
- Uzzu checks the link and opens the home screen. You're done 🎉

> [!TIP]
> To add Uzzu to your home screen, highlight it in **Your apps**, hold **OK**, and choose **Add to favorites**.

---

## 🎮 Using Uzzu

| Remote button | What it does |
| :--- | :--- |
| **Arrows** + **OK** | Move around and pick a game or channel |
| **OK** while watching | Opens the channel list over the video |
| **Channel ▲ / ▼** | Next or previous channel in the same group (if your remote has them) |
| **Back** while watching | Jumps to the last channel you watched |
| Hold **Back** while watching | Leaves the video and returns to the menus |

- **Refresh** on the home screen reloads today's games
- **Settings** on the home screen lets you change your playlist URL or check for updates

---

## 🔄 Updates

You only install Downloader and Uzzu once. Uzzu checks for new versions every day.

- When an update is ready, the home screen shows **Update available**
- Select it, then select **Install update**
- **First update only:** your TV asks you to let Uzzu **install unknown apps**
  - Switch **Uzzu** to **ON**
  - Press **Back**
  - Select **Install update** again
- Select **Install** on the system screen
  - **Fire TV:** press **Down** (then **Right**) until **Install** is highlighted, then press **OK**

Your playlist URL is kept when you update.

---

## 🛟 Help

**"Could not load the playlist"**
- Check that your TV is connected to the internet
- Check that your uzzu.tv subscription is active
- Select **Retry**
- Still failing? Go to **Settings → Playlist URL** and enter the link again

**"Can't play this stream"**
- The channel may be off the air right now
- Select **Retry** or pick another channel

**"App not installed" during an update**
- Uninstall Uzzu
- Install it again using the steps for your device
- Enter your playlist URL again

**Can't find Developer Options on Fire TV**
- Repeat [Step 2](#step-2-turn-on-developer-options)
- Press **OK** on the device name 7 times quickly, without pausing

---

<sub>Uzzu is a personal project. It isn't made by or affiliated with uzzu.tv, Amazon or Google.
Fonts: Roboto and Roboto Mono, SIL Open Font License 1.1.</sub>
