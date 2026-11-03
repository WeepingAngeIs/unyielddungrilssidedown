using System;
using System.Linq;
using UnityEditor;
using UnityEngine;
using Object = UnityEngine.Object;

/// <summary>Shared PC/Quest floor plan. Existing gameplay objects retain their identities.</summary>
public static class NeonNookPerimeterLayout
{
    public static readonly Vector3 ArcadeCenter = new Vector3(-7, 2.8f, 10.3f);

    public static void ReviewQuest()
    {
        UnityEditor.SceneManagement.EditorSceneManager.OpenScene("Assets/IdleLounge/Scenes/NeonNook.unity");
        NeonNookBuilder.Validate();
        string report = PerimeterLayoutAudit.Run();
        System.IO.File.WriteAllText(System.IO.Path.Combine(NeonNookPaths.OutputDirectory, "quest-layout-audit.txt"), report);
        NeonNookBuilder.RenderPreview();
    }

    public static void Apply()
    {
        ArrangeSeats();
        ArrangeTables();
        ReplaceRugs();
        RemoveLegacyArcade();
        BuildArcade(false);
        BuildPlants(null, false);
        var video = GameObject.Find("Watch Together - Shared Video");
        if (video) video.transform.position = new Vector3(4, 3.9f, 10.35f);
        var title = GameObject.Find("THE LIVING ROOM");
        if (title) title.transform.position = new Vector3(4, 6.05f, 10.5f);
        // The dashboard carries the Quest wordmark; PC raises the large architectural sign.
        var brand = Object.FindObjectsOfType<TextMesh>().FirstOrDefault(t => t.name == "NEON NOOK");
        if (brand) brand.GetComponent<Renderer>().enabled = false;
        RemoveNamed("PLAY A LITTLE. STAY A WHILE.", "01  /  BUILD YOUR ARCADE", "03  /  SLOW CORNER");
        Boundary("East window boundary", new Vector3(12, 3.4f, 0), new Vector3(.18f, 5, 22));
        Boundary("South window boundary", new Vector3(0, 3.4f, -11), new Vector3(24, 5, .18f));
        Physics.SyncTransforms();
    }

    static void ArrangeSeats()
    {
        Transform[] sofas = Object.FindObjectsOfType<Transform>()
            .Where(t => t.name.StartsWith("Sofa with two VR seats", StringComparison.Ordinal))
            .OrderBy(t => t.name).ThenBy(t => t.GetSiblingIndex()).ToArray();
        if (sofas.Length != 6) throw new InvalidOperationException("Expected six original sofas.");
        for (int i = 0; i < sofas.Length; i++)
        {
            Transform sofa = sofas[i];
            sofa.name = "Sofa with two VR seats / " + (i + 1).ToString("00");
            sofa.position = i < 3 ? new Vector3(10.4f, 0, -3.8f + i * 3.8f)
                : new Vector3(-4.7f + (i - 3) * 4, 0, -9.6f);
            sofa.rotation = Quaternion.Euler(0, i < 3 ? -90 : 0, 0);
            foreach (Transform exit in sofa.GetComponentsInChildren<Transform>().Where(t => t.name == "Stand here"))
                exit.localPosition = new Vector3(exit.localPosition.x < 0 ? -.65f : .65f, 0, 1.35f);
        }
    }

    static void ArrangeTables()
    {
        Transform[] tops = Object.FindObjectsOfType<Transform>()
            .Where(t => t.name == "Coffee table" || t.name.StartsWith("Side table surface", StringComparison.Ordinal))
            .OrderBy(t => t.name).ThenBy(t => t.GetSiblingIndex()).ToArray();
        Vector3[] positions = { new Vector3(10.4f, 0, -1.9f), new Vector3(10.4f, 0, 1.9f), new Vector3(-2.7f, 0, -9.6f) };
        if (tops.Length != 3) throw new InvalidOperationException("Expected three original table surfaces.");
        for (int i = 0; i < tops.Length; i++)
        {
            Transform top = tops[i];
            Transform support = Object.FindObjectsOfType<Transform>().FirstOrDefault(t =>
                t.name == "Side table base " + i || (t.name == "Table plinth" && Vector3.Distance(t.position, top.position - Vector3.up * .28f) < .05f));
            top.name = "Side table surface " + i;
            top.position = positions[i] + Vector3.up * .48f;
            top.rotation = Quaternion.Euler(0, i == 2 ? 90 : 0, 0);
            top.localScale = new Vector3(1.1f, .15f, .44f);
            if (support)
            {
                support.name = "Side table base " + i;
                support.position = positions[i] + Vector3.up * .2f;
                support.rotation = top.rotation;
                support.localScale = new Vector3(.715f, .4f, .275f);
            }
        }
        RemoveNamed("Table runner");
    }

