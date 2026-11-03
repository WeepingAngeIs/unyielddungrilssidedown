using System;
using System.IO;
using System.Reflection;
using UnityEditor;
using UnityEditor.Events;
using UnityEngine;
using UnityEngine.UI;
using UdonSharpEditor;
using VRC.SDK3.Components;
using VRC.Udon;

/// <summary>Editor-built arcade display. Retains all existing Udon/network identities.</summary>
public static class ArcadePresentation
{
    const string Folder = "Assets/IdleLounge/Generated/ArcadeUI";
    static readonly Color Ink = Hex("101B2B"), Panel = Hex("192A3C"), Card = Hex("21374A");
    static readonly Color Ivory = Hex("F8F1E6"), Muted = Hex("9CB4C6"), Mint = Hex("70F0CB");
    static readonly Color Apricot = Hex("FFCA8B"), Lilac = Hex("C7B5FF"), Line = Hex("385568");
    static Font font;
    static Sprite round;

    // Suggested center: (-7, 2.8, 10.3). Front faces -Z; lower keys sit at 1.35m.
    public static GameObject Build(ArcadeEconomy economy, ArcadeAction[] actions, Vector3 boardCenter, bool pc)
    {
        if (economy == null || actions == null || actions.Length != 6)
            throw new ArgumentException("Arcade presentation needs the existing economy and six existing actions.");
        Directory.CreateDirectory(Folder);
        AssetDatabase.Refresh();
        font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
        round = RoundedSprite();

        GameObject root = new GameObject("Arcade - Wall Control Lounge");
        root.transform.position = boardCenter;
        Frame(root.transform, pc);

        GameObject canvasObject = new GameObject("Arcade Dashboard", typeof(RectTransform), typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster), typeof(VRCUiShape));
        canvasObject.layer = 5;
        RectTransform canvas = canvasObject.GetComponent<RectTransform>();
        canvas.SetParent(root.transform, false);
        canvas.localPosition = new Vector3(0, 0, -0.20f);
        canvas.localScale = Vector3.one * 0.005f;
        canvas.sizeDelta = new Vector2(1600, 800);
        canvasObject.GetComponent<Canvas>().renderMode = RenderMode.WorldSpace;
        canvasObject.GetComponent<CanvasScaler>().dynamicPixelsPerUnit = 1;
        var collider = canvasObject.AddComponent<BoxCollider>();
        collider.size = new Vector3(1600, 800, 1);
        collider.center = new Vector3(0, 0, 1);

        Surface(canvas, "Midnight dashboard", 0, 0, 1600, 800, Ink, false);
        Surface(canvas, "Header band", 0, 327, 1600, 146, Panel, false);
        Surface(canvas, "Top light rail", 0, 396, 1550, 5, Mint, false);
        Surface(canvas, "Header divider", 0, 253, 1504, 2, Line, false);
        TextAt(canvas, "NEON NOOK", -748, 362, 620, 40, 25, Mint, TextAnchor.MiddleLeft);
        TextAt(canvas, "YOUR LITTLE EMPIRE", -748, 308, 1050, 84, 62, Ivory, TextAnchor.MiddleLeft);
        Chip(canvas, "PERSONAL ARCADE", 582, 337, 330, 54, Mint, Panel);
        TextAt(canvas, "PLAY  /  BUILD  /  UNWIND", 412, 288, 340, 34, 22, Muted, TextAnchor.MiddleRight);

        // Top metrics: the wallet is deliberately larger than supporting stats.
        Metric(canvas, "CREDITS AVAILABLE", -745, 208, 660, Mint,
            "walletValueText", "25", economy, 62);
        Metric(canvas, "EARNING WHILE YOU CHILL", -55, 208, 470, Apricot,
            "incomeValueText", "1.0 / sec", economy, 54);
        Metric(canvas, "REOPEN STARS", 445, 208, 300, Lilac,
            "starValueText", "0", economy, 54);

        // Separate business cards put cost, output, and collection count in consistent places.
        UpgradeCard(canvas, economy, -500, "01", "ARCADE CABINET", "A classic. Always earning.", Mint, 0,
            "cabinetOwnedText", "cabinetPriceText", "cabinetIncomeText", "25", "+1.0 / sec");
        UpgradeCard(canvas, economy, 0, "02", "SNACK BAR", "Good snacks. Better returns.", Apricot, 1,
            "snackOwnedText", "snackPriceText", "snackIncomeText", "180", "+7.0 / sec");
        UpgradeCard(canvas, economy, 500, "03", "VIP LOUNGE", "Make room for the regulars.", Lilac, 2,
            "loungeOwnedText", "loungePriceText", "loungeIncomeText", "1,200", "+35.0 / sec");

