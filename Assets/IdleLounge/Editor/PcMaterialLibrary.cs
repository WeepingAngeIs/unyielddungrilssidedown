using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

/// <summary>
/// Real scene materials for the PC edition. Every bitmap is deterministic,
/// seamless and generated from a surface model, rather than a rendered thumbnail.
/// Meshes should use metre-based UV0; RepeatMeters describes each texture repeat.
/// </summary>
public static class PcMaterialLibrary
{
    private const string Root = "Assets/IdleLounge/PC";
    private const int Resolution = 512;
    private static readonly Dictionary<string, Material> Cache = new Dictionary<string, Material>();
    public static readonly string[] Keys = {
        "floor", "wood", "wall", "fabricTeal", "fabricCream", "fabricRose",
        "metal", "rug", "brass", "glass", "emitTeal", "emitAmber"
    };

    private struct Surface
    {
        public string pattern;
        public Color color;
        public float metallic;
        public float smoothness;
        public float repeat;
        public Surface(string pattern, Color color, float metallic, float smoothness, float repeat)
        {
            this.pattern = pattern;
            this.color = color;
            this.metallic = metallic;
            this.smoothness = smoothness;
            this.repeat = repeat;
        }
    }

    /// <summary>Physical width/height, in metres, represented by one texture repeat.</summary>
    public static float RepeatMeters(string key) { return Definition(key).repeat; }

    [MenuItem("Neon Nook/PC/Create PBR material library")]
    public static void EnsureAll()
    {
        foreach (string key in Keys) Get(key);
        AssetDatabase.SaveAssets();
    }

    [MenuItem("Neon Nook/PC/Rebuild procedural texture maps")]
    public static void RebuildAll()
    {
        Cache.Clear();
        EnsureFolders();
        foreach (string key in Keys)
        {
            Surface spec = Definition(key);
            if (spec.pattern != "glass" && spec.pattern != "emission") GenerateMaps(key, spec);
            Get(key);
        }
        AssetDatabase.SaveAssets();
    }