    static void ReplaceRugs()
    {
        RemoveNamed("Inlaid rug", "Rug edge");
        Material fabric = AssetDatabase.LoadAssetAtPath<Material>("Assets/IdleLounge/Generated/Panel.mat");
        if (!fabric) fabric = AssetDatabase.LoadAssetAtPath<Material>("Assets/IdleLounge/Generated/Navy.mat");
        Rug(new Vector3(9.8f, .025f, 0), new Vector3(3.2f, .04f, 13), fabric);
        Rug(new Vector3(-.7f, .025f, -9), new Vector3(12.5f, .04f, 3), fabric);
        Rug(new Vector3(-7, .025f, 8.25f), new Vector3(8.4f, .04f, 4.6f), fabric);
    }

    static void Rug(Vector3 position, Vector3 size, Material material)
    {
        var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
        go.name = "Inlaid rug";
        go.transform.position = position;
        go.transform.localScale = size;
        go.GetComponent<Renderer>().sharedMaterial = material;
        Object.DestroyImmediate(go.GetComponent<Collider>());
    }

    static void RemoveLegacyArcade()
    {
        // These are non-networked presentation objects. Actions are retained below.
        RemoveNamed("Console pedestal", "Arcade display", "Personal wallet", "Businesses and prices",
            "Arcade feedback", "How to play", "Instructions panel", "Console nameplate", "Console accent strip");
        string[] labels = { "PLAY +2", "CABINET", "SNACK BAR", "LOUNGE", "BOOST", "REOPEN" };
        foreach (var text in Object.FindObjectsOfType<TextMesh>().Where(t => labels.Contains(t.name)).ToArray())
            Object.DestroyImmediate(text.gameObject);
    }

    public static GameObject BuildArcade(bool pc)
    {
        ArcadeEconomy economy = Object.FindObjectOfType<ArcadeEconomy>();
        ArcadeAction[] actions = Object.FindObjectsOfType<ArcadeAction>();
        if (!economy || actions.Length != 6) throw new InvalidOperationException("Existing arcade gameplay is missing.");
        foreach (var action in actions) action.transform.SetParent(null, true);
        RemoveNamed("Arcade - Wall Control Lounge");
        return ArcadePresentation.Build(economy, actions, ArcadeCenter, pc);
    }

    public static void BuildPlants(Transform parent, bool pc)
    {
        RemoveNamed("Planter", "Sculptural foliage", "Broad leaf", "Lounge botanicals");
        LoungeBotanicals.Build(parent, pc);
        var plants = Object.FindObjectsOfType<Transform>().Where(t => t.name.EndsWith("lanceleaf planter", StringComparison.Ordinal)).ToArray();
        foreach (var plant in plants)
            if (plant.position.x < 0 && plant.position.z > 0) plant.position = new Vector3(-10.8f, 0, 7);
    }

    static void Boundary(string name, Vector3 position, Vector3 size)
    {
        if (GameObject.Find(name)) return;
        var go = new GameObject(name);
        go.transform.position = position;
        go.AddComponent<BoxCollider>().size = size;
    }

    static void RemoveNamed(params string[] names)
    {
        foreach (var transform in Object.FindObjectsOfType<Transform>().Where(t => names.Contains(t.name)).ToArray())
            if (transform) Object.DestroyImmediate(transform.gameObject);
    }
}
