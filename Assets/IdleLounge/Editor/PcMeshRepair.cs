using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.SceneManagement;

/// <summary>
/// Rebuilds the static snapshots from the supplied Gaming Room in PC-owned assets.
/// BakeMesh snapshots in Generated have valid vertex arrays but zero mesh bounds.
/// Public setters reconstruct their buffers and submesh metadata, and a final
/// RecalculateBounds restores the bounds needed by rendering and the lightmapper.
/// User-supplied Gaming Room assets, materials, UVs and winding are untouched.
/// FixGeneratedSourceBounds changes only our own generated snapshot metadata.
/// </summary>
public static class PcMeshRepair
{
    private const string Folder = "Assets/IdleLounge/PC/Meshes";
    private static readonly Dictionary<Mesh, Mesh> Cache = new Dictionary<Mesh, Mesh>();

    /// <summary>Repairs only our generated static snapshots, including Quest culling.</summary>
    public static int FixGeneratedSourceBounds()
    {
        int count = 0;
        foreach (string guid in AssetDatabase.FindAssets("t:Mesh", new[] { "Assets/IdleLounge/Generated" }))
        {
            string path = AssetDatabase.GUIDToAssetPath(guid);
            if (!path.StartsWith("Assets/IdleLounge/Generated/SourceMesh_", StringComparison.Ordinal)
                || !path.EndsWith(".asset", StringComparison.Ordinal)) continue;
            Mesh mesh = AssetDatabase.LoadAssetAtPath<Mesh>(path);
            if (mesh == null) continue;
            mesh.RecalculateBounds();
            EditorUtility.SetDirty(mesh);
            count++;
        }
        AssetDatabase.SaveAssets();
        Debug.Log("GENERATED_SOURCE_BOUNDS_FIXED: " + count);
        return count;
    }

    [MenuItem("Neon Nook/PC/Repair imported mesh snapshots")]
    private static void RepairFromMenu() { RepairImportedMeshesInActiveScene(); }

    public static int RepairImportedMeshesInActiveScene()
    {
        Scene scene = SceneManager.GetActiveScene();
        if (!scene.IsValid() || !scene.isLoaded) throw new InvalidOperationException("No active loaded scene.");
        // This operation is deliberately restricted to the PC scene.
        if (scene.path != "Assets/IdleLounge/Scenes/NeonNook-PC.unity")
            throw new InvalidOperationException("Open NeonNook-PC.unity before repairing its imported meshes.");
        Directory.CreateDirectory(Folder);
        if (!AssetDatabase.IsValidFolder(Folder)) AssetDatabase.Refresh();
        var replacements = new Dictionary<Mesh, Mesh>();
        int changed = 0;
        foreach (MeshFilter filter in UnityEngine.Object.FindObjectsOfType<MeshFilter>(true))
        {
            if (filter.gameObject.scene != scene || !filter.name.StartsWith("Gaming Room /", StringComparison.Ordinal)) continue;
            Mesh source = filter.sharedMesh;
            if (source == null) throw new InvalidOperationException(filter.name + " has no source mesh.");
            string sourcePath = AssetDatabase.GetAssetPath(source);
            if (sourcePath.StartsWith(Folder + "/CleanSource_", StringComparison.Ordinal)) continue;
            // The generated BakeMesh snapshots need repair. Direct imported models
            // retain their importer settings and are validated by the scene audit.
            if (!sourcePath.StartsWith("Assets/IdleLounge/Generated/SourceMesh_", StringComparison.Ordinal)) continue;
            if (!replacements.TryGetValue(source, out Mesh replacement))
            {
                replacement = CleanCopy(source);
                replacements.Add(source, replacement);
            }
            filter.sharedMesh = replacement;
            var collider = filter.GetComponent<MeshCollider>();
            if (collider != null && collider.sharedMesh == source) collider.sharedMesh = replacement;
            EditorUtility.SetDirty(filter);
            changed++;
        }
        if (changed > 0) EditorSceneManager.MarkSceneDirty(scene);
        AssetDatabase.SaveAssets();
        Debug.Log("PC_IMPORTED_MESH_REPAIR_PASS: " + changed + " renderer meshes rebuilt; " + replacements.Count + " unique assets.");
        return changed;
    }

