using System;
using System.Globalization;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
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
    { BeginScene(NeonNookPcUpgrade.ScenePath, true); }

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

                economy.SendCustomEvent("Tap");
                float tappedCredits = Number(economy, "credits");
                Require(Math.Abs(tappedCredits - idleCredits - 2f) < 0.05f, "Udon Tap adds two credits");
                Append("PASS Udon Tap: " + tappedCredits.ToString("F3", CultureInfo.InvariantCulture));

                economy.SendCustomEvent("BuyCabinet");
                float purchasedCredits = Number(economy, "credits");
                Require(Number(economy, "cabinets") == 1f, "Udon purchase adds cabinet");
                Require(Math.Abs(purchasedCredits - tappedCredits + 25f) < 0.05f, "Udon purchase spends 25 credits");
                Append("PASS Udon BuyCabinet: wallet " + purchasedCredits.ToString("F3", CultureInfo.InvariantCulture));
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
                    Append("PASS PC player camera: HDR, post-processing and baked lighting; direct ClientSim capture saved.");
                }
                Finish(true, "RUNTIME_SMOKE_PASS: real Udon variable reads and events under ClientSim.\nNot covered: multiplayer networking, headset rendering/input, video playback or Android runtime.");
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
