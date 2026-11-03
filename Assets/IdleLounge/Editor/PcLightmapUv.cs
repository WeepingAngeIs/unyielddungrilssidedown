using System;
using System.Collections.Generic;
using UnityEditor;
using UnityEngine;

/// <summary>
/// Six continuous, padded lightmap charts for the generated rounded boxes.
/// Each face retains its physical aspect ratio and its bevel strips; no tiny
/// bevel islands or vertex splitting are introduced. Texture UV0 is untouched.
/// </summary>
public static class PcLightmapUv
{
    private struct Placement
    {
        public float x, y;
        public bool rotated;
    }

    private struct Shelf
    {
        public float y, height, usedWidth;
    }

    /// <summary>Default architectural bevel, matching RoundedBox's default .015 m.</summary>
    public static Vector2[] BoxCharts(Vector3 size) { return BoxCharts(size, .015f); }

    /// <summary>
    /// Returns exactly 384 UV2 entries in right, left, up, down, forward, back
    /// order, with y-then-x 8x8 vertices on each face. Pass the same radius used
    /// by RoundedBox so every sample keeps its metric spacing within its chart.
    /// </summary>
    public static Vector2[] BoxCharts(Vector3 size, float radius)
    {
        size = new Vector3(Mathf.Abs(size.x), Mathf.Abs(size.y), Mathf.Abs(size.z));
        if (!Finite(size.x) || !Finite(size.y) || !Finite(size.z)
            || size.x <= 0f || size.y <= 0f || size.z <= 0f || !Finite(radius) || radius <= 0f)
            throw new ArgumentException("Rounded box size and radius must be finite and positive.");
        radius = Mathf.Min(radius, Mathf.Min(size.x, Mathf.Min(size.y, size.z)) * .45f);
        // These match the u/v axes of RoundedBox for each of its six faces.
        var faces = new[] {
            new Vector2(size.z, size.y), new Vector2(size.z, size.y),
            new Vector2(size.x, size.z), new Vector2(size.x, size.z),
            new Vector2(size.x, size.y), new Vector2(size.x, size.y)
        };
        float gutter = Mathf.Max(size.x, Mathf.Max(size.y, size.z)) * .06f;
        Placement[] layout = Pack(faces, gutter, out float extent);
        var result = new Vector2[384];
        for (int face = 0; face < 6; face++)
        {
            Vector2 dimensions = faces[face];
            float[] xs = Steps(dimensions.x * .5f, radius);
            float[] ys = Steps(dimensions.y * .5f, radius);
            Placement placement = layout[face];
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
            {
                float u = xs[x] + dimensions.x * .5f;
                float v = ys[y] + dimensions.y * .5f;
                // Rotate 90 degrees, preserving UV orientation and physical scale.
                float packedX = placement.rotated ? dimensions.y - v : u;
                float packedY = placement.rotated ? u : v;
                result[face * 64 + y * 8 + x] = new Vector2(
                    (placement.x + gutter * .5f + packedX) / extent,
                    (placement.y + gutter * .5f + packedY) / extent);
            }
        }
        return result;
    }

    /// <summary>Use for non-box meshes; call only on a PC-owned mesh copy.</summary>
    public static void Unwrap(Mesh mesh)
    {
        if (mesh == null) throw new ArgumentNullException(nameof(mesh));
        UnwrapParam.SetDefaults(out UnwrapParam parameters);
        parameters.packMargin = .04f;
        Unwrapping.GenerateSecondaryUVSet(mesh, parameters);
        if (mesh.uv2.Length != mesh.vertexCount)
            throw new InvalidOperationException("Lightmap unwrap did not create complete UV2: " + mesh.name);
    }

