using System;
using System.IO;
using System.Reflection;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using VRC.Editor;
using VRC.SDKBase.Editor;
using VRC.SDKBase.Editor.BuildPipeline;

/// <summary>Build-only verification. Never uploads a world or starts the VRChat client.</summary>
public static class WorldBuildChecks
{
    private static string receivedBundlePath;
    private static string receivedBuildError;
    private const BindingFlags InternalStatic = BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic;
    private static void OnProgress(object sender, string message) { Debug.Log("WORLD BUILD: " + message); }
    private static void OnError(object sender, string message) { receivedBuildError = message; }
    private static void OnSuccess(object sender, (string path, string signature) result) { receivedBundlePath = result.path; }
    private static void InvokeSdk(string name)
    {
        MethodInfo method = typeof(VRC_SdkBuilder).GetMethod(name, InternalStatic);
        if (method == null) throw new MissingMethodException(typeof(VRC_SdkBuilder).FullName, name);
        method.Invoke(null, null);
    }
    private static void RegisterCallback(string registration, string handler)
    {
        MethodInfo method = typeof(VRC_SdkBuilder).GetMethod(registration, InternalStatic);
        if (method == null) throw new MissingMethodException(typeof(VRC_SdkBuilder).FullName, registration);
        MethodInfo callback = typeof(WorldBuildChecks).GetMethod(handler, InternalStatic);
        Delegate value = Delegate.CreateDelegate(method.GetParameters()[0].ParameterType, callback);
        method.Invoke(null, new object[] { value });
    }
    public static void Windows() { Export(BuildTarget.StandaloneWindows64, "Windows"); }
    public static void Android() { NeonNookPerimeterLayout.ReviewQuest(); Export(BuildTarget.Android, "Android"); }
    public static void WindowsPC() { Export(BuildTarget.StandaloneWindows64, "Windows-PC", NeonNookPcUpgrade.ScenePath); }

    private static void Export(BuildTarget expectedTarget, string label, string scenePath = "Assets/IdleLounge/Scenes/NeonNook.unity")
    {
        string project = Path.GetFullPath(Path.Combine(Application.dataPath, ".."));
        string report = Path.GetFullPath(Path.Combine(project, "..", "build-validation-" + label + ".txt"));
        string output = NeonNookPaths.OutputDirectory;
        File.WriteAllText(report, "STARTED " + DateTime.UtcNow.ToString("O") + "\nTarget: " + label + "\n");
        receivedBundlePath = null;
        receivedBuildError = null;
        try
        {
            if (EditorUserBuildSettings.activeBuildTarget != expectedTarget)
                throw new InvalidOperationException("Start Unity with -buildTarget " + expectedTarget + " before this method; current target is " + EditorUserBuildSettings.activeBuildTarget);

            EditorSceneManager.OpenScene(scenePath, OpenSceneMode.Single);
            AssetDatabase.SaveAssets();
            // Match the SDK world builder's build-only pipeline, including Udon preprocessing.
            if (!VRCBuildPipelineCallbacks.OnVRCSDKBuildRequested(VRCSDKRequestedBuildType.Scene))
                throw new InvalidOperationException("VRChat SDK build callback blocked the world export.");
            EnvConfig.ConfigurePlayerSettings();
            EditorPrefs.SetBool("VRC.SDKBase_StripAllShaders", false);
            VRC_SdkBuilder.shouldBuildUnityPackage = false;
            VRC_SdkBuilder.PreBuildBehaviourPackaging();
            MethodInfo setBuilder = typeof(VRC_SdkBuilder).GetMethod("SetCurrentBuilder", InternalStatic);
            if (setBuilder == null) throw new MissingMethodException("VRC_SdkBuilder.SetCurrentBuilder");
            setBuilder.MakeGenericMethod(typeof(ISDKWorldBuilder)).Invoke(null, null);
            InvokeSdk("ClearCallbacks");
            RegisterCallback("RegisterBuildProgressCallback", "OnProgress");
            RegisterCallback("RegisterBuildErrorCallback", "OnError");
            RegisterCallback("RegisterBuildSuccessCallback", "OnSuccess");
            bool success = VRC_SdkBuilder.RunExportSceneResource();
            string buildError = receivedBuildError;
            string bundlePath = receivedBundlePath;
            if (!success || !string.IsNullOrEmpty(buildError))
                throw new InvalidOperationException("VRChat world export failed: " + (buildError ?? "See Unity log."));
            if (string.IsNullOrEmpty(bundlePath) || !File.Exists(bundlePath))
                throw new InvalidOperationException("SDK returned success but no existing bundle path was received. Check Unity log before claiming build success.");

            Directory.CreateDirectory(output);
            string destination = Path.Combine(output, "NeonNook-" + label + ".vrcw");
            File.Copy(bundlePath, destination, true);
            long bytes = new FileInfo(destination).Length;
            File.AppendAllText(report, "PASS: VRChat SDK assetbundle export completed.\nBundle: " + destination + "\nBytes: " + bytes + "\nNo upload or runtime/headset test performed.\n");
            Debug.Log("WORLD_BUILD_PASS " + label + " " + destination + " " + bytes + " bytes");
        }
        catch (Exception e)
        {
            File.AppendAllText(report, "FAIL: " + e + "\n");
            Debug.LogException(e);
            throw;
        }
        finally
        {
            InvokeSdk("ClearCallbacks");
        }
    }
}
