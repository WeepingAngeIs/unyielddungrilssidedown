using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using UnityEditor;
using UnityEngine;
using UnityEngine.SceneManagement;

/// <summary>Read-only checks for the open-center, perimeter-seating layout.</summary>
public static class PerimeterLayoutAudit
{
    private const float Radius = .25f;
    private const float Height = 1.65f;
    private const float SoleOffset = .08f;
    private const float ContactTolerance = .005f;

    [MenuItem("Neon Nook/Audit perimeter circulation")]
    private static void RunFromMenu() { Run(); }

    public static string Run()
    {
        Scene scene = SceneManager.GetActiveScene();
        if (!scene.IsValid() || !scene.isLoaded) throw new InvalidOperationException("No loaded active scene.");
        var errors = new HashSet<string>();
        var notes = new HashSet<string>();
        var report = new StringBuilder();
        // 14 x 12 m of open floor, with 2.2 m clear headroom. Floor/rug contact
        // below 8 cm is allowed; elevated furniture and solid props are not.
        var central = new Bounds(new Vector3(0, 1.14f, 0), new Vector3(14, 2.12f, 12));
        VRC.SDKBase.VRCStation[] stations = UnityEngine.Object.FindObjectsOfType<VRC.SDKBase.VRCStation>(true)
            .Where(s => s.gameObject.scene == scene && s.gameObject.activeInHierarchy).ToArray();
        Transform[] sofas = UnityEngine.Object.FindObjectsOfType<Transform>(true)
            .Where(t => t.gameObject.scene == scene && t.gameObject.activeInHierarchy && t.name.StartsWith("Sofa with two VR seats", StringComparison.Ordinal)).ToArray();
        if (sofas.Length != 6) errors.Add("Expected six perimeter sofas; found " + sofas.Length + ".");
        if (stations.Length != 12) errors.Add("Expected twelve preserved stations; found " + stations.Length + ".");
        foreach (MeshCollider collider in UnityEngine.Object.FindObjectsOfType<MeshCollider>(true))
            if (Active(collider, scene) && collider.sharedMesh == null)
                errors.Add(Path(collider.transform) + ": active MeshCollider has no mesh.");

        Physics.SyncTransforms();
        var probes = new GameObject("Temporary perimeter audit probes");
        probes.hideFlags = HideFlags.HideAndDontSave;
        var box = probes.AddComponent<BoxCollider>();
        box.size = central.size;
        box.enabled = false;
        var capsule = probes.AddComponent<CapsuleCollider>();
        capsule.direction = 1; capsule.radius = Radius; capsule.height = Height; capsule.enabled = false;
        int routes = 0;
        try
        {
            foreach (Collider obstacle in Physics.OverlapBox(central.center, central.extents, Quaternion.identity, ~0, QueryTriggerInteraction.Ignore))
            {
                if (!Active(obstacle, scene) || obstacle.transform == probes.transform || obstacle.bounds.max.y <= .09f) continue;
                if (Physics.ComputePenetration(box, central.center, Quaternion.identity, obstacle,
                    obstacle.transform.position, obstacle.transform.rotation, out Vector3 direction, out float distance)
                    && distance > ContactTolerance)
                    errors.Add("Central 14 x 12 m clearance intersects " + Path(obstacle.transform) + " (penetration " + distance.ToString("F3") + " m).");
            }

            foreach (VRC.SDKBase.VRCStation station in stations)
            {
                Transform exit = station.stationExitPlayerLocation;
                if (exit == null) { errors.Add(Path(station.transform) + ": station exit is missing."); continue; }
                Vector3 lower = exit.position + Vector3.up * (SoleOffset + Radius);
                Vector3 upper = exit.position + Vector3.up * (SoleOffset + Height - Radius);
                Vector3 center = (lower + upper) * .5f;
                foreach (Collider obstacle in Physics.OverlapCapsule(lower, upper, Radius, ~0, QueryTriggerInteraction.Ignore))
                {
                    if (Ignore(obstacle, station, scene, probes.transform, exit.position.y + SoleOffset)) continue;
                    if (Physics.ComputePenetration(capsule, center, Quaternion.identity, obstacle,
                        obstacle.transform.position, obstacle.transform.rotation, out Vector3 direction, out float distance)
                        && distance > ContactTolerance)
                        errors.Add(Path(station.transform) + ": exit blocked by " + Path(obstacle.transform) + ".");
                }
                // Straight, inward walking route to the nearest point within the
                // open central zone. A 35 cm inset keeps the full capsule inside.
                Vector3 destination = new Vector3(Mathf.Clamp(exit.position.x, -6.65f, 6.65f), exit.position.y,
                    Mathf.Clamp(exit.position.z, -5.65f, 5.65f));
                Vector3 route = destination - exit.position;
                if (route.magnitude > .01f)
                {
                    foreach (RaycastHit hit in Physics.CapsuleCastAll(lower, upper, Radius, route.normalized,
                        route.magnitude, ~0, QueryTriggerInteraction.Ignore))
                    {
                        if (Ignore(hit.collider, station, scene, probes.transform, exit.position.y + SoleOffset)) continue;
                        errors.Add(Path(station.transform) + ": inward route blocked by " + Path(hit.collider.transform)
                            + " after " + hit.distance.ToString("F2") + " m.");
                    }
                }
                routes++;
                report.AppendLine("Seat exit " + exit.position.ToString("F2") + " -> clear-zone destination " + destination.ToString("F2"));
            }

            // Broad-phase visual notes catch decorative objects whose collision
            // has been removed. These are notes because bounds can enclose empty
            // space; they are not evidence of a triangle-level intersection.
            foreach (Renderer renderer in UnityEngine.Object.FindObjectsOfType<Renderer>(true))
            {
                if (renderer.gameObject.scene != scene || !renderer.enabled || !renderer.gameObject.activeInHierarchy) continue;
                if (renderer.bounds.max.y <= .09f || !renderer.bounds.Intersects(central)) continue;
                notes.Add("Visual bounds enter the open-center volume: " + Path(renderer.transform) + ". Inspect actual geometry.");
            }
        }
        finally { UnityEngine.Object.DestroyImmediate(probes); }

        var text = new StringBuilder();
        text.AppendLine("NEON NOOK PERIMETER CIRCULATION AUDIT");
        text.AppendLine("Scene: " + scene.path);
        text.AppendLine("UTC: " + DateTime.UtcNow.ToString("O"));
        text.AppendLine("Clear floor: x[-7,7], z[-6,6], 168 square metres; y[0.08,2.20].");
        text.AppendLine("Walking capsule: radius 0.25 m, total height 1.65 m; sole 0.08 m above exit.");
        text.AppendLine("Sofas: " + sofas.Length + "; stations: " + stations.Length + "; exit routes tested: " + routes + ".");
        text.AppendLine();
        text.Append(report);
        foreach (string note in notes.OrderBy(n => n)) text.AppendLine("NOTE: " + note);
        foreach (string error in errors.OrderBy(e => e)) text.AppendLine("ERROR: " + error);
        text.AppendLine();
        text.AppendLine(errors.Count == 0 ? "PASS: PERIMETER_LAYOUT_AUDIT_PASS" : "FAIL: " + errors.Count + " circulation issues.");
        text.AppendLine("This checks collider clearance and straight exit routes; visual clipping, video readability and unusual avatar dimensions still need inspection.");
        string output = text.ToString();
        string path = System.IO.Path.Combine(NeonNookPaths.OutputDirectory, "perimeter-layout-audit.txt");
        File.WriteAllText(path, output);
        Debug.Log(output);
        if (errors.Count > 0) throw new InvalidOperationException("Perimeter circulation failed; see " + path + ". " + errors.First());
        return output;
    }

    private static bool Active(Collider collider, Scene scene)
    {
        return collider != null && collider.enabled && !collider.isTrigger
            && collider.gameObject.scene == scene && collider.gameObject.activeInHierarchy;
    }

    private static bool Ignore(Collider collider, VRC.SDKBase.VRCStation station, Scene scene, Transform probes, float soleY)
    {
        return !Active(collider, scene) || collider.transform == probes || collider.bounds.max.y <= soleY + .01f
            || collider.transform == station.transform || collider.transform.IsChildOf(station.transform);
    }

    private static string Path(Transform transform)
    {
        string name = transform.name;
        while (transform.parent != null) { transform = transform.parent; name = transform.name + "/" + name; }
        return name;
    }
}
