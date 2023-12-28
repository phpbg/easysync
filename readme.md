# EasySync

Synchronize your phone with your WebDAV server, the easy way. 

## Features
* Synchronize images, videos, audio and downloads
* Synchronize in both directions :
  * Files touched on your phone will be uploaded / deleted on your server
  * Files touched on your server will be uploaded / deleted on your phone
* Basic auth (login with password)
* Secure storage of your credentials
* Preserve timestamps if you use Nextcloud as DAV server

## Get it

### On Google Play
<a href='https://play.google.com/store/apps/details?id=com.phpbg.easysync&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'>
    <img width='240' alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/>
    <img width='90' alt='Get it on Google Play' src='./qr-playstore.png'/>
</a>

### On F-Droid
<a href='https://f-droid.org/packages/com.phpbg.easysync'>
    <img width='240' alt="Get it on F-Droid" src='https://fdroid.gitlab.io/artwork/badge/get-it-on.png'>
    <img width='90' alt='Get it on F-Droid' src='./qr-fdroid.png'/>
</a>

## FAQ

### What is synchronized
* **Images, video, screenshots**
  * If they are displayed in your gallery, they will be synchronized. This includes images and videos in `DCIM/`, `Pictures/`, `Movies/` and `Download/`
  * If they are only available in a specific app but not in gallery, they won't be synchronized
  * Please note that messaging apps (messages, whatsapp, signal, etc.) generally offer you the choice between saving files in your gallery (in such case they will be synchronized) or not
* **Audio, music**
  * All audio files that are visible in `Alarms/`, `Audiobooks/`, `Music/`, `Notifications/`, `Podcasts/`, `Ringtones/` and `Recordings/` will be synchronized
  * Beware that google's own voice recorder stores its files privately and offer its own cloud synchronization. They won't be synchronized by EasySync
* **Downloaded files**
  * All downloaded files in `Download/` will be synchronized, whether they are pdf, epubs, documents, images, etc.

### What is not synchronized
Everything not explicitly stated above is not synchronized. More specifically:
* Applications
* Applications data/state
* Messages
* Contacts
* Games progress
* Wifi or network parameters
* Android settings and phone customization

### Can I choose what is synchronized
No

### I am a nextcloud user. Does this replace nextcloud app?
When you configure EasySync you select a specific folder on your DAV server. This folder will be fully synced with your phone.

* If you want to access files on your DAV server that are **outside** that specific folder, then you still need nextcloud app.
* If you need **per folder synchronization**, then you still need nextcloud app.
* If you want to use all nextcloud features (such as sharing), then you still need nextcloud app.
* Otherwise it is probably not necessary.

### Changes on android device side are not immediately reflected
1. Please be sure to disable battery optimization permission (it will be shown on home screen if required). Disabling battery optimization will not drain your battery. It will just enable immediate sync of file changes.
2. You may also enable `Sync on cellular` and/or `Sync on battery` in `Synchronization settings` (available from home screen).

### Changes on DAV side are not immediately reflected
In order to preserve battery we cannot detect quickly DAV changes. If you need immediate sync use "Sync now" button on home screen.

## Supported Android phones
* From Android 8 to latest releases
* Huawei phones may kill the app in background, be warned. These tips will help you:
  * https://consumer.huawei.com/eg-en/support/article-list/article-detail/en-gb15792041/
  * https://dontkillmyapp.com/huawei

## Supported DAV servers
It should work with any DAV compliant server.
* Nextcloud / owncloud
* *add your server here*

## Sync is slow on Nextcloud
There are several reports of nextcloud DAV to be slow.

You can get **drastic speedup** (almost 10x) when using a device password instead of your user's password:
1. Create a [device password](https://docs.nextcloud.com/server/19/user_manual/session_management.html#managing-devices)
2. Go to DAV Settings in easysync, use your own login, but put the device password instead of your usual user password
3. Enjoy 10x speedup

## Donate
If you like this app consider making [a donation](https://github.com/sponsors/phpbg)

## License
MIT

By using this application, you agree to be bound by [these terms and conditions and legal disclaimer.](./LICENSE)

## Roadmap
 * Fix: handle permissions rejected that cannot be asked anymore (the permission activity doesn't start)
 * Feature: check for low free space on dav or on device
 * Feature: show a notification if too many sync jobs fail (e.g. when remote dav is not reacheable)
 * Optimization: when new remote files are locally added during FullSync, a FileSync is trigged which is useless. See if we can avoid this

## See also

* [NextCloud Android App](https://github.com/nextcloud/android)
* [Round-Sync Android App](https://github.com/newhinton/Round-Sync)
* [Awesome WebDAV: Android Apps](https://github.com/WebDAVDevs/awesome-webdav/tree/main?tab=readme-ov-file#android)