    /// <summary>
    /// Returns a shared saved material. Main/detail texture scales are already set
    /// for UV coordinates measured in metres, including matching normal-map scale.
    /// Do not modify a returned shared material to compensate for stretched mesh UVs.
    /// </summary>
    public static Material Get(string key)
    {
        if (Cache.TryGetValue(key, out Material cached) && cached != null) return cached;
        Surface spec = Definition(key);
        EnsureFolders();
        string path = Root + "/Materials/PC_" + key + ".mat";
        Material material = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (material == null)
        {
            material = new Material(Shader.Find("Standard"));
            material.name = "PC_" + key;
            AssetDatabase.CreateAsset(material, path);
        }
        material.shader = Shader.Find("Standard");
        material.enableInstancing = true;
        material.SetFloat("_Mode", 0);
        material.SetInt("_SrcBlend", (int)BlendMode.One);
        material.SetInt("_DstBlend", (int)BlendMode.Zero);
        material.SetInt("_ZWrite", 1);
        material.SetOverrideTag("RenderType", "Opaque");
        material.DisableKeyword("_ALPHATEST_ON");
        material.DisableKeyword("_ALPHABLEND_ON");
        material.DisableKeyword("_ALPHAPREMULTIPLY_ON");
        material.renderQueue = -1;
        material.color = Color.white;
        material.SetFloat("_Metallic", spec.metallic);
        material.SetFloat("_Glossiness", spec.smoothness);
        material.SetFloat("_GlossMapScale", 1f);
        material.SetFloat("_SmoothnessTextureChannel", 0f);
        material.DisableKeyword("_SMOOTHNESS_TEXTURE_ALBEDO_CHANNEL_A");
        material.SetFloat("_BumpScale", 1f);
        material.SetFloat("_OcclusionStrength", 0.35f);
        material.DisableKeyword("_EMISSION");
        material.SetColor("_EmissionColor", Color.black);
        material.globalIlluminationFlags = MaterialGlobalIlluminationFlags.EmissiveIsBlack;

        if (spec.pattern == "glass")
        {
            // Thin architectural glazing: restrained tint, specular highlights
            // and reflection probes remain visible. Use closed window frames and
            // keep glass planes offset from opaque surfaces to avoid z fighting.
            material.color = new Color(0.76f, 0.90f, 0.94f, 0.12f);
            material.SetFloat("_Mode", 3);
            material.SetFloat("_Metallic", 0f);
            material.SetFloat("_Glossiness", 0.94f);
            material.SetOverrideTag("RenderType", "Transparent");
            material.SetInt("_SrcBlend", (int)BlendMode.One);
            material.SetInt("_DstBlend", (int)BlendMode.OneMinusSrcAlpha);
            material.SetInt("_ZWrite", 0);
            material.EnableKeyword("_ALPHAPREMULTIPLY_ON");
            material.renderQueue = (int)RenderQueue.Transparent;
        }
        else if (spec.pattern == "emission")
        {
            material.color = spec.color * 0.22f;
            material.EnableKeyword("_EMISSION");
            material.SetColor("_EmissionColor", spec.color * (key == "emitAmber" ? 3.2f : 2.5f));
            material.globalIlluminationFlags = MaterialGlobalIlluminationFlags.BakedEmissive;
        }
        else
        {
            if (!File.Exists(MapPath(key, "Albedo")) || !File.Exists(MapPath(key, "Normal")) ||
                !File.Exists(MapPath(key, "Surface")) || !File.Exists(MapPath(key, "Occlusion")))
                GenerateMaps(key, spec);

            material.SetTexture("_MainTex", LoadMap(key, "Albedo"));
            material.SetTexture("_BumpMap", LoadMap(key, "Normal"));
            material.SetTexture("_MetallicGlossMap", LoadMap(key, "Surface"));
            material.SetTexture("_OcclusionMap", LoadMap(key, "Occlusion"));
            material.EnableKeyword("_NORMALMAP");
            material.EnableKeyword("_METALLICGLOSSMAP");
            material.EnableKeyword("_OCCLUSIONMAP");
            // The Standard shader's _MainTex_ST transform also applies to normal,
            // metallic/smoothness, and occlusion maps, so their texels stay aligned.
            material.mainTextureScale = Vector2.one / spec.repeat;
        }
        EditorUtility.SetDirty(material);
        Cache[key] = material;
        return material;
    }

    private static Surface Definition(string key)
    {
        switch (key)
        {
            case "floor": return new Surface("slate", new Color(.245f, .285f, .315f), 0f, .31f, 2f);
            case "wood": return new Surface("walnut", new Color(.355f, .225f, .135f), 0f, .39f, 2f);
            case "wall": return new Surface("plaster", new Color(.16f, .225f, .29f), 0f, .14f, 2f);
            case "fabricTeal": return new Surface("weave", new Color(.17f, .43f, .405f), 0f, .17f, .45f);
            case "fabricCream": return new Surface("weave", new Color(.72f, .68f, .59f), 0f, .17f, .45f);
            case "fabricRose": return new Surface("weave", new Color(.51f, .265f, .30f), 0f, .17f, .45f);
            case "metal": return new Surface("brushed", new Color(.23f, .28f, .32f), .88f, .58f, .6f);
            case "rug": return new Surface("rug", new Color(.24f, .29f, .34f), 0f, .08f, .65f);
            case "brass": return new Surface("brushed", new Color(.70f, .49f, .235f), 1f, .62f, .6f);
            case "glass": return new Surface("glass", Color.white, 0f, .94f, 1f);
            case "emitTeal": return new Surface("emission", new Color(.12f, .94f, .78f), .2f, .42f, 1f);
            case "emitAmber": return new Surface("emission", new Color(1f, .48f, .16f), .2f, .42f, 1f);
            default: throw new ArgumentException("Unknown PC material key: " + key);
        }
    }

    private static void EnsureFolders()
    {
        Directory.CreateDirectory(Root + "/Textures");
        Directory.CreateDirectory(Root + "/Materials");
        if (!AssetDatabase.IsValidFolder(Root + "/Materials")) AssetDatabase.Refresh();
    }

