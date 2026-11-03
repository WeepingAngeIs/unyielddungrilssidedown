# Validation — Neon Nook

Checked November 2, 2026 with Unity **2022.3.22f1** and VRChat SDK **3.10.6**. These results cover the current perimeter layout and wall-mounted arcade dashboard.

## Scenes and geometry

| Check | Enhanced PC | Lighter Quest / Android |
|---|---:|---:|
| Scene | `NeonNook-PC.unity` | `NeonNook.unity` |
| Referenced triangles | 219,578 | 70,226 |
| Reported renderers | 295 | 140 |
| Sofas / VRChat seats | 6 / 12 | 6 / 12 |
| Clear central floor | 168 m² | 168 m² |
| Matching network entries | 23 | 23 |

The lighter scene's renderer total includes six disabled renderers retained on the original arcade action objects and the disabled redundant decorative wordmark. It has zero realtime lights and one Rigidbody.

Both perimeter circulation audits passed. They checked the clear zone at **x −7…7 m, z −6…6 m**, from y 0.08 to 2.20 m, and all twelve seat-exit routes using a walking capsule of radius 0.25 m and total height 1.65 m. The PC quality audit also checked twelve station exits with a radius 0.20 m / height 1.50 m capsule.

The PC quality audit passed with 84 unique geometry meshes, 48 used materials and 97 lightmap GI renderers. Two baked lightmap textures are saved with the PC scene. The final lighting bake produced no overlapping-UV warnings. Matching network entries are preserved between PC and Android; these are two platform versions of the same world.

## Actual UI and Udon runtime checks

Both saved scenes passed the ClientSim / Udon VM smoke test:

- Automatic income increased the fresh wallet above its starting 25 credits: **32.655 on PC** and **32.622 in the lighter scene**.
- All six UI buttons had one enabled persistent listener, each targeting the correct existing `ArcadeAction`, its unique action ID and the same economy through `SendCustomEvent("Activate")`.
- After the idle-income check, a deterministic 25-credit fixture exercised the actual **PLAY button** to reach 27 credits, then the actual **BUY CABINET button** to leave 2 credits and one owned cabinet.
- The dashboard updated its wallet, income to **2.0 / sec**, owned count to **1 OWNED**, next price to **29**, marginal income, purchase feedback and opaque unaffordable state. Prestige progress matched the earned-credit value and stayed within 0–1.
- Upgraded idle income continued: approximately **+4.027 credits on PC** and **+4.008 in the lighter scene** over two seconds. Display values refreshed after this continued income.
- All **45 visible dashboard labels** fit their actual Unity font and wrapping bounds.
- The PC player camera inherited HDR and post-processing, and the runtime loaded baked lightmaps. Player-camera and live dashboard captures were saved from ClientSim.

These tests invoke the real Unity UI button events and Udon programs. They do **not** simulate a physical pointer ray, VR controller pointing or headset touch input.

## Exports

VRChat SDK assetbundle exports completed for both target scenes without uploading:

| Target | Bundle | Size |
|---|---|---:|
| Windows PC | `NeonNook-Windows-PC.vrcw` | 9,313,717 bytes |
| Android | `NeonNook-Android.vrcw` | 1,095,647 bytes |

Bundle export runs SDK build callbacks and packaging, including Udon preprocessing. It does not replace the complete interactive SDK Builder panel checks or a VRChat session. No world has been uploaded or published.

## Evidence and remaining checks

Reports are saved under `Docs/Validation`: `runtime-smoke-PC.txt`, `runtime-smoke.txt`, `build-validation-Windows-PC.txt`, `build-validation-Android.txt`, `validation.txt`, `pc-quality-audit.txt`, `pc-layout-audit.txt` and `quest-layout-audit.txt`. Updated views are under `Docs/Preview`; `pc-clientsim.png` and `pc-arcade-runtime.png` come from the running Unity/ClientSim scene. After the runtime tests, the redundant Quest decorative wordmark was hidden to prevent overlap in elevated views; its final layout audit, preview rendering and Android export were repeated.

Actual PC/Quest hardware pointing, headset rendering and frame rate, multiplayer ownership and late joining, and network video playback remain **untested**. The lighter scene's ClientSim pass is an editor test, not Android runtime execution. Test a direct HTTPS MP4 and the intended video providers on both platforms, including local mute/volume and host departure.

Collider clearance tests use defined capsule sizes and straight routes; they do not certify every avatar proportion or visual intersection. A bake without overlapping-UV warnings does not certify every UV chart or viewing angle. Personal arcade progress remains intentionally **current-visit-only**, with no persistent save or offline income.