    public static Mesh CleanCopy(Mesh source)
    {
        if (source == null) throw new ArgumentNullException(nameof(source));
        if (Cache.TryGetValue(source, out Mesh cached) && cached != null) return cached;
        Directory.CreateDirectory(Folder);
        if (!AssetDatabase.IsValidFolder(Folder)) AssetDatabase.Refresh();
        if (!source.isReadable) throw new InvalidOperationException(source.name + " must be readable before repair.");
        if (source.blendShapeCount != 0 || source.bindposes.Length != 0 || source.boneWeights.Length != 0)
            throw new InvalidOperationException(source.name + " is not a static snapshot; refusing to discard skinning data.");
        if (!AssetDatabase.TryGetGUIDAndLocalFileIdentifier(source, out string guid, out long localId))
            throw new InvalidOperationException("Source mesh must be saved before repair: " + source.name);
        var clean = new Mesh { name = "PC imported " + guid.Substring(0, 8) + " " + localId,
            indexFormat = source.vertexCount > 65535 ? IndexFormat.UInt32 : source.indexFormat };
        clean.vertices = source.vertices;
        clean.normals = source.normals;
        if (source.HasVertexAttribute(VertexAttribute.Tangent)) clean.tangents = source.tangents;
        if (source.HasVertexAttribute(VertexAttribute.Color)) clean.colors = source.colors;
        for (int channel = 0; channel < 8; channel++) CopyUv(source, clean, channel);
        clean.subMeshCount = source.subMeshCount;
        for (int sub = 0; sub < source.subMeshCount; sub++)
        {
            // GetIndices applies baseVertex; SetIndices writes canonical absolute
            // indices without changing the submesh/material order or topology.
            clean.SetIndices(source.GetIndices(sub, true), source.GetTopology(sub), sub, true);
        }
        clean.RecalculateBounds();
        VerifyEquivalent(source, clean);
        string path = Folder + "/CleanSource_" + guid + "_" + localId + ".asset";
        Mesh existing = AssetDatabase.LoadAssetAtPath<Mesh>(path);
        if (existing != null)
        {
            EditorUtility.CopySerialized(clean, existing);
            UnityEngine.Object.DestroyImmediate(clean);
            EditorUtility.SetDirty(existing);
            Cache[source] = existing;
            return existing;
        }
        AssetDatabase.CreateAsset(clean, path);
        Cache[source] = clean;
        return clean;
    }

    private static void CopyUv(Mesh source, Mesh target, int channel)
    {
        var attribute = (VertexAttribute)((int)VertexAttribute.TexCoord0 + channel);
        if (!source.HasVertexAttribute(attribute)) return;
        int dimension = source.GetVertexAttributeDimension(attribute);
        if (dimension == 2)
        {
            var values = new List<Vector2>(); source.GetUVs(channel, values); target.SetUVs(channel, values);
        }
        else if (dimension == 3)
        {
            var values = new List<Vector3>(); source.GetUVs(channel, values); target.SetUVs(channel, values);
        }
        else if (dimension == 4)
        {
            var values = new List<Vector4>(); source.GetUVs(channel, values); target.SetUVs(channel, values);
        }
        else throw new InvalidOperationException(source.name + " has unsupported UV" + channel + " dimension " + dimension);
    }

    private static void VerifyEquivalent(Mesh source, Mesh clean)
    {
        if (!source.vertices.SequenceEqual(clean.vertices) || !source.normals.SequenceEqual(clean.normals)
            || !source.tangents.SequenceEqual(clean.tangents) || !source.colors.SequenceEqual(clean.colors))
            throw new InvalidOperationException(source.name + ": rebuilt vertex attributes changed unexpectedly.");
        for (int channel = 0; channel < 8; channel++)
        {
            var before = new List<Vector4>(); var after = new List<Vector4>();
            source.GetUVs(channel, before); clean.GetUVs(channel, after);
            if (!before.SequenceEqual(after)) throw new InvalidOperationException(source.name + ": UV" + channel + " changed during rebuild.");
        }
        for (int sub = 0; sub < source.subMeshCount; sub++)
        {
            if (source.GetTopology(sub) != clean.GetTopology(sub)
                || !source.GetIndices(sub, true).SequenceEqual(clean.GetIndices(sub, true)))
                throw new InvalidOperationException(source.name + ": submesh " + sub + " changed during rebuild.");
        }
        Bounds bounds = clean.bounds;
        bounds.Expand(Mathf.Max(.00001f, bounds.size.magnitude * .00001f));
        if (clean.vertices.Any(v => !bounds.Contains(v)))
            throw new InvalidOperationException(source.name + ": rebuilt mesh bounds do not contain all vertices.");
    }
}