    private static string MapPath(string key, string map) { return Root + "/Textures/" + key + "_" + map + ".png"; }
    private static Texture2D LoadMap(string key, string map) { return AssetDatabase.LoadAssetAtPath<Texture2D>(MapPath(key, map)); }

    private static void GenerateMaps(string key, Surface spec)
    {
        int count = Resolution * Resolution;
        var albedo = new Color32[count];
        var surface = new Color32[count];
        var occlusion = new Color32[count];
        var normal = new Color32[count];
        var height = new float[count];
        for (int y = 0; y < Resolution; y++)
        {
            for (int x = 0; x < Resolution; x++)
            {
                float u = (float)x / Resolution;
                float v = (float)y / Resolution;
                Evaluate(spec, u, v, out float variation, out float h, out float gloss, out float ao);
                int index = y * Resolution + x;
                Color color = spec.color * variation;
                color.a = 1f;
                albedo[index] = color;
                height[index] = h;
                surface[index] = new Color(spec.metallic, spec.metallic, spec.metallic, gloss);
                occlusion[index] = new Color(ao, ao, ao, 1f);
            }
        }
        float gradientScale = Resolution / (2f * spec.repeat);
        for (int y = 0; y < Resolution; y++)
        {
            for (int x = 0; x < Resolution; x++)
            {
                int left = y * Resolution + (x + Resolution - 1) % Resolution;
                int right = y * Resolution + (x + 1) % Resolution;
                int down = ((y + Resolution - 1) % Resolution) * Resolution + x;
                int up = ((y + 1) % Resolution) * Resolution + x;
                Vector3 n = new Vector3(-(height[right] - height[left]) * gradientScale,
                    -(height[up] - height[down]) * gradientScale, 1f).normalized;
                normal[y * Resolution + x] = new Color(n.x * .5f + .5f, n.y * .5f + .5f, n.z * .5f + .5f, 1f);
            }
        }
        SaveMap(MapPath(key, "Albedo"), albedo, false, true);
        SaveMap(MapPath(key, "Normal"), normal, true, false);
        SaveMap(MapPath(key, "Surface"), surface, false, false);
        SaveMap(MapPath(key, "Occlusion"), occlusion, false, false);
    }

    private static void Evaluate(Surface spec, float u, float v, out float shade, out float height, out float gloss, out float ao)
    {
        float broad = Noise(u, v, 7, 7, 17) * .6f + Noise(u, v, 23, 23, 41) * .4f;
        float fine = Noise(u, v, 113, 113, 87);
        shade = 1f;
        height = 0f;
        gloss = spec.smoothness;
        ao = 1f;
        switch (spec.pattern)
        {
            case "slate":
                float tu = Mathf.Repeat(u * 2f, 1f);
                float tv = Mathf.Repeat(v * 2f, 1f);
                float edge = Mathf.Min(tu, 1f - tu, tv, 1f - tv);
                float interior = Mathf.SmoothStep(0f, 1f, Mathf.Clamp01((edge - .003f) / .012f));
                float tileTint = .96f + Hash(Mathf.FloorToInt(u * 2), Mathf.FloorToInt(v * 2), 52) * .08f;
                shade = Mathf.Lerp(.48f, tileTint * (.95f + broad * .10f), interior);
                height = -.003f * (1f - interior) + (broad - .5f) * .0009f;
                gloss = Mathf.Lerp(.08f, spec.smoothness + (fine - .5f) * .06f, interior);
                ao = Mathf.Lerp(.8f, 1f, interior);
                break;
            case "walnut":
                float warp = Mathf.Sin(v * Mathf.PI * 4f) * .15f + Noise(u, v, 5, 3, 61) * .7f;
                float grain = Mathf.Sin((u * 88f + warp) * Mathf.PI * 2f);
                float fineGrain = Noise(u, v, 197, 11, 21);
                float plank = Mathf.Repeat(u * 5f, 1f);
                float plankInterior = Mathf.SmoothStep(0f, 1f, Mathf.Clamp01(Mathf.Min(plank, 1f - plank) / .018f));
                float plankTint = .94f + Hash(Mathf.FloorToInt(u * 5), 1, 32) * .12f;
                shade = plankTint * (.98f + grain * .045f + (fineGrain - .5f) * .13f) * Mathf.Lerp(.62f, 1f, plankInterior);
                height = grain * .00008f + (fineGrain - .5f) * .00015f - (1f - plankInterior) * .0007f;
                gloss = spec.smoothness + (fineGrain - .5f) * .08f;
                ao = Mathf.Lerp(.86f, 1f, plankInterior);
                break;
            case "plaster":
                shade = .97f + broad * .045f + (fine - .5f) * .025f;
                height = (broad - .5f) * .00035f + (fine - .5f) * .00035f;
                gloss += (fine - .5f) * .04f;
                break;
            case "weave":
            case "rug":
                float threads = spec.pattern == "rug" ? 64f : 48f;
                float warpThread = Mathf.Sin(u * threads * Mathf.PI * 2f);
                float weftThread = Mathf.Sin(v * threads * Mathf.PI * 2f);
                float woven = warpThread * weftThread;
                shade = .98f + woven * .035f + (fine - .5f) * .04f;
                height = woven * (spec.pattern == "rug" ? .00038f : .00022f);
                gloss += woven * .015f;
                ao = .96f + woven * .025f;
                break;
            case "brushed":
                float scratch = Noise(u, v, 251, 3, 51);
                shade = .97f + scratch * .05f;
                height = (scratch - .5f) * .000025f;
                gloss += (scratch - .5f) * .07f;
                break;
        }
        gloss = Mathf.Clamp01(gloss);
    }

