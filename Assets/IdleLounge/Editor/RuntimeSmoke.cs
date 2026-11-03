using System;
using System.Globalization;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.UI;
using VRC.SDK3.ClientSim;
using VRC.Udon;

/// <summary>Invoke with -batchmode -executeMethod RuntimeSmoke.Begin, without -quit.</summary>
[InitializeOnLoad]
public static class RuntimeSmoke
{
    private const string Prefix = "NeonNook.RuntimeSmoke.";
    private const string ClientSimPrefs = "com.vrchat.clientsim.settings";
    private static string ReportPath => Path.GetFullPath(Path.Combine(Application.dataPath, SessionState.GetBool(Prefix + "PC", false) ? "../../runtime-smoke-PC.txt" : "../../runtime-smoke.txt"));

    static RuntimeSmoke()
    {
        EditorApplication.update += Tick;
        EditorApplication.playModeStateChanged += ModeChanged;
        Application.logMessageReceived += CaptureLog;
    }

    public static void Begin()
    { BeginScene("Assets/IdleLounge/Scenes/NeonNook.unity", false); }

    public static void BeginPC()
    { ArcadeEconomyChecks.Run(); BeginScene(NeonNookPcUpgrade.ScenePath, true); }

    public static void BeginQuestReview()
    { NeonNookPerimeterLayout.ReviewQuest(); Begin(); }

    private static void BeginScene(string scenePath, bool pc)
    {
        if (EditorApplication.isPlayingOrWillChangePlaymode)
            throw new InvalidOperationException("RuntimeSmoke must begin in Edit Mode.");
        SessionState.SetBool(Prefix + "Active", true);
        SessionState.SetBool(Prefix + "PC", pc);
        SessionState.SetInt(Prefix + "Phase", 0);
        SessionState.SetInt(Prefix + "ExitCode", 1);
        SessionState.SetString(Prefix + "Errors", "");
        SessionState.SetString(Prefix + "Report", "NEON NOOK CLIENTSIM / UDON VM SMOKE\n");
        SetTime("Started", Now());
        SessionState.SetBool(Prefix + "HadSettings", EditorPrefs.HasKey(ClientSimPrefs));
        SessionState.SetString(Prefix + "OriginalSettings", EditorPrefs.GetString(ClientSimPrefs, ""));
        try
        {
            var settings = ClientSimSettings.Instance;
            settings.enableClientSim = true;
            settings.initializationDelay = 0f;
            settings.hideMenuOnLaunch = true;
            settings.displayLogs = false;
            settings.spawnPlayer = true;
            ClientSimSettings.SaveSettings(settings);
            EditorSceneManager.OpenScene(scenePath);
            Append("Scene: " + scenePath);
            Append("Started UTC: " + DateTime.UtcNow.ToString("O"));
            EditorApplication.isPlaying = true;
        }
        catch (Exception exception) { Finish(false, exception.ToString()); }
    }

    private static double Now() => (DateTime.UtcNow - new DateTime(1970, 1, 1)).TotalSeconds;
    private static void SetTime(string key, double value) => SessionState.SetString(Prefix + key, value.ToString("R", CultureInfo.InvariantCulture));
    private static double GetTime(string key) => double.Parse(SessionState.GetString(Prefix + key, "0"), CultureInfo.InvariantCulture);

    private static void ModeChanged(PlayModeStateChange change)
    {
        if (!SessionState.GetBool(Prefix + "Active", false)) return;
        if (change == PlayModeStateChange.EnteredPlayMode && SessionState.GetInt(Prefix + "Phase", 0) == 0)
        {
            SessionState.SetInt(Prefix + "Phase", 1);
            SetTime("Playing", Now());
        }
    }

