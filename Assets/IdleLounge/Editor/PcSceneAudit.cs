using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;

/// <summary>
/// Structural checks on the active scene, including real collider penetration
/// at seat exits. This complements visual inspection; it does not certify every
/// possible player pose, UV chart overlap, shader variant, or camera angle.
/// </summary>
public static class PcSceneAudit
{
    [MenuItem("Neon Nook/PC/Audit open scene")]
    private static void RunFromMenu() { Run(); }

    public static string Run()
    {
        Scene scene = SceneManager.GetActiveScene();
        var errors = new List<string>();
        var warnings = new List<string>();
        var checkedMeshes = new HashSet<Mesh>();
        var checkedMaterials = new HashSet<Material>();
        var report = new StringBuilder();
        int renderers = 0;
        int triangles = 0;
        int giRenderers = 0;
        int unbakedGiRenderers = 0;
        int exits = 0;

        if (!scene.IsValid() || !scene.isLoaded) errors.Add("No loaded active scene.");
        Renderer[] sceneRenderers = UnityEngine.Object.FindObjectsOfType<Renderer>(true)
            .Where(r => r.gameObject.scene == scene && r.enabled && r.gameObject.activeInHierarchy).ToArray();

        foreach (Renderer renderer in sceneRenderers)
        {
            renderers++;
            string label = ObjectPath(renderer.transform);
            Material[] materials = renderer.sharedMaterials;
            if (materials.Length == 0) errors.Add(label + ": renderer has no material slots.");
            foreach (Material material in materials)
            {
                if (material == null)
                {
                    errors.Add(label + ": missing used material.");
                    continue;
                }
                if (material.shader == null || material.shader.name == "Hidden/InternalErrorShader")
                    errors.Add(label + ": missing/error shader on " + material.name + ".");
                if (checkedMaterials.Add(material)) AuditMapImporters(material, errors);
            }

            Mesh mesh = null;
            MeshFilter filter = renderer.GetComponent<MeshFilter>();
            if (filter != null) mesh = filter.sharedMesh;
            if (renderer is SkinnedMeshRenderer skin) mesh = skin.sharedMesh;
            // TextMesh, particles and CanvasRenderer are not authored mesh geometry.
            if (mesh == null)
            {
                if (filter != null || renderer is SkinnedMeshRenderer)
                    errors.Add(label + ": geometry renderer has no mesh.");
                continue;
            }
            if (mesh.vertexCount == 0)
            {
                errors.Add(label + ": mesh has no vertices.");
                continue;
            }

            try
            {
                triangles += mesh.triangles.Length / 3;
                if (checkedMeshes.Add(mesh)) AuditMesh(mesh, label, errors);
                bool contributes = (GameObjectUtility.GetStaticEditorFlags(renderer.gameObject) & StaticEditorFlags.ContributeGI) != 0;
                bool usesLightmaps = !(renderer is MeshRenderer mr) || mr.receiveGI == ReceiveGI.Lightmaps;
                if (contributes && usesLightmaps)
                {
                    giRenderers++;
                    if (renderer.lightmapIndex < 0 || renderer.lightmapIndex >= 0xfffe) unbakedGiRenderers++;
                    Vector2[] lightmapUv = mesh.uv2;
                    if (lightmapUv == null || lightmapUv.Length != mesh.vertexCount)
                        errors.Add(label + ": ContributeGI geometry lacks complete lightmap UV2 (Unity's second UV channel).");
                    else if (lightmapUv.Any(v => !Finite(v.x) || !Finite(v.y)))
                        errors.Add(label + ": non-finite lightmap UV2 values.");
                }
                if (materials.Any(m => m != null && m.HasProperty("_BumpMap") && m.GetTexture("_BumpMap") != null)
                    && mesh.tangents.Length != mesh.vertexCount)
                    warnings.Add(label + ": normal-mapped geometry has no complete tangent array; generate tangents before evaluating normals.");
            }
            catch (Exception e)
            {
                errors.Add(label + ": mesh could not be inspected: " + e.Message);
            }
        }

        Physics.SyncTransforms();
        var probeObject = new GameObject("PC audit temporary exit capsule");
        probeObject.hideFlags = HideFlags.HideAndDontSave;
        CapsuleCollider probe = probeObject.AddComponent<CapsuleCollider>();
        probe.direction = 1;
        probe.radius = .2f;
        probe.height = 1.5f;
        probe.center = Vector3.zero;
        probe.isTrigger = true;
        probe.enabled = false;
        try
        {
            foreach (VRC.SDKBase.VRCStation station in UnityEngine.Object.FindObjectsOfType<VRC.SDKBase.VRCStation>(true))
            {
                if (station.gameObject.scene != scene || !station.gameObject.activeInHierarchy) continue;
                if (station.stationExitPlayerLocation == null)
                {
                    errors.Add(ObjectPath(station.transform) + ": missing station exit transform.");
                    continue;
                }
                exits++;
                Transform exit = station.stationExitPlayerLocation;
                const float radius = .2f;
                const float height = 1.5f;
                float footY = exit.position.y + .08f;
                Vector3 lower = exit.position + Vector3.up * (.08f + radius);
                Vector3 upper = exit.position + Vector3.up * (.08f + height - radius);
                Vector3 center = (lower + upper) * .5f;
                foreach (Collider obstacle in Physics.OverlapCapsule(lower, upper, radius, ~0, QueryTriggerInteraction.Ignore))
                {
                    if (obstacle == null || obstacle == probe || !obstacle.enabled || obstacle.isTrigger) continue;
                    if (obstacle.gameObject.scene != scene || !obstacle.gameObject.activeInHierarchy) continue;
                    if (obstacle.transform == station.transform || obstacle.transform.IsChildOf(station.transform)) continue;
                    // Floor/rug contact below the capsule's sole is intentional.
                    if (obstacle.bounds.max.y <= footY + .01f) continue;
                    // Narrow-phase confirmation avoids flagging mere bounding-box
                    // contact or nearby table legs/supports outside the capsule.
                    if (!Physics.ComputePenetration(probe, center, Quaternion.identity,
                        obstacle, obstacle.transform.position, obstacle.transform.rotation,
                        out Vector3 direction, out float distance)) continue;
                    if (distance <= .005f) continue;
                    errors.Add(ObjectPath(station.transform) + ": exit capsule penetrates " +
                        ObjectPath(obstacle.transform) + " by " + distance.ToString("F3") + " m.");
                }
            }
        }
        finally
        {
            UnityEngine.Object.DestroyImmediate(probeObject);
        }

        if (unbakedGiRenderers > 0)
            warnings.Add(unbakedGiRenderers + " ContributeGI renderers do not currently have a baked lightmap index. Their UV2 channel was still checked.");
        report.AppendLine("NEON NOOK PC SCENE QUALITY AUDIT");
        report.AppendLine("Scene: " + scene.path);
        report.AppendLine("UTC: " + DateTime.UtcNow.ToString("O"));
        report.AppendLine("Active renderers: " + renderers);
        report.AppendLine("Referenced triangles: " + triangles);
        report.AppendLine("Unique geometry meshes: " + checkedMeshes.Count);
        report.AppendLine("Used materials: " + checkedMaterials.Count);
        report.AppendLine("Lightmap GI renderers: " + giRenderers);
        report.AppendLine("Baked lightmap textures: " + (LightmapSettings.lightmaps == null ? 0 : LightmapSettings.lightmaps.Length));
        report.AppendLine("Station exit capsules tested: " + exits);
        report.AppendLine("Capsule radius 0.20 m; total height 1.50 m; sole 0.08 m above exit transform.");
        report.AppendLine("Penetration tolerance 0.005 m; triggers, seat-local colliders, and floor contact excluded.");
        report.AppendLine();
        foreach (string warning in warnings.Distinct()) report.AppendLine("NOTE: " + warning);
        foreach (string error in errors.Distinct()) report.AppendLine("ERROR: " + error);
        report.AppendLine();
        report.AppendLine(errors.Count == 0 ? "PASS: PC_SCENE_AUDIT_PASS" : "FAIL: " + errors.Count + " structural issues.");
        report.AppendLine("Does not certify UV chart overlap/stretching, all possible avatar proportions, visual intersections, shader compilation, or headset performance. Inspect actual Unity/VRChat views too.");
        string text = report.ToString();
        string path = Path.Combine(NeonNookPaths.OutputDirectory, "pc-quality-audit.txt");
        File.WriteAllText(path, text);
        Debug.Log(text);
        if (errors.Count > 0) throw new InvalidOperationException("PC scene audit failed. See " + path + ". First issue: " + errors[0]);
        return text;
    }