        // A separate strip carries purchase feedback; no text is squeezed into the cards.
        Surface(canvas, "Feedback panel", 0, -145, 1490, 65, Panel);
        Surface(canvas, "Feedback indicator", -719, -145, 5, 35, Mint, false);
        economy.feedbackText = TextAt(canvas, "Welcome. Your arcade is already earning — relax or press PLAY.", -699, -145, 1410, 56, 25, Ivory, TextAnchor.MiddleLeft);

        TextAt(canvas, "MAKE YOUR NEXT MOVE", -745, -208, 650, 38, 25, Muted, TextAnchor.MiddleLeft);
        TextAt(canvas, "ALL CONTROLS ARE PERSONAL", 285, -208, 460, 38, 21, Muted, TextAnchor.MiddleRight);
        Image[] states = new Image[3];
        string[] names = { "PLAY", "BUY CABINET", "BUY SNACK BAR", "BUY LOUNGE", "BOOST", "REOPEN" };
        Color[] colors = { Mint, Mint, Apricot, Lilac, Apricot, Lilac };
        for (int i = 0; i < 6; i++)
        {
            ArcadeAction action = FindAction(actions, i);
            if (action == null) throw new InvalidOperationException("Missing arcade action " + i);
            float x = -625 + i * 250;
            action.transform.SetParent(root.transform, true);
            action.transform.position = boardCenter + new Vector3(x * 0.005f, -1.45f, -0.25f);
            action.transform.localRotation = Quaternion.identity;
            foreach (Renderer r in action.GetComponents<Renderer>()) r.enabled = false;
            foreach (Collider c in action.GetComponents<Collider>()) c.enabled = false;
            UdonBehaviour backing = UdonSharpEditorUtility.GetBackingUdonBehaviour(action);
            if (backing == null) throw new InvalidOperationException("Action has no backing Udon: " + action.name);
            Image face = ActionKey(canvas, names[i], x, -290, colors[i], backing);
            if (i >= 1 && i <= 3) states[i - 1] = face;
            if (i == 0) Bind(economy, "tapValueText", TextAt(canvas, "+2 credits", x - 111, -321, 222, 34, 23, Ink, TextAnchor.MiddleCenter));
            if (i == 4) Bind(economy, "boostValueText", TextAt(canvas, "250  /  +50%", x - 111, -321, 222, 34, 21, Ink, TextAnchor.MiddleCenter));
            if (i == 5) Bind(economy, "prestigeValueText", TextAt(canvas, "0 / 25K", x - 111, -321, 222, 34, 21, Ink, TextAnchor.MiddleCenter));
        }
        Bind(economy, "purchaseStateImages", states);

        // Prestige meter remains visible without competing with the three businesses.
        Surface(canvas, "Reopen track", 0, -359, 1490, 7, Line, false);
        Image progress = Surface(canvas, "Reopen progress", 0, -359, 1490, 7, Lilac, false);
        progress.sprite = round;
        progress.type = Image.Type.Filled;
        progress.fillMethod = Image.FillMethod.Horizontal;
        progress.fillOrigin = 0;
        progress.fillAmount = 0;
        Bind(economy, "prestigeProgressFill", progress);
        TextAt(canvas, "THIS VISIT ONLY  ·  PROGRESS RESETS WHEN YOU LEAVE", -745, -384, 1230, 28, 18, Muted, TextAnchor.MiddleLeft);
        TextAt(canvas, "RELAX. IT ADDS UP.", 460, -384, 285, 28, 18, Mint, TextAnchor.MiddleRight);

