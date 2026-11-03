using System;
using System.Collections.Generic;
using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

/// <summary>
/// Original low-poly botanical meshes: connected petioles, curved leaves and
/// restrained blush variegation in grounded ceramic pots. No external assets.
/// </summary>
public static class LoungeBotanicals
{
    private const string Root = "Assets/IdleLounge/Botanicals";
    private const string GroupName = "Lounge botanicals";

    /// <summary>Builds four reusable planters at the lounge's clear corner positions.</summary>
    public static void Build(Transform parent, bool pc)
    {
        EnsureFolders();
        Transform previous = parent != null ? parent.Find(GroupName) : null;
        if (previous != null) UnityEngine.Object.DestroyImmediate(previous.gameObject);
        var group = new GameObject(GroupName).transform;
        if (parent != null) group.SetParent(parent, false);
        Vector3[] positions = {
            new Vector3(-10.7f, 0f, 9.4f), new Vector3(10.7f, 0f, 9.4f),
            new Vector3(-10.7f, 0f, -9.4f), new Vector3(10.7f, 0f, -9.4f)
        };
        for (int i = 0; i < positions.Length; i++)
        {
            GameObject plant = BuildAt(group, positions[i], pc, i % 2);
            plant.transform.rotation = Quaternion.Euler(0f, 25f + i * 67f, 0f);
        }
        AssetDatabase.SaveAssets();
    }

    /// <summary>
    /// Creates a pot whose bottom is exactly at the supplied world position.
    /// Variant 0 has long blush-striped leaves; variant 1 has broader forest leaves.
    /// Meshes and platform-specific materials are reused between instances.
    /// </summary>
    public static GameObject BuildAt(Transform parent, Vector3 position, bool pc, int variant = 0)
    {
        EnsureFolders();
        variant = Mathf.Abs(variant) % 2;
        var plant = new GameObject(variant == 0 ? "Blush lanceleaf planter" : "Forest lanceleaf planter");
        if (parent != null) plant.transform.SetParent(parent, false);
        plant.transform.position = position;

        Material ceramic = Material("Ceramic warm ivory", new Color(.64f, .57f, .51f), pc, .28f);
        Material soil = Material("Earth", new Color(.10f, .065f, .045f), pc, .05f);
        Material stem = Material("Living stems", new Color(.15f, .255f, .12f), pc, .23f);
        Material green = Material("Forest green", new Color(.10f, .235f, .16f), pc, .40f);
        Material lightGreen = Material("Sage midrib", new Color(.27f, .40f, .235f), pc, .38f);
        Material blush = Material("Subtle blush variegation", new Color(.46f, .265f, .31f), pc, .36f);

        CreatePart(plant.transform, "Rounded ceramic vessel", PotMesh(), new[] { ceramic }, pc);
        CreatePart(plant.transform, "Visible planted soil", SoilMesh(), new[] { soil }, pc);
        CreatePart(plant.transform, "Connected tapered stems", StemMesh(variant), new[] { stem }, pc);
        CreatePart(plant.transform, "Curved lanceolate foliage", FoliageMesh(variant), new[] { green, lightGreen, blush }, pc);

        // Only the grounded pot obstructs walking; foliage does not snag players.
        BoxCollider collider = plant.AddComponent<BoxCollider>();
        collider.center = new Vector3(0f, .25f, 0f);
        collider.size = new Vector3(.60f, .50f, .60f);
        return plant;
    }

    private static void EnsureFolders()
    {
        bool missing = !Directory.Exists(Root + "/Meshes") || !Directory.Exists(Root + "/Materials");
        Directory.CreateDirectory(Root + "/Meshes");
        Directory.CreateDirectory(Root + "/Materials");
        if (missing) AssetDatabase.Refresh();
    }

    private static Material Material(string name, Color color, bool pc, float smoothness)
    {
        string path = Root + "/Materials/" + name + (pc ? "-PC" : "-Quest") + ".mat";
        Material material = AssetDatabase.LoadAssetAtPath<Material>(path);
        Shader shader = Shader.Find(pc ? "Standard" : "NeonNook/SoftSurface");
        if (shader == null) throw new InvalidOperationException("Botanical shader missing: " + (pc ? "Standard" : "NeonNook/SoftSurface"));
        if (material == null)
        {
            material = new Material(shader);
            material.name = name + (pc ? " PC" : " Quest");
            AssetDatabase.CreateAsset(material, path);
        }
        material.shader = shader;
        material.color = color;
        material.enableInstancing = true;
        if (pc)
        {
            material.SetFloat("_Metallic", 0f);
            material.SetFloat("_Glossiness", smoothness);
        }
        EditorUtility.SetDirty(material);
        return material;
    }

