using UnityEngine;
using UnityEditor;
using System.IO;
public static class InspectRoom {
 public static void Run() {
  var report = new System.Text.StringBuilder();
  foreach(var guid in AssetDatabase.FindAssets("t:Model", new[]{"Assets/Gaming Room"})) {
   string path=AssetDatabase.GUIDToAssetPath(guid); var asset=AssetDatabase.LoadAssetAtPath<GameObject>(path);
   report.AppendLine(path);
   foreach(var r in asset.GetComponentsInChildren<Renderer>(true)) report.AppendLine(r.name+" | "+r.bounds+" | "+string.Join(",", System.Array.ConvertAll(r.sharedMaterials,m=>m?m.name:"null")));
  }
  File.WriteAllText("../room-inspection.txt", report.ToString());
 }
}
