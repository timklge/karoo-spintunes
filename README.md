# Karoo Spintunes Extension

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/timklge/karoo-spintunes/android.yml)](https://github.com/timklge/karoo-spintunes/actions/workflows/android.yml)
[![GitHub Downloads (specific asset, all releases)](https://img.shields.io/github/downloads/timklge/karoo-spintunes/app-release.apk)](https://github.com/timklge/karoo-spintunes/releases)
[![GitHub License](https://img.shields.io/github/license/timklge/karoo-spintunes)](https://github.com/timklge/karoo-spintunes/blob/master/LICENSE)

This extension for Karoo devices adds a graphical data field with Spotify music controls that can be added to data pages.

Compatible with Karoo 2 and Karoo 3 devices.

![Player](player.png)
![Playlists](playlists.png)
![Podcast](podcast.png)
![Login](login.png)

## Features

- Play/pause, skip forward, skip backward, shuffle, repeat, add to queue, view and play playlist, view queue
- Thumbnails for currently played track and playlists
- If Spotify is installed on your Karoo: Volume control, automatic volume control based on ride speed
- To be implemented: search, view album, view artist, podcast selection

## Installation

If you are using a Karoo 3, you can use [Hammerhead's sideloading procedure](https://support.hammerhead.io/hc/en-us/articles/31576497036827-Companion-App-Sideloading) to install the app:

1. Using the browser on your phone, long-press [this download link](https://github.com/timklge/karoo-spintunes/releases/latest/download/app-release.apk) and share it with the Hammerhead Companion app.
2. Your karoo should show an info screen about the app now. Press "Install".
3. Open the app from the main menu and acknowledge the API usage note.
4. Set up your data fields as desired.

If you are using a Karoo 2, you can use manual sideloading:

1. Download the apk from the [releases page](https://github.com/timklge/karoo-spintunes/releases) (or build it from source)
2. Set up your Karoo for sideloading. DC Rainmaker has a great [step-by-step guide](https://www.dcrainmaker.com/2021/02/how-to-sideload-android-apps-on-your-hammerhead-karoo-1-karoo-2.html).
3. Install the app by running `adb install app-release.apk`.
4. Open the app from the main menu and acknowledge the API usage note.
5. Set up your data fields as desired.

There are two ways to use this app:

- Remote control mode: You can install this extension on your Karoo and control Spotify running on your phone (or any other "Spotify Connect"-enabled device like your computer if you are training indoors). The extension will control the Spotify app via the Spotify Web API, so you need an active internet connection (which is provided by the companion app as long as your phone has mobile data reception). Volume control is not available in this mode. Player state is refreshed every 45 seconds when the player data field is on screen and when the current song ends. You can set the player to always refresh every 45 seconds to reduce the delay when opening the player data page, but this might increase battery usage.
- Standalone mode: If you have sideloaded Spotify itself onto your Karoo, the extension can be used to directly control the Spotify app, which works without an internet connection. You can then directly pair your bluetooth headphones to the Karoo and thereby also get the radar alerts, TBT sounds etc the Karoo can play over bluetooth. Please note that the Spotify app can not use the companion app to stream new songs while riding, so you have to download the playlists you want to listen to using the Spotify app itself and let it synchronize them while you are within WiFi range. Player state is refreshed instantly in this mode. Make sure your Karoo has a wifi connection on startup so that the Spotify app allows the spintunes app to connect; during riding, no active internet connection is required.

## Usage

After installing this app on your Karoo and opening it once from the main menu, you can add a player data field to one of your data pages. It is recommended to use a data field that covers a whole page.

## Credits

- Icons are from [boxicons.com](https://boxicons.com) ([MIT-licensed](icon_credits.txt))
- Uses [Spotify Android SDK](https://github.com/spotify/android-sdk) (Apache2-licensed)
- Interfaces with the [Spotify Web API](https://developer.spotify.com/documentation/web-api/)
- Uses [karoo-ext](https://github.com/hammerheadnav/karoo-ext) (Apache2-licensed)