    // Periodic value noise. Integer periods guarantee matching values and slopes
    // across tile edges, including wrapped height derivatives for normal maps.
    private static float Noise(float u, float v, int px, int py, int seed)
    {
        float fx = u * px;
        float fy = v * py;
        int x = Mathf.FloorToInt(fx);
        int y = Mathf.FloorToInt(fy);
        float tx = fx - x;
        float ty = fy - y;
        tx = tx * tx * (3f - 2f * tx);
        ty = ty * ty * (3f - 2f * ty);
        return Mathf.Lerp(
            Mathf.Lerp(Hash(x % px, y % py, seed), Hash((x + 1) % px, y % py, seed), tx),
            Mathf.Lerp(Hash(x % px, (y + 1) % py, seed), Hash((x + 1) % px, (y + 1) % py, seed), tx), ty);
    }

    private static float Hash(int x, int y, int seed)
    {
        unchecked
        {
            uint value = (uint)(x * 374761393 + y * 668265263 + seed * 1442695041);
            value = (value ^ (value >> 13)) * 1274126177u;
            return (value ^ (value >> 16)) / (float)uint.MaxValue;
        }
    }

    private static void SaveMap(string path, Color32[] pixels, bool isNormal, bool isColor)
    {
        var image = new Texture2D(Resolution, Resolution, TextureFormat.RGBA32, false, !isColor);
        image.SetPixels32(pixels);
        image.Apply(false, false);
        File.WriteAllBytes(path, image.EncodeToPNG());
        UnityEngine.Object.DestroyImmediate(image);
        AssetDatabase.ImportAsset(path, ImportAssetOptions.ForceSynchronousImport);
        var importer = (TextureImporter)AssetImporter.GetAtPath(path);
        importer.textureType = isNormal ? TextureImporterType.NormalMap : TextureImporterType.Default;
        importer.convertToNormalmap = false;
        importer.sRGBTexture = isColor;
        importer.wrapMode = TextureWrapMode.Repeat;
        importer.filterMode = FilterMode.Trilinear;
        importer.mipmapEnabled = true;
        importer.anisoLevel = 8;
        importer.maxTextureSize = Resolution;
        importer.textureCompression = TextureImporterCompression.CompressedHQ;
        importer.alphaSource = TextureImporterAlphaSource.FromInput;
        importer.alphaIsTransparency = false;
        importer.isReadable = false;
        importer.SaveAndReimport();
    }
}