    private static Placement[] Pack(Vector2[] faces, float gutter, out float bestExtent)
    {
        bestExtent = float.PositiveInfinity;
        float bestArea = float.PositiveInfinity;
        var best = new Placement[6];
        var candidate = new Placement[6];
        var shelves = new Shelf[6];
        var widths = new float[6];
        var heights = new float[6];
        var order = new int[6];

        // Only six rectangles: all orientation choices plus three deterministic
        // shelf orders are inexpensive. Candidate widths include every possible
        // shelf subset sum, and near-square widths for better overall occupancy.
        for (int rotationMask = 0; rotationMask < 64; rotationMask++)
        {
            float totalArea = 0f, minimumWidth = 0f;
            for (int i = 0; i < 6; i++)
            {
                bool rotated = (rotationMask & (1 << i)) != 0;
                widths[i] = (rotated ? faces[i].y : faces[i].x) + gutter;
                heights[i] = (rotated ? faces[i].x : faces[i].y) + gutter;
                totalArea += widths[i] * heights[i];
                minimumWidth = Mathf.Max(minimumWidth, widths[i]);
            }
            var candidateWidths = new HashSet<float>();
            for (int mask = 1; mask < 64; mask++)
            {
                float width = 0;
                for (int i = 0; i < 6; i++) if ((mask & (1 << i)) != 0) width += widths[i];
                if (width >= minimumWidth) candidateWidths.Add(width);
            }
            float squareWidth = Mathf.Sqrt(totalArea);
            foreach (float factor in new[] { .8f, 1f, 1.2f, 1.5f })
                candidateWidths.Add(Mathf.Max(minimumWidth, squareWidth * factor));
            // Sorted iteration makes the chosen layout deterministic on ties.
            var sortedWidths = new List<float>(candidateWidths);
            sortedWidths.Sort();
            for (int mode = 0; mode < 3; mode++)
            {
                for (int i = 0; i < 6; i++) order[i] = i;
                int sortMode = mode;
                Array.Sort(order, (a, b) => {
                    float ka = sortMode == 0 ? heights[a] : sortMode == 1 ? widths[a] : widths[a] * heights[a];
                    float kb = sortMode == 0 ? heights[b] : sortMode == 1 ? widths[b] : widths[b] * heights[b];
                    int compare = kb.CompareTo(ka);
                    return compare == 0 ? a.CompareTo(b) : compare;
                });
                foreach (float candidateWidth in sortedWidths)
                {
                    int shelfCount = 0;
                    float usedWidth = 0f, usedHeight = 0f;
                    foreach (int face in order)
                    {
                        int chosen = -1;
                        float leastRemainder = float.PositiveInfinity;
                        for (int row = 0; row < shelfCount; row++)
                        {
                            Shelf shelf = shelves[row];
                            float remainder = candidateWidth - shelf.usedWidth - widths[face];
                            if (heights[face] <= shelf.height + 1e-6f && remainder >= -1e-6f && remainder < leastRemainder)
                            { chosen = row; leastRemainder = remainder; }
                        }
                        if (chosen < 0)
                        {
                            chosen = shelfCount++;
                            shelves[chosen] = new Shelf { y = usedHeight, height = heights[face], usedWidth = 0f };
                            usedHeight += heights[face];
                        }
                        Shelf selected = shelves[chosen];
                        candidate[face] = new Placement { x = selected.usedWidth, y = selected.y,
                            rotated = (rotationMask & (1 << face)) != 0 };
                        selected.usedWidth += widths[face];
                        shelves[chosen] = selected;
                        usedWidth = Mathf.Max(usedWidth, selected.usedWidth);
                    }
                    float extent = Mathf.Max(usedWidth, usedHeight);
                    float area = usedWidth * usedHeight;
                    if (extent < bestExtent - 1e-6f || (Mathf.Abs(extent - bestExtent) <= 1e-6f && area < bestArea))
                    {
                        bestExtent = extent; bestArea = area;
                        Array.Copy(candidate, best, 6);
                    }
                }
            }
        }
        return best;
    }

    private static float[] Steps(float half, float radius)
    {
        return new[] { -half, -half + radius * .134f, -half + radius * .5f, -half + radius,
            half - radius, half - radius * .5f, half - radius * .134f, half };
    }

    private static bool Finite(float value) { return !float.IsNaN(value) && !float.IsInfinity(value); }
}
