# Security

## Where to get TwinnDroid

The only official place to download TwinnDroid right now is the [Releases page](../../releases) of this repo. If I publish it somewhere else later (F-Droid, Orion Store, Google Play), I'll list it here and in the README myself.

Copies on other sites weren't uploaded by me and may be old or modified. TwinnDroid installs and signs apps on your phone, so please only get it from a source you trust.

## Checking your copy is official

Every official release is signed with my release key. Its SHA-256 certificate fingerprint is:

```
1C:DA:AA:9B:7F:DB:AA:1A:BE:FD:D6:1B:59:D8:F7:2A:A6:E6:3F:65:6C:29:88:B8:F4:7B:9E:E5:AB:95:EE:FC
```

You can check it two ways:

- **In the app:** Settings → About shows **Official build** with a tick. A copy signed by anyone else shows **Unofficial build** instead.
- **On a computer:** `apksigner verify --print-certs TwinnDroid.apk` and compare the `SHA-256 digest` line with the fingerprint above.

Android also won't let a differently signed copy install over an official one, so if an "update" from somewhere else refuses to install, that's why.

## What TwinnDroid connects to

- **GitHub's public API**, once when the app starts, to see if there's a newer release. You can turn this off in Settings → Updates. Nothing about you or your clones is sent.
- **Shizuku**, locally on your phone, if you've set it up.

There are no analytics, ads or trackers.

## Reporting a problem

If you find a security issue, please don't open a public issue. Use GitHub's [private vulnerability reporting](../../security/advisories/new) instead, and I'll get back to you as soon as I can.