    private static void Tick()
    {
        if (!SessionState.GetBool(Prefix + "Active", false)) return;
        int phase = SessionState.GetInt(Prefix + "Phase", 0);
        if (Now() - GetTime("Started") > 90d)
        {
            Append("FAIL: 90-second hard timeout.");
            WriteAndExit(1);
            return;
        }
        if (phase == 3)
        {
            if (!EditorApplication.isPlayingOrWillChangePlaymode)
                WriteAndExit(SessionState.GetInt(Prefix + "ExitCode", 1));
            return;
        }
        if (!EditorApplication.isPlaying) return;
        try
        {
            // Fallback for no-domain-reload configurations that miss the transition callback.
            if (phase == 0)
            {
                SessionState.SetInt(Prefix + "Phase", 1);
                SetTime("Playing", Now());
                return;
            }
            if (phase == 1 && Now() - GetTime("Playing") >= 8d)
            {
                Require(ClientSimMain.HasInstance(), "ClientSim instance exists");
                var economy = FindEconomy();
                float idleCredits = Number(economy, "credits");
                Require(idleCredits > 25f, "Udon Update accrued idle credits above starting 25; got " + idleCredits);
                Require(Number(economy, "cabinets") == 0f, "Fresh run has no purchased cabinets");
                Append("PASS idle accrual: " + idleCredits.ToString("F3", CultureInfo.InvariantCulture));

                bool dashboard = GameObject.Find("Arcade Dashboard") != null;
                SessionState.SetBool(Prefix + "Dashboard", dashboard);
                Button[] buttons = dashboard ? DashboardButtons(economy) : null;
                if (dashboard)
                {
                    CheckDashboard(economy, false, false);
                    // Deterministic affordability fixture after proving real idle accrual.
                    // PLAY and purchase still traverse the actual persistent Unity UI events.
                    economy.SetProgramVariable("credits", 25f);
                    idleCredits = 25f;
                    buttons[0].onClick.Invoke();
                }
                else economy.SendCustomEvent("Tap");
                float tappedCredits = Number(economy, "credits");
                Require(Math.Abs(tappedCredits - idleCredits - 2f) < 0.05f, "Udon Tap adds two credits");
                Append("PASS " + (dashboard ? "UI PLAY -> ArcadeAction -> Udon Tap" : "Udon Tap") + ": " + tappedCredits.ToString("F3", CultureInfo.InvariantCulture));

                if (dashboard) buttons[1].onClick.Invoke();
                else economy.SendCustomEvent("BuyCabinet");
                float purchasedCredits = Number(economy, "credits");
                Require(Number(economy, "cabinets") == 1f, "Udon purchase adds cabinet");
                Require(Math.Abs(purchasedCredits - tappedCredits + 25f) < 0.05f, "Udon purchase spends 25 credits");
                Append("PASS " + (dashboard ? "UI cabinet purchase" : "Udon BuyCabinet") + ": wallet " + purchasedCredits.ToString("F3", CultureInfo.InvariantCulture));
                if (dashboard) CheckDashboard(economy, true, true);
                SessionState.SetFloat(Prefix + "AfterPurchase", purchasedCredits);
                SetTime("Purchased", Now());
                SessionState.SetInt(Prefix + "Phase", 2);
            }
            else if (phase == 2 && Now() - GetTime("Purchased") >= 2d)
            {
                float credits = Number(FindEconomy(), "credits");
                float earned = credits - SessionState.GetFloat(Prefix + "AfterPurchase", 0f);
                Require(earned > 1.8f, "Udon continues accruing after upgrade; gain " + earned);
                Append("PASS continued upgraded idle accrual: +" + earned.ToString("F3", CultureInfo.InvariantCulture));
                if (SessionState.GetBool(Prefix + "Dashboard", false))
                {
                    CheckDashboard(FindEconomy(), true, false);
                    AuditDashboardText();
                    Append("PASS dashboard refreshed after continued idle income, not just on purchase.");
                }
                string errors = SessionState.GetString(Prefix + "Errors", "");
                Require(string.IsNullOrEmpty(errors), "No lounge/Udon runtime errors: " + errors);
                if(SessionState.GetBool(Prefix + "PC", false))
                {
                    var camera=UnityEngine.Object.FindObjectsOfType<Camera>().FirstOrDefault(c=>c.name=="PlayerCamera" && c.enabled);
                    Require(camera!=null,"ClientSim player camera exists");
                    Require(camera.allowHDR,"Player camera inherits HDR");
                    Require(camera.GetComponent<UnityEngine.Rendering.PostProcessing.PostProcessLayer>()!=null,"Player camera inherits scene post-processing");
                    Require(LightmapSettings.lightmaps.Length>0,"PC runtime has baked lightmaps");
                    NeonNookPcUpgrade.CaptureRuntimeCamera(camera);
                    if (SessionState.GetBool(Prefix + "Dashboard", false)) CaptureDashboard(camera);
                    Append("PASS PC player camera: HDR, post-processing and baked lighting; direct ClientSim capture saved.");
                }
                Finish(true, "RUNTIME_SMOKE_PASS: real Udon variable reads and events under ClientSim; upgraded scenes exercise persistent Unity UI actions and live display values.\nNot covered: physical pointer/raycast or headset input, multiplayer networking, video playback or Android runtime.");
            }
        }
        catch (Exception exception) { Finish(false, exception.ToString()); }
    }

