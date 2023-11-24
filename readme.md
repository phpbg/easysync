# easysync

Synchronize your phone with your DAV server, the easy way. 

## Features
* Synchronize images, videos, audio and downloads
* Synchronize in both directions :
  * Files touched on your phone will be uploaded / deleted on your server
  * Files touched on your server will be uploaded / deleted on your phone
* Basic auth (login with password)
* Secure storage of your credentials
* Preserve timestamps if you use Nextcloud as DAV server

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

You can get drastic speedup (almost 10x) when using a device password instead of your user's password:
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
