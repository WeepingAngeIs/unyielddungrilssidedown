# Neon Nook

A spacious VRChat arcade lounge inspired by AdVenture Capitalist's buy / earn / reinvest loop. Selected furniture from the supplied Gaming Room package is reused in an original 24 x 22 metre hangout.

## Open

1. Install Unity **2022.3.22f1** with Android build support through VRChat Creator Companion.
2. Add this existing project to Creator Companion and restore pinned VRChat Worlds/Base **3.10.6** packages. Alternatively run `./Setup-Project.ps1` in PowerShell.
3. Open `Assets/IdleLounge/Scenes/NeonNook.unity`, then Play for ClientSim.
4. **Neon Nook > Build preview world** regenerates the scene. Save manual changes in a separate scene first.

Versions match official VRChat requirements checked November 1, 2026. Do not independently upgrade this project to Unity 6.

## Features

- Personal idle arcade: automatic income, tapping, three businesses, efficiency boosts, and confirmed reopen/prestige at 100K earned.
- Separate wallets per visitor. **Current visit only**: no persistent saving or offline earnings.
- Twelve VRChat seats, broad walking routes, a quiet corner and skyline outlook.
- Shared host-controlled video; personal volume, mute and retry. No automatic external media playback. Direct HTTPS MP4 is the most predictable test source; codec, URL permissions and platform support affect playback.
- One synchronized pickup ball and a return button that ignores a held ball.
- Lightweight opaque shaders, no realtime lights, mirrors or post-processing; 1024 x 576 video render target.

## VRChat testing and upload

Sign into the SDK, run Builder checks, and Build & Test on Windows. For cross-platform publication, upload Windows first, switch to Android, and upload with the **same blueprint ID**. There is no assigned world ID or account credential in this project.

Before publishing, use an actual PC + Quest session to test independent wallets, seating exits, video late joins, host departure, ball ownership transfers, and local volume. Measure headset performance with avatars present. Desktop renders and Android compilation cannot establish headset frame rate or networking behavior.

## Source assets

`Assets/Gaming Room` preserves user-supplied meshes, textures and source materials. Its readme is a folder guide; no general redistribution license was found. Keep source private and verify the creator's terms before redistributing assets or publishing. Bundled lilToon scripts/shaders and baked lighting were excluded. Used materials are converted to the lounge's lightweight shader.

Code and generated scene: `Assets/IdleLounge`. SDK packages restore from official URLs; Unity caches and builds are ignored.

See `VALIDATION.md` for completed checks and remaining hardware tests.

- [Supported Unity version](https://creators.vrchat.com/sdk/upgrade/current-unity-version/)
- [Video players](https://creators.vrchat.com/worlds/udon/video-players/)
- [Android limitations](https://creators.vrchat.com/platforms/android/quest-content-limitations/)

## Preview images and developer checks

![Neon Nook overview](Docs/Preview/neon-nook-overview.png)

Generated previews and bundles default to the ignored `Builds` folder. Set `NEON_NOOK_OUTPUT_DIR` to choose another output folder. Editor menu actions rebuild or validate the scene. `ArcadeEconomyChecks.Run` checks purchase/prestige rules. `RuntimeSmoke.Begin` runs real Udon events under ClientSim (batch mode, without `-quit`, exits automatically). `WorldBuildChecks.Windows` and `.Android` export VRChat bundles for the selected target without uploading. Export both targets from the same saved scene; do not regenerate between platform exports.