    private static UdonBehaviour FindEconomy()
    {
        var host = GameObject.Find("Personal arcade economy");
        Require(host != null, "Economy GameObject exists");
        var economy = host.GetComponent<UdonBehaviour>();
        Require(economy != null && economy.enabled, "Economy backing UdonBehaviour is enabled");
        Require(economy.GetProgramVariable("credits") != null, "Udon program is initialized");
        return economy;
    }

    private static float Number(UdonBehaviour behaviour, string variable)
    {
        object value = behaviour.GetProgramVariable(variable);
        Require(value != null, "Udon variable exists: " + variable);
        return Convert.ToSingle(value, CultureInfo.InvariantCulture);
    }

    private static Button[] DashboardButtons(UdonBehaviour economy)
    {
        GameObject dashboard = GameObject.Find("Arcade Dashboard");
        Require(dashboard != null, "Arcade dashboard exists");
        Button[] found = dashboard.GetComponentsInChildren<Button>();
        Require(found.Length == 6, "Dashboard contains all six action buttons");
        Button[] byAction = new Button[6];
        foreach (Button button in found)
        {
            Require(button.interactable && button.gameObject.activeInHierarchy, "Action button is interactable: " + button.name);
            Require(button.onClick.GetPersistentEventCount() == 1, "Exactly one persistent action on " + button.name);
            var target = button.onClick.GetPersistentTarget(0) as UdonBehaviour;
            Require(target != null && target.enabled, "Persistent button target is enabled backing Udon");
            Require(button.onClick.GetPersistentMethodName(0) == "SendCustomEvent", "Button routes through Udon SendCustomEvent");
            var serialized = new SerializedObject(button);
            var call = serialized.FindProperty("m_OnClick.m_PersistentCalls.m_Calls").GetArrayElementAtIndex(0);
            Require(call.FindPropertyRelative("m_Arguments.m_StringArgument").stringValue == "Activate", "Button dispatches ArcadeAction.Activate");
            Require(call.FindPropertyRelative("m_CallState").intValue != 0, "Persistent listener is enabled");
            int action = (int)Number(target, "action");
            Require(action >= 0 && action < 6 && byAction[action] == null, "Each existing action ID is mapped once");
            Require(target.GetProgramVariable("economy") as UdonBehaviour == economy, "Action references the same existing economy");
            byAction[action] = button;
        }
        Require(byAction.All(button => button != null), "Action IDs 0 through 5 all have UI listeners");
        Append("PASS six UI listeners: exact existing Udon targets, unique action IDs, Activate event and economy identity.");
        return byAction;
    }

    private static Text DashboardText(UdonBehaviour economy, string field)
    {
        var text = economy.GetProgramVariable(field) as Text;
        Require(text != null && text.gameObject.activeInHierarchy, "Active dashboard text bound: " + field);
        return text;
    }

