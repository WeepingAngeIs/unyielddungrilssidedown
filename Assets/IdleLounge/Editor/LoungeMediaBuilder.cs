using UnityEngine;
using UnityEngine.UI;
using UnityEngine.Video;
using UnityEditor;
using UnityEditor.Events;
using UdonSharpEditor;
using VRC.SDK3.Components;
using VRC.SDK3.Video.Components;
using VRC.Udon;

public static class LoungeMediaBuilder
{
    private const string AssetFolder = "Assets/IdleLounge/Generated";
    private static readonly Color Ink = new Color(0.025f, 0.045f, 0.075f);
    private static readonly Color Cyan = new Color(0.20f, 0.90f, 0.94f);

    // Audience is at smaller Z; screen and UI face -Z.
    public static GameObject Build(Vector3 screenCenter)
    {
        if (!AssetDatabase.IsValidFolder(AssetFolder)) AssetDatabase.CreateFolder("Assets/IdleLounge", "Generated");
        GameObject root = new GameObject("Watch Together - Shared Video");
        root.transform.position = screenCenter;
        var frameMat = MaterialAsset("MediaFrame", "Unlit/Color", Ink);
        var accentMat = MaterialAsset("MediaAccent", "Unlit/Color", Cyan);
        Box(root.transform, "Screen frame", new Vector3(0, 0, 0.05f), new Vector3(6.62f, 3.82f, 0.14f), frameMat);
        Box(root.transform, "Cyan edge", new Vector3(0, -1.88f, -0.03f), new Vector3(6.62f, 0.035f, 0.035f), accentMat);

        string rtPath = AssetFolder + "/LoungeScreen.renderTexture";
        RenderTexture rt = AssetDatabase.LoadAssetAtPath<RenderTexture>(rtPath);
        if (rt == null)
        {
            rt = new RenderTexture(1024, 576, 0, RenderTextureFormat.ARGB32);
            rt.name = "Lounge Screen 1024x576";
            rt.useMipMap = false;
            rt.wrapMode = TextureWrapMode.Clamp;
            AssetDatabase.CreateAsset(rt, rtPath);
        }
        Material screenMat = MaterialAsset("VideoSurface", "Unlit/Texture", Color.white);
        screenMat.mainTexture = rt;
        EditorUtility.SetDirty(screenMat);
        GameObject screen = GameObject.CreatePrimitive(PrimitiveType.Quad);
        screen.name = "Video Screen 6.4m x 3.6m";
        screen.transform.SetParent(root.transform, false);
        screen.transform.localPosition = new Vector3(0, 0, -0.04f);
        screen.transform.localScale = new Vector3(6.4f, 3.6f, 1);
        Object.DestroyImmediate(screen.GetComponent<Collider>());
        screen.GetComponent<Renderer>().sharedMaterial = screenMat;

        GameObject speakerGo = new GameObject("Screen speakers");
        speakerGo.transform.SetParent(root.transform, false);
        speakerGo.transform.localPosition = new Vector3(0, -1.6f, -0.2f);
        AudioSource audio = speakerGo.AddComponent<AudioSource>();
        audio.playOnAwake = false;
        audio.spatialBlend = 1f;
        audio.rolloffMode = AudioRolloffMode.Linear;
        audio.minDistance = 4f;
        audio.maxDistance = 17f;
        audio.volume = 0.35f;

        VRCUnityVideoPlayer player = root.AddComponent<VRCUnityVideoPlayer>();
        // The SDK wrapper creates its native Unity VideoPlayer at runtime, as in
        // the official UdonSyncPlayer prefab. Do not add a second native player.
        SerializedObject videoSettings = new SerializedObject(player);
        SetBool(videoSettings, "autoPlay", false);
        SetBool(videoSettings, "loop", false);
        SetBool(videoSettings, "EnableAutomaticResync", false);
        SetInt(videoSettings, "renderMode", 0); // VRChat enum: RenderTexture=0; Unity enum uses 2.
        SetInt(videoSettings, "aspectRatio", (int)VideoAspectRatio.FitInside);
        SetInt(videoSettings, "maximumResolution", 720);
        videoSettings.FindProperty("targetTexture").objectReferenceValue = rt;
        SerializedProperty outputs = videoSettings.FindProperty("targetAudioSources");
        outputs.arraySize = 1;
        outputs.GetArrayElementAtIndex(0).objectReferenceValue = audio;
        videoSettings.ApplyModifiedPropertiesWithoutUndo();

        LoungeVideo control = root.AddUdonSharpComponent<LoungeVideo>();
        control.videoPlayer = player;
        control.speaker = audio;

        GameObject idle = new GameObject("Idle screen welcome", typeof(RectTransform), typeof(Canvas));
        idle.layer = 5;
        idle.transform.SetParent(root.transform, false);
        idle.transform.localPosition = new Vector3(0, 0, -0.055f);
        idle.transform.localScale = Vector3.one * 0.005f;
        idle.GetComponent<Canvas>().renderMode = RenderMode.WorldSpace;
        RectTransform idleRect = idle.GetComponent<RectTransform>();
        idleRect.sizeDelta = new Vector2(1280, 720);
        Panel(idleRect, "Idle screen background", Vector2.zero, new Vector2(1280, 720), Ink).GetComponent<Image>().raycastTarget = false;
        Label(idleRect, "NEON NOOK  /  CINEMA", new Vector2(0, 205), new Vector2(1160, 70), 32, Cyan, TextAnchor.MiddleCenter);
        Label(idleRect, "WATCH TOGETHER", new Vector2(0, 50), new Vector2(1160, 115), 72, Color.white, TextAnchor.MiddleCenter);
        Label(idleRect, "Take a seat. Let your arcade grow.", new Vector2(0, -55), new Vector2(1160, 75), 36, new Color(0.7f, 0.82f, 0.9f), TextAnchor.MiddleCenter);
        Label(idleRect, "The host can load a video using the controls below.\nYour volume stays personal.", new Vector2(0, -205), new Vector2(1160, 110), 28, new Color(0.5f, 0.64f, 0.72f), TextAnchor.MiddleCenter);
        control.idleOverlay = idle;

        GameObject canvasGo = new GameObject("Video controls", typeof(RectTransform), typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster), typeof(VRCUiShape));
        canvasGo.layer = 5;
        canvasGo.transform.SetParent(root.transform, false);
        canvasGo.transform.localPosition = new Vector3(0, -2.65f, -0.13f);
        canvasGo.transform.localScale = Vector3.one * 0.005f;
        RectTransform canvasRect = canvasGo.GetComponent<RectTransform>();
        canvasRect.sizeDelta = new Vector2(1280, 270);
        canvasGo.GetComponent<Canvas>().renderMode = RenderMode.WorldSpace;
        canvasGo.GetComponent<CanvasScaler>().dynamicPixelsPerUnit = 1;
        BoxCollider uiCollider = canvasGo.AddComponent<BoxCollider>();
        uiCollider.size = new Vector3(1280, 270, 1);
        uiCollider.center = new Vector3(0, 0, 1);
        Panel(canvasRect, "Control background", Vector2.zero, new Vector2(1280, 270), Ink);
        Label(canvasRect, "WATCH TOGETHER", new Vector2(-435, 104), new Vector2(370, 42), 30, Cyan, TextAnchor.MiddleLeft);
        Label(canvasRect, "HOST CONTROLS  /  YOUR VOLUME", new Vector2(245, 104), new Vector2(720, 42), 22, Color.white, TextAnchor.MiddleRight);