    private static GameObject CreatePart(Transform parent, string name, Mesh mesh, Material[] materials, bool pc)
    {
        var part = new GameObject(name, typeof(MeshFilter), typeof(MeshRenderer));
        part.transform.SetParent(parent, false);
        part.GetComponent<MeshFilter>().sharedMesh = mesh;
        MeshRenderer renderer = part.GetComponent<MeshRenderer>();
        renderer.sharedMaterials = materials;
        renderer.shadowCastingMode = pc ? ShadowCastingMode.On : ShadowCastingMode.Off;
        renderer.receiveShadows = pc;
        renderer.lightProbeUsage = LightProbeUsage.BlendProbes;
        if (pc)
        {
            renderer.receiveGI = ReceiveGI.LightProbes;
            GameObjectUtility.SetStaticEditorFlags(part, StaticEditorFlags.ContributeGI | StaticEditorFlags.BatchingStatic | StaticEditorFlags.ReflectionProbeStatic);
        }
        else GameObjectUtility.SetStaticEditorFlags(part, StaticEditorFlags.BatchingStatic);
        return part;
    }

    private static Mesh PotMesh()
    {
        const string name = "CeramicVessel-v1";
        Mesh existing = LoadMesh(name);
        if (existing != null) return existing;
        var builder = new MeshBuilder(1);
        // Radius/height profile: grounded foot, rounded belly, rolled rim, inner lip.
        Vector2[] profile = {
            new Vector2(.22f, 0f), new Vector2(.26f, .025f),
            new Vector2(.29f, .065f), new Vector2(.335f, .19f),
            new Vector2(.35f, .42f), new Vector2(.347f, .49f),
            new Vector2(.332f, .515f), new Vector2(.31f, .505f),
            new Vector2(.302f, .475f), new Vector2(.302f, .445f)
        };
        const int sides = 24;
        for (int row = 0; row < profile.Length; row++)
            for (int side = 0; side <= sides; side++)
            {
                float a = side * Mathf.PI * 2f / sides;
                builder.Vertex(new Vector3(Mathf.Sin(a) * profile[row].x, profile[row].y, Mathf.Cos(a) * profile[row].x),
                    new Vector2((float)side / sides, profile[row].y));
            }
        for (int row = 0; row < profile.Length - 1; row++)
            for (int side = 0; side < sides; side++)
            {
                int a = row * (sides + 1) + side;
                builder.Quad(a, a + 1, a + sides + 2, a + sides + 1, 0, false);
            }
        return SaveMesh(name, builder.Finish(name));
    }

    private static Mesh SoilMesh()
    {
        const string name = "PlantedSoil-v1";
        Mesh existing = LoadMesh(name);
        if (existing != null) return existing;
        var builder = new MeshBuilder(1);
        builder.Vertex(new Vector3(0f, .47f, 0f), new Vector2(.5f, .5f));
        const int sides = 24;
        for (int i = 0; i <= sides; i++)
        {
            float a = i * Mathf.PI * 2f / sides;
            Vector3 point = new Vector3(Mathf.Sin(a) * .302f, .465f, Mathf.Cos(a) * .302f);
            builder.Vertex(point, new Vector2(point.x + .5f, point.z + .5f));
        }
        for (int i = 0; i < sides; i++) builder.Triangle(0, i + 1, i + 2, 0);
        return SaveMesh(name, builder.Finish(name));
    }

    private struct Leaf
    {
        public Vector3 root, joint, direction, side;
        public float length, width, lift, droop;
        public bool blush;
    }

    private static Leaf Definition(int index, int variant)
    {
        float angle = (index * 137.508f + variant * 31f) * Mathf.Deg2Rad;
        Vector3 direction = new Vector3(Mathf.Sin(angle), 0f, Mathf.Cos(angle));
        bool inner = index % 3 == 0;
        float height = inner ? 1.24f + .04f * (index % 2) : .83f + .09f * (index % 3);
        return new Leaf {
            root = direction * (.025f + .012f * (index % 3)) + Vector3.up * .462f,
            joint = direction * (inner ? .09f : .20f) + Vector3.up * height,
            direction = direction,
            side = Vector3.Cross(Vector3.up, direction),
            length = variant == 0 ? (inner ? .52f : .65f + .035f * (index % 2)) : (inner ? .46f : .58f),
            width = variant == 0 ? (inner ? .105f : .145f) : (inner ? .15f : .205f),
            lift = inner ? .26f : .17f,
            droop = inner ? .025f : .21f,
            blush = variant == 0 ? index == 1 || index == 4 || index == 6 : index == 2
        };
    }

    private static Mesh StemMesh(int variant)
    {
        string name = "ConnectedPetioles-" + variant + "-v1";
        Mesh existing = LoadMesh(name);
        if (existing != null) return existing;
        var builder = new MeshBuilder(1);
        int count = variant == 0 ? 9 : 7;
        const int rings = 5, sides = 6;
        for (int i = 0; i < count; i++)
        {
            Leaf leaf = Definition(i, variant);
            int start = builder.Count;
            for (int row = 0; row < rings; row++)
            {
                float t = (float)row / (rings - 1);
                Vector3 center = Vector3.Lerp(leaf.root, leaf.joint, t) - leaf.direction * (.05f * Mathf.Sin(t * Mathf.PI));
                Vector3 tangent = (leaf.joint - leaf.root - leaf.direction * (.05f * Mathf.PI * Mathf.Cos(t * Mathf.PI))).normalized;
                Vector3 across = leaf.side;
                Vector3 around = Vector3.Cross(tangent, across).normalized;
                float radius = Mathf.Lerp(.015f, .006f, t);
                for (int side = 0; side <= sides; side++)
                {
                    float a = side * Mathf.PI * 2f / sides;
                    builder.Vertex(center + radius * (Mathf.Cos(a) * across + Mathf.Sin(a) * around), new Vector2((float)side / sides, t));
                }
            }
            for (int row = 0; row < rings - 1; row++) for (int side = 0; side < sides; side++)
            {
                int a = start + row * (sides + 1) + side;
                builder.Quad(a, a + 1, a + sides + 2, a + sides + 1, 0, false);
            }
        }
        return SaveMesh(name, builder.Finish(name));
    }