    private static void CheckDashboard(UdonBehaviour economy, bool boughtCabinet, bool exact)
    {
        float credits = Number(economy, "credits");
        string wallet = DashboardText(economy, "walletValueText").text;
        float visibleWallet;
        Require(float.TryParse(wallet, NumberStyles.Float, CultureInfo.InvariantCulture, out visibleWallet), "Small test wallet is a numeric live credit value");
        // Quarterly display refresh can lag the current VM by at most one displayed
        // credit at this fixture's 1/s or 2/s income. Immediate action refresh is exact.
        float difference = Mathf.Floor(credits) - visibleWallet;
        Require(exact ? difference == 0 : difference >= 0 && difference <= 1, "Wallet text tracks current Udon wallet: visible=" + wallet + " VM=" + credits);
        Require(DashboardText(economy, "incomeValueText").text == (boughtCabinet ? "2.0 / sec" : "1.0 / sec"), "Income text updates after purchase");
        Require(DashboardText(economy, "cabinetOwnedText").text == (boughtCabinet ? "1 OWNED" : "0 OWNED"), "Cabinet owned label reflects VM");
        Require(DashboardText(economy, "cabinetPriceText").text == (boughtCabinet ? "29" : "25"), "Next cabinet price recalculates after purchase");
        Require(DashboardText(economy, "cabinetIncomeText").text == "+1.0 / sec", "Cabinet marginal income label matches base production");
        var progress = economy.GetProgramVariable("prestigeProgressFill") as Image;
        Require(progress != null && progress.fillAmount >= 0 && progress.fillAmount <= 1, "Prestige progress bound and clamped to 0..1");
        float expectedProgress = Mathf.Clamp01(Number(economy, "runEarned") / Number(economy, "prestigeThreshold"));
        Require(Mathf.Abs(progress.fillAmount - expectedProgress) < 0.001f, "Prestige progress reflects actual earned credits");
        var states = economy.GetProgramVariable("purchaseStateImages") as Image[];
        Require(states != null && states.Length == 3 && states.All(image => image != null), "Three business affordability indicators bound");
        if (boughtCabinet && exact)
        {
            Require(credits < 29, "Known post-purchase fixture cannot afford next cabinet");
            Color state = states[0].color;
            Require(Mathf.Abs(state.r - 0.48f) < 0.01f && Mathf.Abs(state.g - 0.54f) < 0.01f && Mathf.Abs(state.b - 0.60f) < 0.01f && Mathf.Abs(state.a - 1f) < 0.01f,
                "Unaffordable cabinet uses opaque steel gray, retaining readable label contrast");
            Require(!string.IsNullOrEmpty(DashboardText(economy, "feedbackText").text), "Purchase produces readable feedback");
        }
        Append("PASS dashboard values: wallet=" + wallet + ", income=" + DashboardText(economy, "incomeValueText").text + ", owned=" + DashboardText(economy, "cabinetOwnedText").text + ", next=" + DashboardText(economy, "cabinetPriceText").text + ", progress=" + progress.fillAmount.ToString("F5", CultureInfo.InvariantCulture));
    }

    private static void CaptureDashboard(Camera camera)
    {
        Transform dashboard = GameObject.Find("Arcade Dashboard").transform;
        Vector3 oldPosition = camera.transform.position;
        Quaternion oldRotation = camera.transform.rotation;
        float oldFov = camera.fieldOfView;
        RenderTexture oldTarget = camera.targetTexture;
        RenderTexture oldActive = RenderTexture.active;
        bool oldWrite = GL.sRGBWrite;
        var hdr = new RenderTexture(1920, 1080, 24, RenderTextureFormat.ARGBHalf, RenderTextureReadWrite.Linear);
        var ldr = new RenderTexture(1920, 1080, 0, RenderTextureFormat.ARGB32, RenderTextureReadWrite.sRGB);
        Texture2D pixels = null;
        try
        {
            camera.transform.position = dashboard.position - dashboard.forward * 6.5f;
            camera.transform.LookAt(dashboard.position, dashboard.up);
            camera.fieldOfView = 46f;
            camera.targetTexture = hdr;
            camera.Render();
            GL.sRGBWrite = true;
            Graphics.Blit(hdr, ldr);
            RenderTexture.active = ldr;
            pixels = new Texture2D(1920, 1080, TextureFormat.RGB24, false);
            pixels.ReadPixels(new Rect(0, 0, 1920, 1080), 0, 0);
            pixels.Apply();
            string path = Path.Combine(NeonNookPaths.OutputDirectory, "pc-arcade-runtime.png");
            File.WriteAllBytes(path, pixels.EncodeToPNG());
            Append("Runtime dashboard capture: " + path);
        }
        finally
        {
            camera.transform.SetPositionAndRotation(oldPosition, oldRotation);
            camera.fieldOfView = oldFov;
            camera.targetTexture = oldTarget;
            RenderTexture.active = oldActive;
            GL.sRGBWrite = oldWrite;
            UnityEngine.Object.DestroyImmediate(hdr);
            UnityEngine.Object.DestroyImmediate(ldr);
            if (pixels != null) UnityEngine.Object.DestroyImmediate(pixels);
        }
    }