        // Legacy monolithic fields are no longer used; the economy retains feedback.
        economy.statusText = null;
        economy.detailText = null;
        economy.RefreshDisplay();
        UdonSharpEditorUtility.CopyProxyToUdon(economy);
        foreach (var action in actions) UdonSharpEditorUtility.CopyProxyToUdon(action);
        EditorUtility.SetDirty(economy);
        return root;
    }

    static void Metric(Transform parent, string title, float left, float top, float width, Color accent, string field, string initial, ArcadeEconomy economy, int fontSize)
    {
        Surface(parent, title + " card", left + width / 2, 176, width, 130, Panel);
        TextAt(parent, title, left + 24, top, width - 48, 30, 21, accent, TextAnchor.MiddleLeft);
        Bind(economy, field, TextAt(parent, initial, left + 24, 151, width - 48, 80, fontSize, Ivory, TextAnchor.MiddleLeft));
    }

    static void UpgradeCard(Transform parent, ArcadeEconomy economy, float x, string number, string title, string subtitle, Color accent, int icon,
        string ownedField, string priceField, string incomeField, string initialPrice, string initialIncome)
    {
        Surface(parent, title + " border", x, -5, 480, 208, Line);
        Surface(parent, title + " surface", x, -5, 476, 204, Card);
        Surface(parent, title + " accent", x - 234, -5, 4, 170, accent, false);
        Icon(parent, icon, new Vector2(x - 188, 53), accent);
        TextAt(parent, title, x - 142, 62, 359, 38, 29, Ivory, TextAnchor.MiddleLeft);
        TextAt(parent, subtitle, x - 142, 25, 359, 31, 19, Muted, TextAnchor.MiddleLeft);
        TextAt(parent, number, x + 173, -60, 48, 42, 23, Line, TextAnchor.MiddleRight);
        Surface(parent, title + " separator", x, -7, 422, 1, Line, false);
        Bind(economy, ownedField, TextAt(parent, "0 OWNED", x - 211, -35, 205, 33, 22, Muted, TextAnchor.MiddleLeft));
        Bind(economy, incomeField, TextAt(parent, initialIncome, x + 8, -35, 203, 33, 23, accent, TextAnchor.MiddleRight));
        TextAt(parent, "NEXT", x - 211, -76, 65, 30, 20, Muted, TextAnchor.MiddleLeft);
        Bind(economy, priceField, TextAt(parent, initialPrice, x - 135, -74, 285, 42, 34, Ivory, TextAnchor.MiddleLeft));
    }

    static Image ActionKey(Transform parent, string label, float x, float y, Color accent, UdonBehaviour backing)
    {
        Surface(parent, label + " shadow", x, y - 6, 235, 116, Color.black);
        Image face = Surface(parent, label + " key", x, y, 235, 112, accent);
        face.raycastTarget = true;
        var button = face.gameObject.AddComponent<Button>();
        button.targetGraphic = face;
        ColorBlock c = button.colors;
        c.normalColor = Color.white;
        c.highlightedColor = new Color(1.15f, 1.15f, 1.15f);
        c.selectedColor = Color.white;
        c.pressedColor = new Color(0.66f, 0.76f, 0.8f);
        c.fadeDuration = 0.08f;
        button.colors = c;
        button.navigation = new Navigation { mode = Navigation.Mode.None };
        UnityEventTools.AddStringPersistentListener(button.onClick, backing.SendCustomEvent, "Activate");
        TextAt(parent, label, x - 111, y + 15, 222, 42, label.Length > 10 ? 24 : 29, Ink, TextAnchor.MiddleCenter);
        return face;
    }

    static void Icon(Transform parent, int kind, Vector2 p, Color accent)
    {
        Surface(parent, "Icon well", p.x, p.y, 68, 68, Ink);
        if (kind == 0)
        {
            Surface(parent, "Cabinet body", p.x, p.y - 2, 34, 47, accent, false);
            Surface(parent, "Cabinet screen", p.x, p.y + 8, 24, 20, Ink, false);
            Surface(parent, "Cabinet controls", p.x, p.y - 9, 24, 4, Ink, false);
        }
        else if (kind == 1)
        {
            Surface(parent, "Cup", p.x - 3, p.y - 3, 28, 32, accent, false);
            Surface(parent, "Cup lid", p.x - 3, p.y + 15, 36, 5, accent, false);
            Surface(parent, "Straw", p.x + 4, p.y + 23, 4, 14, accent, false);
        }
        else
        {
            Surface(parent, "Sofa back", p.x, p.y + 4, 40, 23, accent);
            Surface(parent, "Sofa seat", p.x, p.y - 9, 48, 15, accent);
            Surface(parent, "Sofa leg L", p.x - 17, p.y - 20, 5, 8, accent, false);
            Surface(parent, "Sofa leg R", p.x + 17, p.y - 20, 5, 8, accent, false);
        }
    }

    static void Chip(Transform parent, string label, float x, float y, float w, float h, Color text, Color bg)
    {
        Surface(parent, label + " border", x, y, w, h, Line);
        Surface(parent, label + " fill", x, y, w - 3, h - 3, bg);
        TextAt(parent, label, x - w / 2, y, w, h, 23, text, TextAnchor.MiddleCenter);
    }

    static Image Surface(Transform parent, string name, float x, float y, float w, float h, Color color, bool rounded = true)
    {
        RectTransform rect = Rect(parent, name, x, y, w, h);
        Image image = rect.gameObject.AddComponent<Image>();
        image.color = color;
        image.raycastTarget = false;
        if (rounded) { image.sprite = round; image.type = Image.Type.Sliced; }
        return image;
    }

    // Text positions use a LEFT edge and vertical center to avoid alignment drift.
    static Text TextAt(Transform parent, string value, float left, float y, float w, float h, int size, Color color, TextAnchor align)
    {
        RectTransform rect = Rect(parent, value.Length > 35 ? value.Substring(0, 35) : value, left + w / 2, y, w, h);
        Text text = rect.gameObject.AddComponent<Text>();
        text.font = font;
        text.text = value;
        text.fontSize = size;
        text.color = color;
        text.alignment = align;
        text.horizontalOverflow = HorizontalWrapMode.Wrap;
        text.verticalOverflow = VerticalWrapMode.Truncate;
        text.raycastTarget = false;
        text.supportRichText = false;
        return text;
    }

    static RectTransform Rect(Transform parent, string name, float x, float y, float w, float h)
    {
        var go = new GameObject(name, typeof(RectTransform));
        go.layer = 5;
        RectTransform rect = go.GetComponent<RectTransform>();
        rect.SetParent(parent, false);
        rect.anchorMin = rect.anchorMax = rect.pivot = Vector2.one * 0.5f;
        rect.anchoredPosition = new Vector2(x, y);
        rect.sizeDelta = new Vector2(w, h);
        return rect;
    }

    static ArcadeAction FindAction(ArcadeAction[] actions, int index)
    {
        foreach (var a in actions) if (a != null && a.action == index) return a;
        return null;
    }

    static void Bind(ArcadeEconomy economy, string fieldName, UnityEngine.Object value)
    {
        FieldInfo field = typeof(ArcadeEconomy).GetField(fieldName);
        if (field == null) throw new MissingFieldException("ArcadeEconomy requires presentation field " + fieldName);
        field.SetValue(economy, value);
    }
    static void Bind(ArcadeEconomy economy, string fieldName, Image[] value)
    {
        FieldInfo field = typeof(ArcadeEconomy).GetField(fieldName);
        if (field == null) throw new MissingFieldException("ArcadeEconomy requires presentation field " + fieldName);
        field.SetValue(economy, value);
    }

    static void Frame(Transform parent, bool pc)
    {
        string path = Folder + (pc ? "/HousingPC.mat" : "/HousingQuest.mat");
        Material mat = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (mat == null)
        {
            mat = new Material(Shader.Find(pc ? "Standard" : "Unlit/Color"));
            mat.color = Hex("263C4D");
            if (pc) { mat.SetFloat("_Metallic", 0.7f); mat.SetFloat("_Glossiness", 0.55f); }
            AssetDatabase.CreateAsset(mat, path);
        }
        GameObject frame = GameObject.CreatePrimitive(PrimitiveType.Cube);
        frame.name = "Arcade satin-metal housing";
        frame.transform.SetParent(parent, false);
        frame.transform.localScale = new Vector3(8.18f, 4.18f, 0.32f);
        frame.GetComponent<Renderer>().sharedMaterial = mat;
        frame.isStatic = true;
    }

    static Sprite RoundedSprite()
    {
        string path = Folder + "/RoundedPanel.png";
        if (!File.Exists(path))
        {
            var texture = new Texture2D(64, 64, TextureFormat.RGBA32, false);
            Color[] colors = new Color[64 * 64];
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++)
            {
                float dx = Mathf.Max(Mathf.Abs(x - 31.5f) - 19.5f, 0);
                float dy = Mathf.Max(Mathf.Abs(y - 31.5f) - 19.5f, 0);
                colors[y * 64 + x] = new Color(1, 1, 1, Mathf.Clamp01(12.5f - Mathf.Sqrt(dx * dx + dy * dy)));
            }
            texture.SetPixels(colors);
            texture.Apply();
            File.WriteAllBytes(path, texture.EncodeToPNG());
            UnityEngine.Object.DestroyImmediate(texture);
            AssetDatabase.ImportAsset(path, ImportAssetOptions.ForceSynchronousImport);
        }
        TextureImporter importer = (TextureImporter)AssetImporter.GetAtPath(path);
        importer.textureType = TextureImporterType.Sprite;
        importer.spriteImportMode = SpriteImportMode.Single;
        importer.spriteBorder = new Vector4(16, 16, 16, 16);
        importer.mipmapEnabled = false;
        importer.alphaIsTransparency = true;
        importer.textureCompression = TextureImporterCompression.Uncompressed;
        importer.SaveAndReimport();
        return AssetDatabase.LoadAssetAtPath<Sprite>(path);
    }

    static Color Hex(string value) { Color color; ColorUtility.TryParseHtmlString("#" + value, out color); return color; }
}
