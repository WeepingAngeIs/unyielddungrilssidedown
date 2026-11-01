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

## Enhanced PC scene — November 1, 2026

The PC scene is `Assets/IdleLounge/Scenes/NeonNook-PC.unity`; the lighter Android scene remains `NeonNook.unity`.

- Unity compilation and CPU progressive lighting bake passed. Two directional lightmaps, baked light probes and two reflection probes are saved with the PC scene. Final bake produced no overlapping-UV warnings.
- The PC scene audit passed: 257,294 referenced triangles, 359 active renderers, 42 used materials, 123 lightmapped renderers and all 12 station exits checked with a 0.20 m radius / 1.50 m height capsule. No missing used materials, invalid normals/tangents/indices, invalid bounds, or incomplete UV channels were reported.
- Rounded meshes use six explicitly padded lightmap charts, preserving metric texture UVs. Small details and imported chair/keyboard pieces use baked probes. Generated imported snapshots had zero bounds; those bounds were repaired, and PC snapshots were reconstructed with geometry, submeshes and UVs verified unchanged.
- Fixed the slow-corner seat exits, widened arcade gaps, aligned PC glazing/frame grids, closed the window header gaps and added shared window boundaries. The user's existing foundation position was retained.
- PC and Android scenes contain the same 23 network entries (46 serialized ID/object fields compared equal).
- Enhanced-scene ClientSim/Udon VM smoke passed: idle wallet 32.604, tap 34.604, cabinet purchase 9.604, then +3.999 upgraded income in about two seconds. The player camera inherited HDR/post-processing and loaded baked lightmaps.
- Direct Unity captures were visually inspected at the entry, arcade, lounge, imported desk, and close furniture surfaces. `Docs/Preview/pc-clientsim.png` comes from the running ClientSim player camera after the purchase test. No AI image enhancement was used.
- Enhanced PC VRChat SDK bundle export passed: 9,952,841 bytes.
- Updated lighter Android bundle export passed: 1,013,980 bytes. Both exports use the matching network entries and revised shared layout.

These checks cover the generated geometry and saved lighting; they cannot certify every avatar size or camera angle. PC/Quest headset frame rate, real multiplayer and network video playback still require a VRChat session.