    private static void AuditDashboardText()
    {
        Canvas.ForceUpdateCanvases();
        GameObject dashboard = GameObject.Find("Arcade Dashboard");
        var failures = new System.Text.StringBuilder();
        int count = 0;
        foreach (Text text in dashboard.GetComponentsInChildren<Text>())
        {
            if (string.IsNullOrEmpty(text.text)) continue;
            count++;
            float height = text.rectTransform.rect.height;
            // Unity calculates preferredHeight using the actual font, wrapping and
            // current rect width, so this catches clipped or accidentally wrapped labels.
            float preferred = text.preferredHeight;
            if (preferred > height + 2f)
                failures.AppendLine(text.name + ": needs " + preferred.ToString("F1", CultureInfo.InvariantCulture)
                    + "px high, has " + height.ToString("F1", CultureInfo.InvariantCulture) + "px; text=" + text.text);
        }
        Require(failures.Length == 0, "Dashboard text must fit its bounds:\n" + failures);
        Append("PASS dashboard text layout: " + count + " visible labels fit their actual Unity font/wrap bounds.");
    }

    private static void CaptureLog(string message, string stackTrace, LogType type)
    {
        if (!SessionState.GetBool(Prefix + "Active", false)) return;
        if (type != LogType.Error && type != LogType.Exception && type != LogType.Assert) return;
        string text = message + "\n" + stackTrace;
        if (text.Contains("ArcadeEconomy") || text.Contains("ArcadeAction") || text.Contains("LoungeVideo") ||
            text.Contains("LoungeSeat") || text.Contains("BallReset") || text.Contains("UdonBehaviour") || text.Contains("UdonVMException"))
            SessionState.SetString(Prefix + "Errors", SessionState.GetString(Prefix + "Errors", "") + text + "\n");
    }

    private static void Require(bool condition, string message)
    {
        if (!condition) throw new InvalidOperationException(message);
    }

    private static void Append(string text) => SessionState.SetString(Prefix + "Report", SessionState.GetString(Prefix + "Report", "") + text + "\n");

    private static void Finish(bool success, string message)
    {
        Append((success ? "PASS: " : "FAIL: ") + message);
        SessionState.SetInt(Prefix + "ExitCode", success ? 0 : 1);
        SessionState.SetInt(Prefix + "Phase", 3);
        File.WriteAllText(ReportPath, SessionState.GetString(Prefix + "Report", ""));
        EditorApplication.isPlaying = false;
    }

    private static void WriteAndExit(int code)
    {
        // Include errors logged during Play Mode teardown as well.
        string errors = SessionState.GetString(Prefix + "Errors", "");
        if (!string.IsNullOrEmpty(errors)) { Append("Captured runtime errors:\n" + errors); code = 1; }
        File.WriteAllText(ReportPath, SessionState.GetString(Prefix + "Report", ""));
        if (SessionState.GetBool(Prefix + "HadSettings", false))
            EditorPrefs.SetString(ClientSimPrefs, SessionState.GetString(Prefix + "OriginalSettings", ""));
        else EditorPrefs.DeleteKey(ClientSimPrefs);
        SessionState.SetBool(Prefix + "Active", false);
        EditorApplication.Exit(code);
    }
}
