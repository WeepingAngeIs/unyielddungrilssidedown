using UnityEngine;
using UnityEngine.UI;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine.Rendering;
using UdonSharp;
using UdonSharpEditor;
using VRC.SDK3.Components;
using VRC.Udon;
using System.IO;
using System.Linq;

public static class NeonNookBuilder {
 const string Root="Assets/IdleLounge";
 static Material navy, floor, panel, cream, teal, orange, pink, wood;
 static Font font;
 static Transform decor;
 static ArcadeEconomy economy;
 public static void OpenPreview(){EditorSceneManager.OpenScene(Root+"/Scenes/NeonNook.unity");var view=SceneView.lastActiveSceneView;if(view!=null)view.LookAt(new Vector3(0,1,1),Quaternion.Euler(28,-20,0),18);}
 public static void BuildWindows(){Build();WorldBuildChecks.Windows();}
 public static void BuildAndroid(){Build();WorldBuildChecks.Android();}
 static void ReadSavedMaterial(Material source,Material target){
  var so=new SerializedObject(source);var colors=so.FindProperty("m_SavedProperties.m_Colors");
  for(int i=0;i<colors.arraySize;i++){var p=colors.GetArrayElementAtIndex(i);if(p.FindPropertyRelative("first").stringValue=="_Color")target.color=p.FindPropertyRelative("second").colorValue;}
  var textures=so.FindProperty("m_SavedProperties.m_TexEnvs");
  for(int i=0;i<textures.arraySize;i++){var p=textures.GetArrayElementAtIndex(i);if(p.FindPropertyRelative("first").stringValue=="_MainTex")target.mainTexture=p.FindPropertyRelative("second.m_Texture").objectReferenceValue as Texture;}
 }
 static void EnsurePrograms(){
  foreach(string name in new[]{"ArcadeEconomy","ArcadeAction","LoungeVideo","LoungeSeat","BallReset"}) {
   string p=Root+"/Scripts/"+name+".asset";
   if(!AssetDatabase.LoadAssetAtPath<UdonSharpProgramAsset>(p)){
    var a=ScriptableObject.CreateInstance<UdonSharpProgramAsset>();a.sourceCsScript=AssetDatabase.LoadAssetAtPath<MonoScript>(Root+"/Scripts/"+name+".cs");AssetDatabase.CreateAsset(a,p);
   }
  }
  AssetDatabase.SaveAssets();UdonSharp.Compiler.UdonSharpCompilerV1.CompileSync();
 }
 [MenuItem("Neon Nook/Build preview world")]
 public static void Build() {
  Directory.CreateDirectory(Root+"/Generated"); Directory.CreateDirectory(Root+"/Scenes");
  EnsurePrograms(); ArcadeEconomyChecks.Run();
  EditorSceneManager.NewScene(NewSceneSetup.EmptyScene,NewSceneMode.Single);
  font=Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
  navy=Mat("Midnight", "172535"); floor=Mat("Slate", "293C4D"); panel=Mat("Panel", "213345");
  cream=Mat("Ivory", "F4E6CF"); wood=Mat("WarmOak","9E795C");
  teal=Mat("Mint", "65E5CF",true); orange=Mat("Apricot","FFBC77",true); pink=Mat("Coral","F58A9C",true);
  decor=new GameObject("Architecture and furniture").transform;
  RenderSettings.skybox=null; RenderSettings.ambientLight=new Color(.65f,.7f,.8f); RenderSettings.fog=false;
  QualitySettings.pixelLightCount=0; QualitySettings.antiAliasing=2; QualitySettings.vSyncCount=0;
  PlayerSettings.colorSpace=ColorSpace.Linear;
  PlayerSettings.companyName="WeepingAngeIs"; PlayerSettings.productName="Neon Nook";
  PlayerSettings.Android.minSdkVersion=AndroidSdkVersions.AndroidApiLevel29;
  PlayerSettings.SetScriptingBackend(BuildTargetGroup.Android,ScriptingImplementation.IL2CPP);
  PlayerSettings.Android.targetArchitectures=AndroidArchitecture.ARM64;
  Box("Foundation",new Vector3(0,-.25f,0),new Vector3(26,.5f,24),navy);
  Box("Main floor",new Vector3(0,-.03f,0),new Vector3(24,.06f,22),floor);
  for(int i=-5;i<=5;i++) Box("Floor seam",new Vector3(i*2,0.009f,0),new Vector3(.025f,.012f,22),panel,false);
  for(int i=-5;i<=5;i++) Box("Floor seam",new Vector3(0,0.009f,i*2),new Vector3(24,.012f,.025f),panel,false);
  Box("North wall",new Vector3(0,2.7f,11),new Vector3(24,5.4f,.3f),navy);
  Box("West wall",new Vector3(-12,2.7f,0),new Vector3(.3f,5.4f,22),navy);
  Box("East low wall",new Vector3(12,.6f,0),new Vector3(.3f,1.2f,22),navy);
  Box("South low wall",new Vector3(0,.6f,-11),new Vector3(24,1.2f,.3f),navy);
  Box("North crown",new Vector3(0,5.2f,10.8f),new Vector3(24,.055f,.06f),teal,false);
  Box("West crown",new Vector3(-11.8f,5.2f,0),new Vector3(.06f,.055f,22),teal,false);
  for(int i=0;i<7;i++) { float z=-9+i*3;
   Box("Window frame",new Vector3(11.9f,3,z),new Vector3(.18f,3.6f,.12f),wood);
   Box("Window horizon",new Vector3(12,3.4f,z+1.4f),new Vector3(.1f,.04f,2.7f),teal,false);
  }
  Box("East lintel",new Vector3(12,4.9f,0),new Vector3(.22f,.25f,22),wood);
  // A distant low-poly skyline beyond the open windows.
  for(int i=0;i<18;i++) {float h=2+(i*7%9);Box("Distant skyline",new Vector3(30+(i%3)*4,h/2-3,-32+i*4),new Vector3(3,h,2.8f),panel,false);}
  Sign("NEON NOOK",new Vector3(-5.8f,4.35f,10.7f),1.05f,cream.color);

  Sign("PLAY A LITTLE. STAY A WHILE.",new Vector3(0,1.2f,-8.3f),.22f,cream.color);
  Rug(new Vector3(2,.025f,4),new Vector3(11,.045f,11),panel,teal);
  Rug(new Vector3(-7,.025f,0),new Vector3(6,.045f,12),navy,orange);
  Rug(new Vector3(7,.025f,-5.5f),new Vector3(7,.045f,6),panel,pink);
  // Wide central circulation, six lounge seats and four side seats.
  Sofa(new Vector3(-.3f,0,2.1f),0,teal); Sofa(new Vector3(4.3f,0,2.1f),0,teal);
  Sofa(new Vector3(-.3f,0,-1.2f),0,cream); Sofa(new Vector3(4.3f,0,-1.2f),0,cream);
  Sofa(new Vector3(8.7f,0,5.3f),-90,pink);
  Table(new Vector3(2,0,3.8f)); Table(new Vector3(2,0,.4f));
  Sofa(new Vector3(5.5f,0,-7.2f),0,wood); Table(new Vector3(5.5f,0,-5.6f));
  Sign("THE LIVING ROOM",new Vector3(2,5,10.55f),.3f,teal.color);
  LoungeMediaBuilder.Build(new Vector3(2.2f,3.9f,10.35f));
  BuildEconomy(); BuildBall(); AddSourceFurniture();
  foreach(var p in new[]{new Vector3(-10,0,9),new Vector3(10.4f,0,9.5f),new Vector3(10,0,-9.3f),new Vector3(-10,0,-9)}) Plant(p);
  Sign("01  /  BUILD YOUR ARCADE",new Vector3(-7,3.45f,4.3f),.25f,orange.color);
  Sign("03  /  SLOW CORNER",new Vector3(7,2.6f,-9.8f),.25f,pink.color);
  // World descriptor supplies VRChat defaults and the player spawn.
  var world=(GameObject)PrefabUtility.InstantiatePrefab(AssetDatabase.LoadAssetAtPath<GameObject>("Packages/com.vrchat.worlds/Samples/UdonExampleScene/Prefabs/VRCWorld.prefab"));
  world.name="VRChat World"; var desc=world.GetComponent<VRCSceneDescriptor>();
  var spawn=new GameObject("Arrival").transform;spawn.position=new Vector3(0,.15f,-7.2f);
  desc.spawns=new[]{spawn};desc.RespawnHeightY=-8;
  var so=new SerializedObject(desc);var cap=so.FindProperty("capacity");if(cap!=null)cap.intValue=16;so.ApplyModifiedPropertiesWithoutUndo();
  var camera=new GameObject("Preview Camera").AddComponent<Camera>();camera.transform.position=new Vector3(10.7f,8.2f,-10.4f);camera.transform.LookAt(new Vector3(-.8f,1.2f,2.2f));camera.fieldOfView=68;camera.backgroundColor=new Color(.07f,.11f,.17f);camera.clearFlags=CameraClearFlags.SolidColor;
  desc.ReferenceCamera=camera.gameObject; camera.enabled=false;
  foreach(var u in Object.FindObjectsOfType<UdonSharpBehaviour>()) { UdonSharpEditorUtility.CopyProxyToUdon(u); var b=UdonSharpEditorUtility.GetBackingUdonBehaviour(u); if(b!=null) b.interactText=u is ArcadeAction?"Use arcade":u is LoungeSeat?"Sit down":u is BallReset?"Return ball":"Interact"; }
  UdonSharp.Compiler.UdonSharpCompilerV1.CompileSync();
  EditorSceneManager.SaveScene(UnityEngine.SceneManagement.SceneManager.GetActiveScene(),Root+"/Scenes/NeonNook.unity");
  EditorBuildSettings.scenes=new[]{new EditorBuildSettingsScene(Root+"/Scenes/NeonNook.unity",true)};
  AssetDatabase.SaveAssets(); Validate(); RenderPreview();
 }
 static Material Mat(string name,string hex,bool unlit=false) {
  string path=Root+"/Generated/"+name+".mat"; var m=AssetDatabase.LoadAssetAtPath<Material>(path);
  if(!m){m=new Material(Shader.Find(unlit?"Unlit/Color":"NeonNook/SoftSurface"));AssetDatabase.CreateAsset(m,path);}
  ColorUtility.TryParseHtmlString("#"+hex,out var c);m.color=c;m.enableInstancing=true;return m;
 }
 static GameObject Box(string name,Vector3 pos,Vector3 scale,Material m,bool collider=true) {var g=GameObject.CreatePrimitive(PrimitiveType.Cube);g.name=name;g.transform.SetParent(decor);g.transform.position=pos;g.transform.localScale=scale;g.GetComponent<Renderer>().sharedMaterial=m;g.GetComponent<Renderer>().shadowCastingMode=ShadowCastingMode.Off;g.GetComponent<Renderer>().receiveShadows=false;GameObjectUtility.SetStaticEditorFlags(g,StaticEditorFlags.BatchingStatic);if(!collider)Object.DestroyImmediate(g.GetComponent<Collider>());return g;}
 static void Rug(Vector3 p,Vector3 s,Material m,Material accent){Box("Inlaid rug",p,s,m,false);Box("Rug edge",p+new Vector3(0,.03f,-s.z/2),new Vector3(s.x,.01f,.035f),accent,false);}
 static void Sign(string txt,Vector3 p,float size,Color color) {var g=new GameObject(txt);g.transform.SetParent(decor);g.transform.position=p;if(p.z<-8)g.transform.rotation=Quaternion.Euler(0,180,0);var t=g.AddComponent<TextMesh>();t.text=txt;t.font=font;t.fontSize=80;t.characterSize=size*.1f;t.anchor=TextAnchor.MiddleCenter;t.alignment=TextAlignment.Center;t.color=color;g.GetComponent<MeshRenderer>().sharedMaterial=font.material;}
 static void Sofa(Vector3 p,float yaw,Material fabric){var root=new GameObject("Sofa with two VR seats").transform;root.position=p;root.rotation=Quaternion.Euler(0,yaw,0);
  void Part(string n,Vector3 local,Vector3 scale,Material mat){var b=Box(n,Vector3.zero,scale,mat);b.transform.SetParent(root);b.transform.localPosition=local;b.transform.localRotation=Quaternion.identity;}
  Part("Sofa base",new Vector3(0,.25f,0),new Vector3(3,.4f,1.25f),navy);Part("Cushion",new Vector3(0,.52f,0),new Vector3(2.6f,.22f,1.1f),fabric);Part("Backrest",new Vector3(0,.92f,-.55f),new Vector3(3,.9f,.25f),fabric);
  Part("Armrest",new Vector3(-1.4f,.68f,0),new Vector3(.22f,.55f,1.3f),fabric);Part("Armrest",new Vector3(1.4f,.68f,0),new Vector3(.22f,.55f,1.3f),fabric);
  for(int i=0;i<2;i++){var seat=new GameObject("Sit here");seat.transform.SetParent(root);seat.transform.localPosition=new Vector3(i==0?-.65f:.65f,.67f,.12f);seat.transform.localRotation=Quaternion.identity;var col=seat.AddComponent<BoxCollider>();col.size=new Vector3(.8f,.16f,.8f);col.isTrigger=true;var st=seat.AddComponent<VRCStation>();st.PlayerMobility=VRC.SDKBase.VRCStation.Mobility.Immobilize;st.seated=true;st.stationEnterPlayerLocation=seat.transform;var exit=new GameObject("Stand here").transform;exit.SetParent(root);exit.localPosition=new Vector3(i==0?-.65f:.65f,0,1.5f);st.stationExitPlayerLocation=exit;seat.AddUdonSharpComponent<LoungeSeat>().station=st;}
 }
 static void Table(Vector3 p){Box("Coffee table",p+new Vector3(0,.48f,0),new Vector3(2,.15f,.8f),wood);Box("Table plinth",p+new Vector3(0,.2f,0),new Vector3(1.3f,.4f,.5f),navy);Box("Table runner",p+new Vector3(0,.565f,0),new Vector3(1.3f,.015f,.5f),teal,false);}
 static void Plant(Vector3 p){var pot=GameObject.CreatePrimitive(PrimitiveType.Cylinder);pot.name="Planter";pot.transform.SetParent(decor);pot.transform.position=p+Vector3.up*.3f;pot.transform.localScale=new Vector3(.6f,.3f,.6f);pot.GetComponent<Renderer>().sharedMaterial=wood;for(int i=0;i<5;i++){var leaf=GameObject.CreatePrimitive(PrimitiveType.Sphere);leaf.name="Sculptural foliage";leaf.transform.SetParent(decor);leaf.transform.position=p+new Vector3(Mathf.Sin(i*2.4f)*.23f,1+i*.13f,Mathf.Cos(i*2.4f)*.23f);leaf.transform.localScale=new Vector3(.48f,.9f,.3f);leaf.GetComponent<Renderer>().sharedMaterial=teal;Object.DestroyImmediate(leaf.GetComponent<Collider>());}}
 static Text Display(string name,Vector3 pos,Vector2 size,int fontsize){var g=new GameObject(name,typeof(Canvas));g.transform.position=pos;g.transform.localScale=Vector3.one*.005f;g.GetComponent<Canvas>().renderMode=RenderMode.WorldSpace;g.GetComponent<RectTransform>().sizeDelta=size;var tgo=new GameObject("Text",typeof(RectTransform));tgo.transform.SetParent(g.transform,false);var r=tgo.GetComponent<RectTransform>();r.sizeDelta=size;var t=tgo.AddComponent<Text>();t.font=font;t.fontSize=fontsize;t.color=cream.color;t.alignment=TextAnchor.MiddleLeft;t.horizontalOverflow=HorizontalWrapMode.Wrap;t.verticalOverflow=VerticalWrapMode.Overflow;return t;}
 static void BuildEconomy(){var g=new GameObject("Personal arcade economy");economy=g.AddUdonSharpComponent<ArcadeEconomy>();economy.prestigeThreshold=100000;
  Box("Arcade display",new Vector3(-7,2.05f,4.5f),new Vector3(5.5f,2.5f,.25f),navy);
  economy.statusText=Display("Personal wallet",new Vector3(-7,2.85f,4.34f),new Vector2(950,150),32);
  economy.detailText=Display("Businesses and prices",new Vector3(-7,2.02f,4.33f),new Vector2(950,260),26);
  economy.feedbackText=Display("Arcade feedback",new Vector3(-7,1.08f,4.32f),new Vector2(950,100),23);
  economy.credits=economy.startingCredits;economy.RefreshDisplay();
  string[] names={"PLAY +2","CABINET","SNACK BAR","LOUNGE","BOOST","REOPEN"};
  for(int i=0;i<6;i++){var p=new Vector3(-9+(i%3)*2, .92f,2.9f-(i/3)*1.45f);Box("Console pedestal",p-new Vector3(0,.43f,0),new Vector3(1.6f,.85f,.9f),panel);var b=Box(names[i],p,new Vector3(1.65f,.16f,.95f),i==0?teal:i==5?pink:orange);var a=b.AddUdonSharpComponent<ArcadeAction>();a.economy=economy;a.action=i;Sign(names[i],p+new Vector3(0,.27f,-.5f),.18f,cream.color);}
  var help=Display("How to play",new Vector3(-11.45f,1.6f,-.5f),new Vector2(1000,340),25);help.transform.parent.rotation=Quaternion.Euler(0,-90,0);help.text="YOUR LITTLE ARCADE\n\n1  Tap PLAY or let credits roll in.\n2  Buy businesses. Boost their income.\n3  Reopen at 100K earned for a permanent\n    bonus during this visit.\n\nYour wallet is personal. Progress resets on leaving.";
  Box("Instructions panel",new Vector3(-11.55f,1.6f,-.5f),new Vector3(.12f,2.1f,5.4f),navy);
 }
 static void BuildBall(){var b=GameObject.CreatePrimitive(PrimitiveType.Sphere);b.name="Corner ball - pick up and throw";b.transform.position=new Vector3(-8,.4f,-6.5f);b.transform.localScale=Vector3.one*.55f;b.GetComponent<Renderer>().sharedMaterial=orange;var rb=b.AddComponent<Rigidbody>();rb.mass=.5f;rb.collisionDetectionMode=CollisionDetectionMode.ContinuousDynamic;rb.interpolation=RigidbodyInterpolation.Interpolate;var pm=new PhysicMaterial("Bouncy ball"){bounciness=.75f,dynamicFriction=.45f,staticFriction=.45f,bounceCombine=PhysicMaterialCombine.Maximum};AssetDatabase.CreateAsset(pm,AssetDatabase.GenerateUniqueAssetPath(Root+"/Generated/BallPhysics.physicMaterial"));b.GetComponent<Collider>().sharedMaterial=pm;var pickup=b.AddComponent<VRCPickup>();pickup.pickupable=true;var sync=b.AddComponent<VRCObjectSync>();var reset=Box("Return ball",new Vector3(-10,.8f,-7),new Vector3(.7f,.18f,.7f),teal);reset.AddUdonSharpComponent<BallReset>().ballSync=sync;Sign("BALL BREAK",new Vector3(-8,2,-9.7f),.3f,orange.color);Sign("Pick up. Toss. Relax.\nMint button returns the ball.",new Vector3(-8,1.4f,-9.7f),.15f,cream.color);}
 static void AddSourceFurniture(){
  var model=AssetDatabase.LoadAssetAtPath<GameObject>(Directory.GetFiles("Assets/Gaming Room","*.prefab",SearchOption.AllDirectories).First().Replace('\\','/'));if(!model)return;
  // Preserve the user's source asset and use selected mesh groups as lounge decor.
  foreach(var r in model.GetComponentsInChildren<Renderer>(true)){
   string n=r.name.ToLowerInvariant();if(r.sharedMaterials.Any(m=>m && m.name.StartsWith("Keyboard_")) && r.bounds.center.y>1.5f)continue;if(!(n=="table 1"||r.sharedMaterials.Any(m=>m && (m.name.StartsWith("Keyboard_")||m.name.StartsWith("Chiar_")||m.name.StartsWith("Mouse")||m.name.StartsWith("Monitor_Arm")))))continue;
   Mesh mesh=null;var mf=r.GetComponent<MeshFilter>();if(mf)mesh=mf.sharedMesh;var skin=r as SkinnedMeshRenderer;if(skin){string mp=Root+"/Generated/SourceMesh_"+r.name+".asset";mesh=AssetDatabase.LoadAssetAtPath<Mesh>(mp);if(!mesh){mesh=new Mesh();skin.BakeMesh(mesh);AssetDatabase.CreateAsset(mesh,mp);}}if(!mesh)continue;
   var go=new GameObject("Gaming Room / "+r.name,typeof(MeshFilter),typeof(MeshRenderer));go.transform.SetParent(decor);go.transform.position=new Vector3(11,0,-4)+r.transform.position;go.transform.rotation=r.transform.rotation;go.transform.localScale=r.transform.lossyScale;go.GetComponent<MeshFilter>().sharedMesh=mesh;if(n=="table 1")go.AddComponent<BoxCollider>();
   go.GetComponent<MeshRenderer>().sharedMaterials=r.sharedMaterials.Select(ConvertMaterial).ToArray();go.GetComponent<MeshRenderer>().shadowCastingMode=ShadowCastingMode.Off;
  }
 }
 static Material ConvertMaterial(Material source){if(!source)return panel;string path=Root+"/Generated/Source_"+source.name.Replace('/','_')+".mat";var m=AssetDatabase.LoadAssetAtPath<Material>(path);bool create=m==null;if(create)m=new Material(Shader.Find("NeonNook/SoftSurface"));m.color=source.HasProperty("_Color")?source.GetColor("_Color"):Color.white;if(source.HasProperty("_MainTex"))m.mainTexture=source.GetTexture("_MainTex");ReadSavedMaterial(source,m);m.enableInstancing=true;if(create)AssetDatabase.CreateAsset(m,path);EditorUtility.SetDirty(m);return m;}
 [MenuItem("Neon Nook/Validate preview")]
 public static void Validate(){var report=new System.Text.StringBuilder();int tris=0;foreach(var f in Object.FindObjectsOfType<MeshFilter>())if(f.sharedMesh)tris+=f.sharedMesh.triangles.Length/3;report.AppendLine("Neon Nook scene validation");report.AppendLine("Triangles: "+tris);report.AppendLine("Renderers: "+Object.FindObjectsOfType<Renderer>().Length);report.AppendLine("Seats: "+Object.FindObjectsOfType<VRCStation>().Length);report.AppendLine("Realtime lights: "+Object.FindObjectsOfType<Light>().Length);report.AppendLine("Rigidbodies: "+Object.FindObjectsOfType<Rigidbody>().Length);foreach(var u in Object.FindObjectsOfType<UdonBehaviour>())if(u.programSource==null)throw new System.Exception("Missing Udon source: "+u.name);foreach(var r in Object.FindObjectsOfType<Renderer>())foreach(var m in r.sharedMaterials)if(!m||!m.shader||m.shader.name=="Hidden/InternalErrorShader")throw new System.Exception("Missing material: "+r.name);File.WriteAllText("../validation.txt",report.ToString());Debug.Log(report);}
 [MenuItem("Neon Nook/Render preview")]
 public static void RenderPreview(){var c=GameObject.Find("Preview Camera").GetComponent<Camera>();Render(c,"../../outputs/neon-nook-overview.png",new Vector3(10.7f,8.2f,-10.4f),new Vector3(-.8f,1.2f,2.2f));Render(c,"../../outputs/neon-nook-lounge.png",new Vector3(4,2,-4.5f),new Vector3(1,2.4f,9));Render(c,"../../outputs/neon-nook-arcade.png",new Vector3(-5,3.3f,-2.8f),new Vector3(-7,1.4f,3));Render(c,"../../outputs/neon-nook-gaming-desk.png",new Vector3(7,2,-5.3f),new Vector3(9.6f,1,-8));}
 static void Render(Camera c,string file,Vector3 pos,Vector3 at){file=Path.Combine(NeonNookPaths.OutputDirectory,Path.GetFileName(file));var old=c.transform.position;var rot=c.transform.rotation;c.transform.position=pos;c.transform.LookAt(at);var rt=new RenderTexture(1600,1000,24);rt.antiAliasing=4;c.targetTexture=rt;c.Render();RenderTexture.active=rt;var t=new Texture2D(1600,1000,TextureFormat.RGB24,false);t.ReadPixels(new Rect(0,0,1600,1000),0,0);t.Apply();File.WriteAllBytes(file,t.EncodeToPNG());c.targetTexture=null;RenderTexture.active=null;Object.DestroyImmediate(rt);Object.DestroyImmediate(t);c.transform.SetPositionAndRotation(old,rot);}
}
