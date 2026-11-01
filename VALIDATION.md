# Validation — Neon Nook preview

Checked November 1, 2026 with Unity 2022.3.22f1 and VRChat SDK 3.10.6.

## Completed

- Unity C# and UdonSharp compilation.
- Editor behavior checks against the real economy component: affordability, escalating prices, income contributions, tap throttling, prestige eligibility, confirmation/cancellation/reset, and caps.
- Real Udon VM / ClientSim Play Mode smoke test: idle wallet reached 32.525 credits, Tap produced 34.525, cabinet purchase left 9.525, and upgraded idle earnings continued (+4.022 over about two seconds).
- Scene integrity: 77,582 triangles, 170 renderers, 12 stations, zero realtime lights, one Rigidbody. No missing used materials or Udon program sources.
- Rendered and visually inspected overview, arcade, lounge and supplied gaming desk views.
- Verified final scene video render mode is VRChat RenderTexture enum value 0 (Unity's differently numbered enum must not be used). Target texture, speaker, URL input, seven buttons and idle overlay are wired. Verified SDK routes audio to the speaker controlled by local volume/mute.
- Matching VRChat SDK assetbundle exports passed after video correction: Android 1,014,926 bytes; Windows 935,619 bytes. Both were exported from the same saved scene.
- Official pinned package versions are available through the restoration script.

## Scope of these checks

SDK bundle exports run build callbacks and packaging, including Udon preprocessing. They do not replace the complete interactive SDK Builder panel validation or an actual VRChat test session. No world has been uploaded or published.

Quest headset rendering/performance, VR hand interaction, multiplayer ownership/late joining, and real network video playback remain untested. Test a direct HTTPS MP4 first, then desired provider URLs on both PC and Quest. Progress is intentionally personal and current-visit-only.

Gameplay smoke testing preceded the last static furniture/video-output refinements; the tested economy runtime code is unchanged.