    private static Mesh FoliageMesh(int variant)
    {
        string name = "CurvedLanceLeaves-" + variant + "-v1";
        Mesh existing = LoadMesh(name);
        if (existing != null) return existing;
        var builder = new MeshBuilder(3);
        int count = variant == 0 ? 9 : 7;
        const int rows = 9, columns = 6;
        for (int i = 0; i < count; i++)
        {
            Leaf leaf = Definition(i, variant);
            for (int face = 0; face < 2; face++)
            {
                int start = builder.Count;
                for (int row = 0; row < rows; row++)
                {
                    float t = (float)row / (rows - 1);
                    // A thin but finite attachment and tip keep normals/UVs non-degenerate.
                    float halfWidth = .003f + leaf.width * Mathf.Pow(Mathf.Max(0f, Mathf.Sin(t * Mathf.PI)), .84f);
                    float stripeOffset = .035f * Mathf.Sin(t * 13f + i);
                    float[] across = { -1f, -.46f, -.16f + stripeOffset, .16f + stripeOffset, .46f, 1f };
                    Vector3 center = leaf.joint + leaf.direction * (leaf.length * t)
                        + Vector3.up * (leaf.lift * Mathf.Sin(t * Mathf.PI * .8f) - leaf.droop * t * t);
                    for (int col = 0; col < columns; col++)
                    {
                        float u = across[col];
                        // Cupped section and a soft longitudinal ridge create actual volume.
                        float edgeCurl = .045f * Mathf.Abs(u) * Mathf.Abs(u) * Mathf.Sin(t * Mathf.PI);
                        float ridge = .012f * (1f - Mathf.Abs(u)) * Mathf.Sin(t * Mathf.PI);
                        Vector3 point = center + leaf.side * (halfWidth * u) + Vector3.up * (edgeCurl + ridge);
                        builder.Vertex(point, new Vector2((u + 1f) * .5f, t));
                    }
                }
                for (int row = 0; row < rows - 1; row++) for (int col = 0; col < columns - 1; col++)
                {
                    int a = start + row * columns + col;
                    // Only a few narrow central strips are blush; the canopy remains green.
                    int material = col == 2 ? (leaf.blush ? 2 : 1) : 0;
                    builder.Quad(a, a + columns, a + columns + 1, a + 1, material, face == 1);
                }
            }
        }
        return SaveMesh(name, builder.Finish(name));
    }

    private static Mesh LoadMesh(string name) { return AssetDatabase.LoadAssetAtPath<Mesh>(Root + "/Meshes/" + name + ".asset"); }
    private static Mesh SaveMesh(string name, Mesh mesh)
    {
        UnwrapParam.SetDefaults(out UnwrapParam parameters);
        parameters.packMargin = .04f;
        Unwrapping.GenerateSecondaryUVSet(mesh, parameters);
        mesh.RecalculateBounds();
        AssetDatabase.CreateAsset(mesh, Root + "/Meshes/" + name + ".asset");
        return mesh;
    }

    private sealed class MeshBuilder
    {
        private readonly List<Vector3> vertices = new List<Vector3>();
        private readonly List<Vector2> uv = new List<Vector2>();
        private readonly List<int>[] triangles;
        public int Count { get { return vertices.Count; } }
        public MeshBuilder(int submeshes)
        {
            triangles = new List<int>[submeshes];
            for (int i = 0; i < submeshes; i++) triangles[i] = new List<int>();
        }
        public void Vertex(Vector3 point, Vector2 texture) { vertices.Add(point); uv.Add(texture); }
        public void Triangle(int a, int b, int c, int material)
        { triangles[material].Add(a); triangles[material].Add(b); triangles[material].Add(c); }
        public void Quad(int a, int b, int c, int d, int material, bool reverse)
        {
            if (reverse) { Triangle(a, c, b, material); Triangle(a, d, c, material); }
            else { Triangle(a, b, c, material); Triangle(a, c, d, material); }
        }
        public Mesh Finish(string name)
        {
            var mesh = new Mesh { name = name };
            mesh.SetVertices(vertices); mesh.SetUVs(0, uv); mesh.subMeshCount = triangles.Length;
            for (int i = 0; i < triangles.Length; i++) mesh.SetTriangles(triangles[i], i);
            mesh.RecalculateNormals(); mesh.RecalculateTangents(); mesh.RecalculateBounds();
            return mesh;
        }
    }
}