        RectTransform inputRect = Panel(canvasRect, "Video URL", new Vector2(-110, 46), new Vector2(1010, 48), new Color(0.1f, 0.15f, 0.21f));
        VRCUrlInputField input = inputRect.gameObject.AddComponent<VRCUrlInputField>();
        Text inputText = Label(inputRect, "", Vector2.zero, new Vector2(980, 44), 23, Color.white, TextAnchor.MiddleLeft);
        Text placeholder = Label(inputRect, "Paste video URL here (direct MP4 for editor preview)", Vector2.zero, new Vector2(980, 44), 22, new Color(0.55f, 0.65f, 0.73f), TextAnchor.MiddleLeft);
        input.textComponent = inputText;
        input.placeholder = placeholder;
        input.targetGraphic = inputRect.GetComponent<Image>();
        input.lineType = VRCUrlInputField.LineType.SingleLine;
        input.characterLimit = 2048;
        control.urlInput = input;

        UdonBehaviour backing = UdonSharpEditorUtility.GetBackingUdonBehaviour(control);
        Button(canvasRect, "LOAD", new Vector2(520, 46), new Vector2(190, 48), backing, "LoadVideo");
        string[] labels = { "PLAY / PAUSE", "STOP", "RETRY", "VOL -", "VOL +", "MUTE" };
        string[] methods = { "TogglePause", "StopVideo", "RetryVideo", "VolumeDown", "VolumeUp", "ToggleMute" };
        for (int i = 0; i < labels.Length; i++) Button(canvasRect, labels[i], new Vector2(-520 + i * 208, -16), new Vector2(192, 44), backing, methods[i]);
        control.statusText = Label(canvasRect, "Host: paste a video URL, then LOAD.\nPersonal volume: 35%", new Vector2(0, -85), new Vector2(1220, 82), 22, new Color(0.75f, 0.84f, 0.9f), TextAnchor.MiddleLeft);
        UdonSharpEditorUtility.CopyProxyToUdon(control);
        EditorUtility.SetDirty(control);
        return root;
    }

    private static void SetBool(SerializedObject obj, string name, bool value) { SerializedProperty prop = obj.FindProperty(name); if (prop != null) prop.boolValue = value; }
    private static void SetInt(SerializedObject obj, string name, int value) { SerializedProperty prop = obj.FindProperty(name); if (prop != null) prop.intValue = value; }

    private static Material MaterialAsset(string name, string shader, Color color)
    {
        string path = AssetFolder + "/" + name + ".mat";
        Material material = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (material == null) { material = new Material(Shader.Find(shader)); AssetDatabase.CreateAsset(material, path); }
        if (material.HasProperty("_Color")) material.color = color;
        material.enableInstancing = true;
        EditorUtility.SetDirty(material);
        return material;
    }

    private static void Box(Transform parent, string name, Vector3 position, Vector3 scale, Material material)
    {
        GameObject obj = GameObject.CreatePrimitive(PrimitiveType.Cube);
        obj.name = name;
        obj.transform.SetParent(parent, false);
        obj.transform.localPosition = position;
        obj.transform.localScale = scale;
        obj.GetComponent<Renderer>().sharedMaterial = material;
    }

    private static RectTransform Rect(Transform parent, string name, Vector2 position, Vector2 size)
    {
        GameObject obj = new GameObject(name, typeof(RectTransform));
        obj.layer = 5;
        RectTransform rect = obj.GetComponent<RectTransform>();
        rect.SetParent(parent, false);
        rect.anchorMin = rect.anchorMax = rect.pivot = new Vector2(0.5f, 0.5f);
        rect.anchoredPosition = position;
        rect.sizeDelta = size;
        return rect;
    }

    private static RectTransform Panel(Transform parent, string name, Vector2 position, Vector2 size, Color color)
    {
        RectTransform rect = Rect(parent, name, position, size);
        rect.gameObject.AddComponent<Image>().color = color;
        return rect;
    }

    private static Text Label(Transform parent, string text, Vector2 position, Vector2 size, int fontSize, Color color, TextAnchor alignment)
    {
        RectTransform rect = Rect(parent, "Label", position, size);
        Text label = rect.gameObject.AddComponent<Text>();
        label.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
        label.text = text;
        label.fontSize = fontSize;
        label.color = color;
        label.alignment = alignment;
        label.horizontalOverflow = HorizontalWrapMode.Wrap;
        label.verticalOverflow = VerticalWrapMode.Truncate;
        label.raycastTarget = false;
        return label;
    }

    private static void Button(Transform parent, string title, Vector2 position, Vector2 size, UdonBehaviour target, string action)
    {
        RectTransform rect = Panel(parent, title, position, size, new Color(0.075f, 0.25f, 0.29f));
        UnityEngine.UI.Button button = rect.gameObject.AddComponent<UnityEngine.UI.Button>();
        button.targetGraphic = rect.GetComponent<Image>();
        ColorBlock colors = button.colors;
        colors.highlightedColor = Cyan;
        colors.pressedColor = new Color(0.4f, 0.7f, 0.7f);
        button.colors = colors;
        Label(rect, title, Vector2.zero, size, 23, Color.white, TextAnchor.MiddleCenter);
        UnityEventTools.AddStringPersistentListener(button.onClick, target.SendCustomEvent, action);
    }
}