    private static void AuditMesh(Mesh mesh, string label, List<string> errors)
    {
        Vector3[] vertices = mesh.vertices;
        if (vertices.Length != mesh.vertexCount || vertices.Any(v => !Finite(v.x) || !Finite(v.y) || !Finite(v.z)))
            errors.Add(label + ": invalid/non-finite mesh positions.");
        Bounds bounds = mesh.bounds;
        if (!Finite(bounds.center.x) || !Finite(bounds.center.y) || !Finite(bounds.center.z)
            || !Finite(bounds.extents.x) || !Finite(bounds.extents.y) || !Finite(bounds.extents.z))
            errors.Add(label + ": non-finite mesh bounds.");
        else
        {
            // A finite vertex buffer can still have invalid zero/stale bounds.
            // BakeMesh snapshots need RecalculateBounds before static rendering.
            bounds.Expand(Mathf.Max(.00001f, bounds.size.magnitude * .00001f));
            if (vertices.Any(v => !bounds.Contains(v)))
                errors.Add(label + ": mesh bounds do not contain its vertices; recalculate bounds on a PC-owned copy.");
        }
        Vector3[] normals = mesh.normals;
        if (normals == null || normals.Length != mesh.vertexCount)
            errors.Add(label + ": missing or incomplete mesh normals.");
        else if (normals.Any(n => !Finite(n.x) || !Finite(n.y) || !Finite(n.z) || n.sqrMagnitude < 1e-10f))
            errors.Add(label + ": non-finite or zero mesh normals.");
        Vector2[] uv = mesh.uv;
        if (uv == null || uv.Length != mesh.vertexCount)
            errors.Add(label + ": missing or incomplete texture UV0.");
        else if (uv.Any(v => !Finite(v.x) || !Finite(v.y)))
            errors.Add(label + ": non-finite texture UV0 values.");
        Vector4[] tangents = mesh.tangents;
        if (tangents.Any(t => !Finite(t.x) || !Finite(t.y) || !Finite(t.z) || !Finite(t.w)))
            errors.Add(label + ": non-finite mesh tangents.");
        for (int sub = 0; sub < mesh.subMeshCount; sub++)
        {
            int[] indices = mesh.GetIndices(sub, true);
            if (indices.Any(i => i < 0 || i >= mesh.vertexCount))
                errors.Add(label + ": submesh " + sub + " contains out-of-range indices.");
            if (mesh.GetTopology(sub) == MeshTopology.Triangles && indices.Length % 3 != 0)
                errors.Add(label + ": submesh " + sub + " has an incomplete triangle index buffer.");
        }
    }

