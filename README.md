![Build](https://github.com/thevindu-w/clip_share_client/actions/workflows/build.yml/badge.svg?branch=master)
![Check Style](https://github.com/thevindu-w/clip_share_client/actions/workflows/check_style.yml/badge.svg?branch=master)
![Last commit](https://img.shields.io/github/last-commit/thevindu-w/clip_share_client.svg?color=yellow)
![License](https://img.shields.io/github/license/thevindu-w/clip_share_client.svg?color=blue)

# ClipShare Android Client

### This is the client application of ClipShare for android devices.

Share the clipboard between your phone and desktop. Share files and screenshots securely.
<br>
ClipShare is a lightweight, cross-platform app for sharing copied text, files, and screenshots between an Android mobile
and a desktop.

## Download

<table>
    <tr>
        <th>Server</th>
        <th>Client</th>
    </tr>
    <tr>
        <td>
            <a href="https://github.com/thevindu-w/clip_share_server/releases"><img src="https://raw.githubusercontent.com/thevindu-w/clip_share_client/master/fastlane/metadata/android/en-US/images/icon.png"
               alt="Get it on GitHub" height="100"/></a><br>
            (Download the server from <a href="https://github.com/thevindu-w/clip_share_server/releases">Releases</a>.)
        </td>
        <td>
            <a href="https://apt.izzysoft.de/fdroid/index/apk/com.tw.clipshare"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
               alt="Get it on IzzyOnDroid" height="100"/></a><br>
            (Download the client app
            from <a href="https://apt.izzysoft.de/fdroid/index/apk/com.tw.clipshare">
            apt.izzysoft.de/fdroid/index/apk/com.tw.clipshare</a>.<br>
            or from <a href="https://github.com/thevindu-w/clip_share_client/releases">GitHub Releases</a>.)
        </td>
    </tr>
</table>

<br>

This repository is the Android client of ClipShare. You will need the server on your desktop to connect with it.
ClipShare is lightweight and easy to use. Run the server on your Windows, macOS, or Linux machine to use the ClipShare
app. You can find more information on running the server on Windows, macOS, or Linux at
[github.com/thevindu-w/clip_share_server](https://github.com/thevindu-w/clip_share_server#how-to-use).

## How to use

<p align="center">
<img src="https://raw.githubusercontent.com/thevindu-w/clip_share_client/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg"
alt="help image" height="500">
</p>

- **Get text**: To get copied text from the server (ex: laptop) to the phone.

  _Steps_:
  - Copy any text on the laptop.
  - Press the green colored _GET_ button.
  - Now, the copied text is received and copied to the phone. Paste it anywhere on the phone (possibly in a different
    app).
  

- **Send text**: To send copied text from the phone to the server (ex: laptop).

  _Steps_:
  - Copy any text on the phone (possibly in a different app).
  - Press the red colored _SEND_ button.
  - Now, the copied text is sent and copied to the laptop. Paste it anywhere on the laptop.


- **Get files**: To get copied files from the server (ex: laptop) to the phone.

  _Steps_:
  - Copy any file(s) and/or folder(s) on the laptop.
  - Press the green colored _FILE_ button.
  - The copied files and folders are now received and saved on the phone.


- **Send files**: To send files from the phone to the server (ex: laptop).

  _Method 1 Steps_:
  - Press the red colored _FILE_ button.
  - Select the file(s) to send
  - The files are now sent to the laptop.
  
  _Method 2 Steps_:
  - Share any file(s) with ClipShare from any other app.
  - Press the red colored _FILE_ button.
  - The files are now sent to the laptop.


- **Get image/screenshot**: To get a copied image or screenshot from the server (ex: laptop) to the phone.

  _Steps_:
    - Optional: Copy an image (not an image file) to the clipboard on the laptop.
    - Press the green colored _IMAGE_ button.
    - If there is an image copied on the laptop, it will be received and saved on the phone.
      Otherwise, a screenshot of the laptop will be received and saved on the phone.


- **Scan**: To scan the network to find available servers in the network.

  If there is any server in the network, scanning will find that. If the scan finds only one server, its address will be
  placed in the _Server_ address input area. If the scan finds many servers, a popup will appear to select any server
  out of them, and the selected address will be placed in the _Server_ address input area.
