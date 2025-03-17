# ClipShare Android Client

### Client application of ClipShare for Android devices.

![Build and Test](https://github.com/thevindu-w/clip_share_client/actions/workflows/build-test.yml/badge.svg?branch=master)
![Check Style](https://github.com/thevindu-w/clip_share_client/actions/workflows/check-style.yml/badge.svg?branch=master)
![Last commit](https://img.shields.io/github/last-commit/thevindu-w/clip_share_client.svg?color=yellow)
![License](https://img.shields.io/github/license/thevindu-w/clip_share_client.svg?color=blue)

[![Latest release](https://img.shields.io/github/v/release/thevindu-w/clip_share_client?color=purple)](https://github.com/thevindu-w/clip_share_client/releases)
[![Stars](https://img.shields.io/github/stars/thevindu-w/clip_share_client)](https://github.com/thevindu-w/clip_share_client/stargazers)

Share the clipboard between your phone and desktop. Share files and screenshots securely.
<br>
ClipShare is a lightweight, cross-platform app for sharing copied text, files, and screenshots between an Android mobile
and a desktop.

## Download

<table>
    <tr>
        <th style="text-align:center">Server</th>
        <th style="text-align:center">Client</th>
    </tr>
    <tr>
        <td align="center">
            <a href="https://github.com/thevindu-w/clip_share_server/releases"><img src="https://raw.githubusercontent.com/thevindu-w/clip_share_client/master/fastlane/metadata/android/en-US/images/icon.png" alt="Get it on GitHub" height="100"/></a><br>
            Download the server from <a href="https://github.com/thevindu-w/clip_share_server/releases">Releases</a>.
        </td>
        <td align="center">
            <a href="https://apt.izzysoft.de/fdroid/index/apk/com.tw.clipshare"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="100"/></a><br>
            Download the client app
            from <a href="https://apt.izzysoft.de/fdroid/index/apk/com.tw.clipshare">apt.izzysoft.de/fdroid/index/apk/com.tw.clipshare</a>.<br>
            or from <a href="https://github.com/thevindu-w/clip_share_client/releases">GitHub Releases</a>.
        </td>
    </tr>
</table>

<br>

This is the Android client of ClipShare. You will need the server on your desktop to connect with it.
ClipShare is lightweight and easy to use. Run the server on your Windows, macOS, or Linux machine to use the ClipShare
app. You can find more information on running the server on Windows, macOS, or Linux at
[github.com/thevindu-w/clip_share_server](https://github.com/thevindu-w/clip_share_server#how-to-use).

## How to use

### Main screen

<p align="center">
<img src="https://raw.githubusercontent.com/thevindu-w/clip_share_client/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" alt="help image" height="500">
&nbsp;&nbsp;&nbsp;&nbsp;
<img src="https://raw.githubusercontent.com/thevindu-w/clip_share_client/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" alt="dark theme" height="500">
&nbsp;&nbsp;&nbsp;&nbsp;
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
    - Select the file(s) to send.
    - The files are now sent to the laptop.

  _Method 2 Steps_:
    - Share any file(s) with ClipShare from any other app.
    - Press the red colored _FILE_ button.
    - The files are now sent to the laptop.


- **Send folder**: To send a folder from the phone to the server (ex: laptop).

  _Steps_:
    - Press the red colored _FOLDER_ button.
    - Select the folder to send.
    - The folder is now sent to the laptop.

&emsp; Note: Sending folders requires a server version 2.x or later.

- **Get image/screenshot**: To get a copied image or screenshot from the server (ex: laptop) to the phone.

  _Steps_:
    - Optional: Copy an image (not an image file) to the clipboard on the laptop.
    - Press the green colored _IMAGE_ button.
    - If there is an image copied on the laptop, it will be received and saved on the phone.
      Otherwise, a screenshot of the laptop will be received and saved on the phone.
      <br><br>
      Long pressing the _IMAGE_ button gives more options.
    - Get only a copied image without a screenshot.
    - Get only a screenshot, even when there is an image, copied to the clipboard of the laptop.
    - Select the display to get the screenshot.

&emsp; Note: These options require a server version 3.x or later to work.

- **Scan**: To scan the network to find available servers in the network.

  If there is any server in the network, scanning will find that. If the scan finds only one server, its address will be
  placed in the _Server_ address input area. If the scan finds many servers, a popup will appear to select any server
  out of them, and the selected address will be placed in the _Server_ address input area.

### Settings

#### Auto send

- **Auto send text:** When this setting is enabled, ClipShare will automatically send the text shared with it from other
  apps (ex: when sharing a link from the web browser) without requiring to tap the _Send_ button.
- **Auto send files:** When this setting is enabled, ClipShare will automatically send the files shared with it
  (ex: sharing documents or photos from the file manager or gallery) without requiring to tap the _Send File_ button.
- **Auto send to:** This is the list of trusted servers to auto-send. Add the IP address of each server using the `+`
  button. Setting the address to `*` will allow auto-sending to any server. Tap on the address to edit it, and tap on
  the `X` button to remove the entry from the list.

#### Saved addresses

- **Save addresses:** When this setting is enabled, ClipShare will save the server addresses used by the app.
- **Saved servers:** This is the list of automatically saved server addresses. You can manually add an IP address to
  the list using the `+` button. Tap on any address to edit it, and tap on the `X` button to remove it from the list.

#### Secure mode

- **CA Certificate:** This is the self-signed TLS certificate of the certification authority that signed the client and
  server's TLS certificates. Select the certificate file using the _Browse_ button.
- **Client Certificate:** This is the TLS key and certificate _p12_ or _pfx_ file of the client. Before selecting the
  file using the _Browse_ button, you must enter the password for the _pfx_ file.
  The password should have less than 256 characters.
- **Trusted servers:** This is the list of trusted servers to which the client is allowed to connect. Add the _Common
  Name_ of each server using the `+` button. Tap on the name to edit it, and tap on the `X` button to remove the entry
  from the list. The client app will refuse to connect to servers not having TLS certificates with their _Common Name_
  listed under this list when secure mode is enabled.
- **Secure mode:** When this setting is enabled, the connections with the server (ex: your laptop) are secured with TLS
  encryption. Enabling this setting prevents others on the same network from spying on or modifying the data you share
  with your laptop. To enable this setting, you need to select the CA certificate and client TLS certificate and add at
  least one trusted server. Additionally, you need to configure the server to create and use a server certificate.
  Refer to the
  [TLS certificates](https://github.com/thevindu-w/clip_share_server#create-ssltls-certificate-and-key-files) and
  [Configuration](https://github.com/thevindu-w/clip_share_server#configuration) sections of the
  [ClipShare server](https://github.com/thevindu-w/clip_share_server) for more information.

#### Other settings

- **Close app if idle:** When this setting is enabled, the ClipShare app will automatically close if it is kept idle
  without interacting with it for some time. This time duration can be changed from the _Auto-close delay_ setting
  described below.
- **Auto-close delay:** This is the time duration, in seconds, for which the app is kept idle before automatically
  closing. This setting is visible only when the _Close app if idle_ setting is enabled.
- **Vibration alerts:** When this setting is enabled, the phone will give a short vibration pulse after each successful
  operation (ex: _Get Files_) as feedback to the user.

#### Ports

- **Port:** This is the port on which the server on your laptop listens for plaintext TCP connections. The default value
  for this port is `4337`. If a different port is assigned for the server according to the
  [server configuration](https://github.com/thevindu-w/clip_share_server#configuration), enter the same port here.
- **Secure Port:** This is the port on which the server on your laptop listens for TLS-encrypted connections. The
  default value for this port is `4338`. If a different port is assigned for the server according to the
  [server configuration](https://github.com/thevindu-w/clip_share_server#configuration), enter the same port here.
- **UDP Port:** This is the port on which the server on your laptop listens for UDP scanning requests. The default value
  for this port is `4337`. If a different port is assigned for the server according to the
  [server configuration](https://github.com/thevindu-w/clip_share_server#configuration), enter the same port here.

#### Import/Export settings

- **Import settings:** Use this to import settings from a _JSON_ file exported before. Note that the current settings
  will be discarded when importing settings from a file.
- **Export settings:** Use this to export settings to a _JSON_ file that can be imported later. Settings can be exported
  to preserve settings after reinstalling the app or moving app settings to another device.