    private static void AuditMapImporters(Material material, List<string> errors)
    {
        foreach (string slot in new[] { "_MainTex", "_BumpMap", "_MetallicGlossMap", "_OcclusionMap" })
        {
            if (!material.HasProperty(slot) || material.GetTexture(slot) == null) continue;
            string path = AssetDatabase.GetAssetPath(material.GetTexture(slot));
            // Source-asset maps can intentionally use different conventions.
            if (!path.StartsWith("Assets/IdleLounge/PC/Textures/", StringComparison.Ordinal)) continue;
            TextureImporter importer = AssetImporter.GetAtPath(path) as TextureImporter;
            if (importer == null) continue;
            if (slot == "_BumpMap" && importer.textureType != TextureImporterType.NormalMap)
                errors.Add(material.name + ": bump texture is not imported as a normal map.");
            if (slot != "_MainTex" && importer.sRGBTexture)
                errors.Add(material.name + ": data map " + slot + " incorrectly uses sRGB sampling.");
            if (slot == "_MainTex" && !importer.sRGBTexture)
                errors.Add(material.name + ": generated albedo is not imported as sRGB.");
        }
    }

    private static bool Finite(float value) { return !float.IsNaN(value) && !float.IsInfinity(value); }
    private static string ObjectPath(Transform t)
    {
        string path = t.name;
        while (t.parent != null) { t = t.parent; path = t.name + "/" + path; }
        return path;
    }
}
