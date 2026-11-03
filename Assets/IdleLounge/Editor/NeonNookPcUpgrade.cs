using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.PostProcessing;
using VRC.SDK3.Components;
using Object = UnityEngine.Object;

// Applies real geometry, material and lighting changes to a copy of the playable scene.
public static class NeonNookPcUpgrade
{
    public const string ScenePath = "Assets/IdleLounge/Scenes/NeonNook-PC.unity";
    const string Root = "Assets/IdleLounge/PC";
    static Transform architecture;
    static readonly Dictionary<string, Mesh> Meshes = new Dictionary<string, Mesh>();
    static readonly Dictionary<Mesh, Mesh> LightmapMeshes = new Dictionary<Mesh, Mesh>();

    [MenuItem("Neon Nook/PC/Build and bake enhanced world")]
    public static void Build()
    {
        Assemble();
        Bake();
    }

    public static void Prepare()
    {
        Assemble();
        try { PcSceneAudit.Run(); } finally { Render(); }
    }

    static void Assemble()
    {
        UdonSharp.Compiler.UdonSharpCompilerV1.CompileSync();
        int savedHold=-1;Vector3 savedBall=Vector3.zero;
        if(File.Exists(ScenePath))
        {
            EditorSceneManager.OpenScene(ScenePath);
            var pickup=Object.FindObjectsOfType<VRCPickup>().FirstOrDefault(p=>p.name.StartsWith("Corner ball"));
            if(pickup){savedHold=new SerializedObject(pickup).FindProperty("AutoHold").intValue;savedBall=pickup.transform.position;}
        }
        EditorSceneManager.OpenScene("Assets/IdleLounge/Scenes/NeonNook.unity");
        if(savedHold>=0)
        {
            var pickup=Object.FindObjectsOfType<VRCPickup>().First(p=>p.name.StartsWith("Corner ball"));
            var settings=new SerializedObject(pickup);settings.FindProperty("AutoHold").intValue=savedHold;settings.ApplyModifiedPropertiesWithoutUndo();pickup.transform.position=savedBall;
        }
        PcMeshRepair.FixGeneratedSourceBounds();
        FixSharedLayout();
        PerimeterLayoutAudit.Run();
        EditorSceneManager.SaveScene(UnityEngine.SceneManagement.SceneManager.GetActiveScene());
        Directory.CreateDirectory(Root + "/Meshes");
        Directory.CreateDirectory(Root + "/Lighting");
        AssetDatabase.Refresh();
        EditorSceneManager.SaveScene(UnityEngine.SceneManagement.SceneManager.GetActiveScene(), ScenePath);
        PcMaterialLibrary.EnsureAll();
        architecture = new GameObject("PC architectural detailing").transform;
        ReplaceSurfaces();
        Architecture();
        Furnishings();
        var arcade=NeonNookPerimeterLayout.BuildArcade(true);
        var housing=arcade.GetComponentInChildren<MeshRenderer>();
        Vector3 housingSize=housing.transform.localScale;
        housing.GetComponent<MeshFilter>().sharedMesh=RoundedBox(housingSize,.06f);
        housing.transform.localScale=Vector3.one;housing.GetComponent<BoxCollider>().size=housingSize;
        housing.sharedMaterial=PcMaterialLibrary.Get("metal");BakeStatic(housing.gameObject);
        NeonNookPerimeterLayout.BuildPlants(architecture,true);
        Skyline();
        Lighting();
        ConfigureCamera();
        AssetDatabase.SaveAssets();
        EditorSceneManager.SaveScene(UnityEngine.SceneManagement.SceneManager.GetActiveScene());
    }

    public static void Bake()
    {
        EditorSceneManager.OpenScene(ScenePath);
        PcSceneAudit.Run();
        PerimeterLayoutAudit.Run();
        Debug.Log("PC_BAKE_BEGIN");
        if (!Lightmapping.Bake()) throw new Exception("PC light bake failed.");
        if (LightmapSettings.lightmaps.Length == 0) throw new Exception("No baked lightmaps were produced.");
        foreach (var probe in Object.FindObjectsOfType<ReflectionProbe>())
        {
            string path = Root + "/Lighting/" + probe.name + ".exr";
            if (!Lightmapping.BakeReflectionProbe(probe, path)) throw new Exception("Reflection bake failed: " + probe.name);
        }
        AssetDatabase.SaveAssets();
        EditorSceneManager.SaveScene(UnityEngine.SceneManagement.SceneManager.GetActiveScene());
        Debug.Log("PC_BAKE_PASS: " + LightmapSettings.lightmaps.Length + " lightmaps");
        PcSceneAudit.Run();
        Render();
        CleanupGeneratedAssets();
    }

    public static void RefineAndBake()
    {
        EditorSceneManager.OpenScene(ScenePath);
        foreach(var renderer in Object.FindObjectsOfType<MeshRenderer>())
        {
            if(renderer.name.StartsWith("Gaming Room /") && !renderer.GetComponent<BoxCollider>())
            { renderer.receiveGI=ReceiveGI.LightProbes;renderer.lightProbeUsage=LightProbeUsage.BlendProbes; }
            if(renderer.name=="City tower")renderer.lightProbeUsage=LightProbeUsage.Off;
        }
        CityMaterial();
        AssetDatabase.SaveAssets();EditorSceneManager.SaveScene(UnityEngine.SceneManagement.SceneManager.GetActiveScene());
        Bake();
        CleanupGeneratedAssets();
    }

    static void CleanupGeneratedAssets()
    {
        var used=new HashSet<string>(AssetDatabase.GetDependencies(ScenePath,true));
        foreach(string folder in new[]{Root+"/Meshes",Root+"/Lighting"})
            foreach(string guid in AssetDatabase.FindAssets("",new[]{folder}))
            {
                string path=AssetDatabase.GUIDToAssetPath(guid);
                if(path.StartsWith(folder+"/",StringComparison.Ordinal) && File.Exists(path) && !used.Contains(path))AssetDatabase.DeleteAsset(path);
            }
        AssetDatabase.SaveAssets();
    }

    public static void FixSharedLayout()
    {
        NeonNookPerimeterLayout.Apply();
    }

    static void ReplaceSurfaces()
    {
        var arcade=GameObject.Find("Arcade - Wall Control Lounge").transform;
        var botanicals=GameObject.Find("Lounge botanicals").transform;
        foreach (var r in Object.FindObjectsOfType<MeshRenderer>())
        {
            if(r.transform.IsChildOf(arcade) || r.transform.IsChildOf(botanicals))continue;
            string n = r.name;
            if (n == "Floor seam" || n == "Window horizon" || n == "Window frame" || n == "East lintel" || n == "Distant skyline" || n == "Sculptural foliage")
            { Object.DestroyImmediate(r.gameObject); continue; }
            if (r.GetComponent<TextMesh>() || n.Contains("Video Screen")) continue;
            if (n.StartsWith("Gaming Room /"))
            {
                var importedFilter=r.GetComponent<MeshFilter>();
                if(importedFilter && importedFilter.sharedMesh && AssetDatabase.GetAssetPath(importedFilter.sharedMesh).StartsWith("Assets/IdleLounge/Generated/SourceMesh_"))
                    importedFilter.sharedMesh=PcMeshRepair.CleanCopy(importedFilter.sharedMesh);
                r.sharedMaterials = r.sharedMaterials.Select(UpgradeImported).ToArray();
                r.shadowCastingMode = ShadowCastingMode.On;
                r.receiveShadows = true;
                BakeStatic(r.gameObject);
                continue;
            }
            string old = r.sharedMaterial ? r.sharedMaterial.name : "";
            string key = "wall";
            if (n == "Main floor") key = "floor";
            else if (n.Contains("Cushion") || n == "Backrest" || n == "Armrest")
                key = old == "Mint" ? "fabricTeal" : old == "Coral" ? "fabricRose" : "fabricCream";
            else if (n.Contains("Table") || n.StartsWith("Side table surface") || n == "Coffee table" || n.Contains("Window") || n == "East lintel") key = "wood";
            else if (n.StartsWith("Side table base")) key = "metal";
            else if (n == "Inlaid rug") key = "rug";
            else if (n == "Rug edge") key = "brass";
            else if (n == "North crown" || n == "West crown" || n == "Cyan edge") key = "emitTeal";
            else if (n == "PLAY +2" || n == "Return ball") key = "emitTeal";
            else if (new[]{"CABINET","SNACK BAR","LOUNGE","BOOST","REOPEN"}.Contains(n)) key = "brass";
            else if (n == "Planter") key = "metal";
            else if (n.StartsWith("Corner ball")) key = "fabricRose";
            r.sharedMaterial = PcMaterialLibrary.Get(key);
            r.shadowCastingMode = ShadowCastingMode.On;
            r.receiveShadows = true;
            var mf = r.GetComponent<MeshFilter>();
            var box = r.GetComponent<BoxCollider>();
            // Unity's primitive cube UVs stretch with scale; replace them with metric UV meshes.
            if (mf && mf.sharedMesh && mf.sharedMesh.name == "Cube")
            {
                Vector3 size = r.transform.localScale;
                float radius = n == "Cushion" ? .095f : n == "Backrest" ? .085f : n == "Armrest" ? .075f : n.Contains("table") || n.Contains("Table") ? .035f : .015f;
                mf.sharedMesh = RoundedBox(size, radius);
                r.transform.localScale = Vector3.one;
                if (box) box.size = size;
            }
            if (!r.GetComponent<Rigidbody>()) BakeStatic(r.gameObject);
        }
        // The source sign was hidden behind the top of the video frame.
        var living = GameObject.Find("THE LIVING ROOM");
        if (living) living.transform.position = new Vector3(4, 6.05f, 10.5f);
        var brand=Object.FindObjectsOfType<TextMesh>().FirstOrDefault(t=>t.name=="NEON NOOK");
        if(brand){brand.transform.position=new Vector3(-6.2f,5.95f,10.65f);brand.GetComponent<Renderer>().enabled=true;}
        // Keep collision geometry stable; make the border beams visibly intentional.
        var foundation = GameObject.Find("Foundation");
        if (foundation) foundation.GetComponent<Renderer>().sharedMaterial = PcMaterialLibrary.Get("metal");
    }

    static Material UpgradeImported(Material source)
    {
        if (!source) return PcMaterialLibrary.Get("wall");
        string path = Root + "/Materials/" + source.name + "_PBR.mat";
        var material = AssetDatabase.LoadAssetAtPath<Material>(path);
        if (!material) { material = new Material(Shader.Find("Standard")); AssetDatabase.CreateAsset(material, path); }
        material.color = source.HasProperty("_Color") ? source.color : Color.white;
        material.mainTexture = source.mainTexture;
        material.SetFloat("_Glossiness", source.name.Contains("Metal") ? .65f : .32f);
        material.SetFloat("_Metallic", source.name.Contains("Metal") ? .75f : 0);
        material.enableInstancing = true;
        return material;
    }

    static void Architecture()
    {
        // A real ceiling and deep coves make the interior read as a room.
        Box("PC ceiling", new Vector3(0,6.5f,0), new Vector3(24,.25f,22), "wall");
        for (int i=0;i<5;i++)
        {
            float x=-10+i*5;
            Box("Walnut ceiling rib",new Vector3(x,6.16f,0),new Vector3(.18f,.45f,22),"wood");
            Box("Ceiling light channel",new Vector3(x+.18f,6.12f,0),new Vector3(.045f,.035f,19),"emitAmber",false);
        }
        Box("North upper wall",new Vector3(0,5.95f,11),new Vector3(24,1.3f,.3f),"wall");
        Box("West upper wall",new Vector3(-12,5.95f,0),new Vector3(.3f,1.3f,22),"wall");
        Box("North cove valance",new Vector3(0,5.58f,10.55f),new Vector3(24,.22f,.6f),"wood");
        Box("North warm cove",new Vector3(0,5.73f,10.72f),new Vector3(23,.035f,.065f),"emitAmber",false);
        Box("West cove valance",new Vector3(-11.55f,5.58f,0),new Vector3(.6f,.22f,22),"wood");
        Box("West warm cove",new Vector3(-11.72f,5.73f,0),new Vector3(.065f,.035f,21),"emitAmber",false);
        // Lower wall cladding, recessed seams and slats add physical depth.
        for(int i=0;i<48;i++) Box("North acoustic timber slat",new Vector3(-11.7f+i*.5f,2.6f,10.79f),new Vector3(.065f,5,.08f),"wood",false);
        for(int i=0;i<20;i++) Box("Arcade acoustic slat",new Vector3(-11.77f,2.55f,2.8f+i*.36f),new Vector3(.08f,4.8f,.055f),"wood",false);
        Box("North baseboard",new Vector3(0,.16f,10.76f),new Vector3(24,.3f,.15f),"wood");
        Box("West baseboard",new Vector3(-11.76f,.16f,0),new Vector3(.15f,.3f,22),"wood");
        // Window frames have actual reveals; glass uses world reflections.
        const float eastBay=22f/7;
        for(int i=0;i<7;i++)
        {
            float z=-11+eastBay*(i+.5f);
            Box("City glazing",new Vector3(11.96f,3.55f,z),new Vector3(.028f,4.45f,eastBay-.12f),"glass",false);
            Box("Window sill",new Vector3(11.69f,1.23f,z),new Vector3(.52f,.14f,eastBay),"wood");
        }
        for(int i=0;i<=7;i++)Box("East mullion",new Vector3(11.88f,3.5f,-11+i*eastBay),new Vector3(.22f,4.6f,.12f),"wood");
        Box("East window header",new Vector3(11.9f,6.1f,0),new Vector3(.3f,.6f,22),"wood");
        for(int i=0;i<8;i++)
        {
            float x=-12+i*3;
            Box("South mullion",new Vector3(x,3.5f,-10.9f),new Vector3(.12f,4.6f,.2f),"wood");
            Box("South glazing",new Vector3(x+1.5f,3.5f,-10.95f),new Vector3(2.88f,4.5f,.028f),"glass",false);
        }
        Box("South mullion",new Vector3(12,3.5f,-10.9f),new Vector3(.12f,4.6f,.2f),"wood");
        Box("South window header",new Vector3(0,6.1f,-10.9f),new Vector3(24,.6f,.3f),"wood");
        Ring("Living room pendant",new Vector3(2,5.35f,3.5f),2.65f,.045f,"emitAmber");
        Ring("Living room pendant housing",new Vector3(2,5.43f,3.5f),2.68f,.08f,"metal");
        Ring("Arcade pendant",new Vector3(-7,5.3f,7),1.85f,.035f,"emitTeal");
        for(int i=0;i<3;i++)
        {
            float a=i*Mathf.PI*2/3;
            Box("Pendant suspension",new Vector3(2+Mathf.Cos(a)*2.6f,5.95f,3.5f+Mathf.Sin(a)*2.6f),new Vector3(.018f,1.1f,.018f),"metal",false);
        }
    }

    static void Furnishings()
    {
        // Split bench cushions and seams. Keep all existing station transforms.
        foreach (var sofa in Object.FindObjectsOfType<Transform>().Where(t=>t.name.StartsWith("Sofa with two VR seats")).ToArray())
        {
            var cushion=sofa.Find("Cushion");
            if (cushion)
            {
                Material fabric=cushion.GetComponent<Renderer>().sharedMaterial;
                Object.DestroyImmediate(cushion.gameObject);
                for(int i=0;i<2;i++)
                {
                    var c=Box("Tailored seat cushion",Vector3.zero,new Vector3(1.25f,.23f,1.07f),"fabricCream",true,.095f);
                    c.GetComponent<Renderer>().sharedMaterial=fabric;
                    c.transform.SetParent(sofa,false);c.transform.localPosition=new Vector3(i==0?-.64f:.64f,.54f,.03f);
                }
            }
            for(int i=0;i<2;i++)
            {
                var pillow=Box("Loose throw pillow",Vector3.zero,new Vector3(.48f,.45f,.18f),i==0?"fabricRose":"fabricCream",false,.075f);
                pillow.transform.SetParent(sofa,false);pillow.transform.localPosition=new Vector3(i==0?-.95f:.95f,.89f,-.28f);pillow.transform.localRotation=Quaternion.Euler(-12,0,i==0?8:-8);
            }
        }
        // Quiet, small props ground the room in a believable scale.
        foreach(var table in Object.FindObjectsOfType<Transform>().Where(t=>t.name.StartsWith("Side table surface")).ToArray())
        {
            Vector3 p=table.position;
            var book=Box("Art book",p+table.right*-.20f+Vector3.up*.095f,new Vector3(.22f,.04f,.16f),"fabricRose",false);book.transform.rotation=table.rotation;
            var pages=Box("Book pages",p+table.right*-.20f+Vector3.up*.120f,new Vector3(.21f,.01f,.15f),"fabricCream",false);pages.transform.rotation=table.rotation;
            Cylinder("Tea cup",p+table.right*.30f+Vector3.up*.125f,.055f,.10f,"fabricCream");
            Cylinder("Coffee surface",p+table.right*.30f+Vector3.up*.177f,.045f,.003f,"wood");
        }
        foreach(var p in new[]{new Vector3(11.4f,0,-1.9f),new Vector3(11.4f,0,1.9f),new Vector3(-2.7f,0,-10.5f)})
        {
            Cylinder("Floor lamp foot",p+Vector3.up*.055f,.24f,.11f,"metal");
            Cylinder("Floor lamp stem",p+Vector3.up*.9f,.022f,1.7f,"brass");
            Cylinder("Lamp luminous diffuser",p+Vector3.up*1.82f,.17f,.52f,"emitAmber");
            Cylinder("Lamp top cap",p+Vector3.up*2.1f,.185f,.045f,"brass");
        }
    }

    static void Skyline()
    {
        var city=new GameObject("Distant city geometry").transform;
        Material facade=CityMaterial();
        for(int side=0;side<2;side++) for(int i=0;i<20;i++)
        {
            float height=7+(i*17%16), width=3+(i%3);
            Vector3 p=side==0?new Vector3(24+(i%4)*5,height*.5f-5,-38+i*4):new Vector3(-38+i*4,height*.5f-5,-25-(i%4)*5);
            var b=Box("City tower",p,new Vector3(width,height,3.7f),"metal",false);
            b.transform.SetParent(city);b.GetComponent<Renderer>().sharedMaterial=facade;
        }
    }
    static Material CityMaterial()
    {
        string texPath=Root+"/Textures/CityWindows.png";
        var tex=new Texture2D(128,128,TextureFormat.RGB24,false);
        for(int y=0;y<128;y++)for(int x=0;x<128;x++)
        {
            int cellX=x/16,cellY=y/16;bool on=(cellX*31+cellY*17)%7>2;
            bool pane=x%16>5 && x%16<11 && y%16>4 && y%16<12;
            Color light=(cellX+cellY)%3==0?new Color(.7f,.5f,.27f):new Color(.36f,.5f,.68f);
            tex.SetPixel(x,y,pane&&on?light:new Color(.008f,.015f,.025f));
        }
        tex.Apply();File.WriteAllBytes(texPath,tex.EncodeToPNG());Object.DestroyImmediate(tex);AssetDatabase.ImportAsset(texPath);
        string path=Root+"/Materials/CityFacade.mat";var m=AssetDatabase.LoadAssetAtPath<Material>(path);
        if(!m){m=new Material(Shader.Find("Standard"));AssetDatabase.CreateAsset(m,path);}
        m.mainTexture=AssetDatabase.LoadAssetAtPath<Texture2D>(texPath);m.mainTextureScale=Vector2.one*.25f;m.color=new Color(.18f,.2f,.23f);m.SetFloat("_Glossiness",.15f);
        m.SetTexture("_EmissionMap",m.mainTexture);m.SetColor("_EmissionColor",Color.white*1.2f);m.globalIlluminationFlags=MaterialGlobalIlluminationFlags.BakedEmissive;m.EnableKeyword("_EMISSION");EditorUtility.SetDirty(m);return m;
    }

    static void Lighting()
    {
        var settings=new LightingSettings();settings.name="PC baked interior";
        settings.bakedGI=true;settings.realtimeGI=false;settings.lightmapper=LightingSettings.Lightmapper.ProgressiveCPU;
        settings.lightmapResolution=16;settings.lightmapMaxSize=1024;settings.lightmapPadding=4;
        settings.directSampleCount=32;settings.indirectSampleCount=128;settings.environmentSampleCount=64;
        settings.maxBounces=3;settings.ao=true;settings.aoMaxDistance=.85f;settings.aoExponentIndirect=.65f;settings.aoExponentDirect=.2f;
        LightmapSettings.lightmapsMode=LightmapsMode.CombinedDirectional;
        string sp=Root+"/Lighting/PC-LightingSettings.lighting";
        AssetDatabase.CreateAsset(settings,AssetDatabase.GenerateUniqueAssetPath(sp));Lightmapping.lightingSettings=settings;
        Lightmapping.giWorkflowMode=Lightmapping.GIWorkflowMode.OnDemand;
        RenderSettings.ambientMode=AmbientMode.Trilight;RenderSettings.ambientSkyColor=new Color(.2f,.27f,.4f);
        RenderSettings.ambientEquatorColor=new Color(.13f,.17f,.24f);RenderSettings.ambientGroundColor=new Color(.065f,.065f,.085f);
        RenderSettings.ambientIntensity=.75f;RenderSettings.reflectionIntensity=.8f;
        var sky=new Material(Shader.Find("Skybox/Procedural"));sky.SetFloat("_SunSize",0);sky.SetFloat("_Exposure",.35f);sky.SetColor("_SkyTint",new Color(.12f,.2f,.4f));sky.SetColor("_GroundColor",new Color(.025f,.03f,.07f));
        AssetDatabase.CreateAsset(sky,AssetDatabase.GenerateUniqueAssetPath(Root+"/Materials/NightSky.mat"));RenderSettings.skybox=sky;
        RenderSettings.fog=true;RenderSettings.fogMode=FogMode.ExponentialSquared;RenderSettings.fogDensity=.006f;RenderSettings.fogColor=new Color(.035f,.05f,.09f);
        for(int x=-8;x<=8;x+=8)for(int z=-7;z<=7;z+=7)
            Area("Warm ceiling wash",new Vector3(x,5.95f,z),new Color(1,.85f,.7f),14.8f,new Vector2(3,3),Quaternion.Euler(90,0,0));
        for(int z=-7;z<=8;z+=5)Area("Cool city fill",new Vector3(11.3f,3.5f,z),new Color(.45f,.68f,1),8,new Vector2(3,3),Quaternion.Euler(0,-90,0));
        for(int x=-9;x<=9;x+=6) Point("Amber cove bounce",new Vector3(x,5.15f,10),new Color(1,.64f,.35f),3,7);
        Point("Arcade cyan bounce",new Vector3(-7,3.8f,8),new Color(.25f,.82f,1),4,6);
        Point("Cinema gentle fill",new Vector3(4,3,8),new Color(.5f,.7f,1),3,8);
        foreach(var p in new[]{new Vector3(11.4f,1.8f,-1.9f),new Vector3(11.4f,1.8f,1.9f),new Vector3(-2.7f,1.8f,-10.5f)})Point("Lamp light",p,new Color(1,.78f,.55f),4.5f,5);
        // One modest non-shadowed realtime key supports avatars alongside baked probes.
        var key=new GameObject("Avatar fill").AddComponent<Light>();key.type=LightType.Directional;key.color=new Color(.6f,.73f,1);key.intensity=.22f;key.shadows=LightShadows.None;key.transform.rotation=Quaternion.Euler(40,-60,0);
        QualitySettings.pixelLightCount=2;QualitySettings.antiAliasing=4;
        var probes=new GameObject("Baked light probe grid").AddComponent<LightProbeGroup>();var points=new List<Vector3>();
        for(int x=-10;x<=10;x+=4)for(int z=-9;z<=9;z+=3)foreach(float y in new[]{.4f,1.7f,3.8f})points.Add(new Vector3(x,y,z));probes.probePositions=points.ToArray();
        foreach(var p in new[]{new Vector3(2,2.5f,2),new Vector3(-7,2.5f,2)})
        {
            var rp=new GameObject(p.x>0?"LoungeReflection":"ArcadeReflection").AddComponent<ReflectionProbe>();rp.transform.position=p;rp.mode=ReflectionProbeMode.Baked;rp.resolution=256;rp.size=p.x>0?new Vector3(18,7,23):new Vector3(8,7,20);rp.boxProjection=true;rp.blendDistance=2;rp.hdr=true;rp.intensity=.8f;rp.clearFlags=ReflectionProbeClearFlags.Skybox;
        }
    }
    static void Area(string name,Vector3 p,Color c,float power,Vector2 size,Quaternion rotation)
    {var l=new GameObject(name).AddComponent<Light>();l.transform.position=p;l.transform.rotation=rotation;l.type=LightType.Area;l.lightmapBakeType=LightmapBakeType.Baked;l.areaSize=size;l.color=c;l.intensity=power;l.range=14;l.shadows=LightShadows.Soft;}
    static void Point(string name,Vector3 p,Color c,float power,float range)
    {var l=new GameObject(name).AddComponent<Light>();l.transform.position=p;l.type=LightType.Point;l.lightmapBakeType=LightmapBakeType.Baked;l.color=c;l.intensity=power;l.range=range;l.shadows=LightShadows.Soft;}

    static void ConfigureCamera()
    {
        var profile=ScriptableObject.CreateInstance<PostProcessProfile>();
        var bloom=profile.AddSettings<Bloom>();bloom.enabled.Override(true);bloom.intensity.Override(.35f);bloom.threshold.Override(1.15f);bloom.softKnee.Override(.6f);
        var grade=profile.AddSettings<ColorGrading>();grade.enabled.Override(true);grade.gradingMode.Override(GradingMode.HighDefinitionRange);grade.tonemapper.Override(Tonemapper.ACES);grade.postExposure.Override(.45f);grade.saturation.Override(8);grade.contrast.Override(5);
        string path=Root+"/Lighting/PC-PostProcess.asset";AssetDatabase.CreateAsset(profile,AssetDatabase.GenerateUniqueAssetPath(path));
        foreach(var setting in profile.settings)AssetDatabase.AddObjectToAsset(setting,profile);
        var volume=new GameObject("PC restrained bloom and grading").AddComponent<PostProcessVolume>();volume.isGlobal=true;volume.priority=1;volume.sharedProfile=profile;
        var camera=GameObject.Find("Preview Camera").GetComponent<Camera>();camera.clearFlags=CameraClearFlags.Skybox;camera.allowHDR=true;camera.allowMSAA=true;camera.farClipPlane=180;camera.fieldOfView=68;
        var layer=camera.gameObject.AddComponent<PostProcessLayer>();layer.volumeLayer=~0;layer.volumeTrigger=camera.transform;layer.antialiasingMode=PostProcessLayer.Antialiasing.FastApproximateAntialiasing;
        layer.Init(AssetDatabase.LoadAssetAtPath<PostProcessResources>("Packages/com.unity.postprocessing/PostProcessing/PostProcessResources.asset"));
    }

    static void BakeStatic(GameObject g)
    {
        var r=g.GetComponent<MeshRenderer>();
        if(!r)return;
        bool city=g.name=="City tower";
        GameObjectUtility.SetStaticEditorFlags(g,(city?0:StaticEditorFlags.ContributeGI)|StaticEditorFlags.BatchingStatic|StaticEditorFlags.ReflectionProbeStatic);
        float[] dimensions={r.bounds.size.x,r.bounds.size.y,r.bounds.size.z};Array.Sort(dimensions);
        bool probeLit=city || dimensions[1]<.28f || g.name.Contains("Broad leaf") || g.name.Contains("glazing") || g.name.Contains("pendant") || (g.name.StartsWith("Gaming Room /") && !g.GetComponent<BoxCollider>());
        r.receiveGI=probeLit?ReceiveGI.LightProbes:ReceiveGI.Lightmaps;r.lightProbeUsage=city?LightProbeUsage.Off:LightProbeUsage.BlendProbes;
        var mf=g.GetComponent<MeshFilter>();
        if(probeLit || !mf || !mf.sharedMesh)return;
        if(!mf.sharedMesh.name.StartsWith("Box2_"))
        {
            Mesh source=mf.sharedMesh;
            if(!LightmapMeshes.TryGetValue(source,out var copy))
            {
                copy=Object.Instantiate(source);copy.name=source.name+"_PaddedGI";PcLightmapUv.Unwrap(copy);
                string path=AssetDatabase.GenerateUniqueAssetPath(Root+"/Meshes/"+copy.name.Replace('/','_')+".asset");AssetDatabase.CreateAsset(copy,path);LightmapMeshes[source]=copy;
            }
            mf.sharedMesh=copy;
        }
        // Reserve enough texels for a two-pixel gutter even on a coffee table.
        float area=0;var verts=mf.sharedMesh.vertices;var indices=mf.sharedMesh.triangles;
        for(int i=0;i<indices.Length;i+=3)
            area+=Vector3.Cross(g.transform.TransformVector(verts[indices[i+1]]-verts[indices[i]]),g.transform.TransformVector(verts[indices[i+2]]-verts[indices[i]])).magnitude*.5f;
        var so=new SerializedObject(r);so.FindProperty("m_ScaleInLightmap").floatValue=Mathf.Max(1,72/(Mathf.Sqrt(Mathf.Max(.001f,area))*16));so.ApplyModifiedPropertiesWithoutUndo();
    }

    static GameObject Box(string name,Vector3 pos,Vector3 size,string mat,bool collide=true,float radius=.015f)
    {
        var g=new GameObject(name,typeof(MeshFilter),typeof(MeshRenderer));g.transform.SetParent(architecture);g.transform.position=pos;
        g.GetComponent<MeshFilter>().sharedMesh=RoundedBox(size,radius);g.GetComponent<Renderer>().sharedMaterial=PcMaterialLibrary.Get(mat);
        if(collide)g.AddComponent<BoxCollider>().size=size;BakeStatic(g);return g;
    }
    static void Cylinder(string name,Vector3 pos,float radius,float height,string mat)
    {var g=GameObject.CreatePrimitive(PrimitiveType.Cylinder);g.name=name;g.transform.SetParent(architecture);g.transform.position=pos;g.transform.localScale=new Vector3(radius*2,height/2,radius*2);g.GetComponent<Renderer>().sharedMaterial=PcMaterialLibrary.Get(mat);Object.DestroyImmediate(g.GetComponent<Collider>());BakeStatic(g);}

    static Mesh RoundedBox(Vector3 size,float radius)
    {
        size=new Vector3(Mathf.Abs(size.x),Mathf.Abs(size.y),Mathf.Abs(size.z));radius=Mathf.Min(radius,Mathf.Min(size.x,Mathf.Min(size.y,size.z))*.45f);
        string key=$"Box2_{size.x:F3}_{size.y:F3}_{size.z:F3}_{radius:F3}";
        if(Meshes.TryGetValue(key,out var cached))return cached;
        string path=Root+"/Meshes/"+key+".asset";var existing=AssetDatabase.LoadAssetAtPath<Mesh>(path);if(existing){Meshes[key]=existing;return existing;}
        var vertices=new List<Vector3>();var normals=new List<Vector3>();var uv=new List<Vector2>();var triangles=new List<int>();
        Vector3 half=size*.5f,inner=half-Vector3.one*radius;
        Vector3[] normal={Vector3.right,Vector3.left,Vector3.up,Vector3.down,Vector3.forward,Vector3.back};
        foreach(var n in normal)
        {
            Vector3 u=Mathf.Abs(n.y)>.5f?Vector3.right:Vector3.Cross(Vector3.up,n);Vector3 v=Vector3.Cross(n,u);
            float hu=Vector3.Dot(Abs(u),half),hv=Vector3.Dot(Abs(v),half),hn=Vector3.Dot(Abs(n),half);
            float[] xs=Steps(hu,radius),ys=Steps(hv,radius);int start=vertices.Count;
            for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            {
                Vector3 p=n*hn+u*xs[x]+v*ys[y];Vector3 q=new Vector3(Mathf.Clamp(p.x,-inner.x,inner.x),Mathf.Clamp(p.y,-inner.y,inner.y),Mathf.Clamp(p.z,-inner.z,inner.z));Vector3 d=(p-q).normalized;
                vertices.Add(q+d*radius);normals.Add(d);uv.Add(new Vector2(xs[x]+hu,ys[y]+hv));
            }
            for(int y=0;y<7;y++)for(int x=0;x<7;x++){int a=start+y*8+x;triangles.AddRange(new[]{a,a+1,a+9,a,a+9,a+8});}
        }
        var mesh=new Mesh{name=key};mesh.SetVertices(vertices);mesh.SetNormals(normals);mesh.SetUVs(0,uv);mesh.SetTriangles(triangles,0);mesh.RecalculateTangents();mesh.RecalculateBounds();mesh.uv2=PcLightmapUv.BoxCharts(size,radius);AssetDatabase.CreateAsset(mesh,path);Meshes[key]=mesh;return mesh;
    }
    static Vector3 Abs(Vector3 v){return new Vector3(Mathf.Abs(v.x),Mathf.Abs(v.y),Mathf.Abs(v.z));}
    static float[] Steps(float h,float r){return new[]{-h,-h+r*.134f,-h+r*.5f,-h+r,h-r,h-r*.5f,h-r*.134f,h};}
    static void Ring(string name,Vector3 pos,float major,float minor,string mat)
    {
        var verts=new List<Vector3>();var normals=new List<Vector3>();var uv=new List<Vector2>();var tris=new List<int>();const int aCount=96,bCount=8;
        for(int a=0;a<=aCount;a++)for(int b=0;b<=bCount;b++){float t=a*Mathf.PI*2/aCount,s=b*Mathf.PI*2/bCount;Vector3 n=new Vector3(Mathf.Cos(t)*Mathf.Cos(s),Mathf.Sin(s),Mathf.Sin(t)*Mathf.Cos(s));verts.Add(new Vector3(Mathf.Cos(t)*major,0,Mathf.Sin(t)*major)+n*minor);normals.Add(n);uv.Add(new Vector2(t*major,s*minor));}
        for(int a=0;a<aCount;a++)for(int b=0;b<bCount;b++){int i=a*(bCount+1)+b;tris.AddRange(new[]{i,i+1,i+9,i+1,i+10,i+9});}
        var mesh=new Mesh{name=name};mesh.SetVertices(verts);mesh.SetNormals(normals);mesh.SetUVs(0,uv);mesh.SetTriangles(tris,0);mesh.RecalculateTangents();Unwrapping.GenerateSecondaryUVSet(mesh);AssetDatabase.CreateAsset(mesh,AssetDatabase.GenerateUniqueAssetPath(Root+"/Meshes/"+name+".asset"));
        var go=new GameObject(name,typeof(MeshFilter),typeof(MeshRenderer));go.transform.SetParent(architecture);go.transform.position=pos;go.GetComponent<MeshFilter>().sharedMesh=mesh;go.GetComponent<Renderer>().sharedMaterial=PcMaterialLibrary.Get(mat);BakeStatic(go);
    }

    [MenuItem("Neon Nook/PC/Render eye-level captures")]
    public static void Render()
    {
        var c=GameObject.Find("Preview Camera").GetComponent<Camera>();
        Capture(c,"pc-entry",new Vector3(-.5f,1.75f,-6.8f),new Vector3(1,2.4f,7));
        Capture(c,"pc-lounge",new Vector3(-3.5f,1.7f,3.5f),new Vector3(9.8f,1.3f,1.5f));
        Capture(c,"pc-arcade",new Vector3(-7,2.65f,6.5f),new Vector3(-7,2.8f,10.1f));
        Capture(c,"pc-desk",new Vector3(6.8f,1.7f,-4.7f),new Vector3(9.6f,1,-8));
        Capture(c,"pc-material-detail",new Vector3(7.8f,1.1f,.7f),new Vector3(10.4f,.75f,0));
        Capture(c,"pc-plants",new Vector3(-8.8f,1.2f,5.6f),new Vector3(-10.8f,.9f,7));
    }
    public static void RenderSaved(){EditorSceneManager.OpenScene(ScenePath);Render();}
    public static void CaptureRuntimeCamera(Camera camera){Capture(camera,"pc-clientsim",camera.transform.position,camera.transform.position+camera.transform.forward*10);}
    static void Capture(Camera c,string name,Vector3 pos,Vector3 target)
    {
        c.transform.position=pos;c.transform.LookAt(target);c.fieldOfView=68;
        var rt=new RenderTexture(1920,1080,24,RenderTextureFormat.ARGBHalf,RenderTextureReadWrite.Linear);
        c.targetTexture=rt;c.Render();
        // PPv2 chooses its intermediate format from targetTexture. Keep HDR through
        // bloom/tonemapping, then let the GPU write the final PNG target as sRGB.
        var ldr=new RenderTexture(1920,1080,0,RenderTextureFormat.ARGB32,RenderTextureReadWrite.sRGB);
        bool previousWrite=GL.sRGBWrite;GL.sRGBWrite=true;Graphics.Blit(rt,ldr);GL.sRGBWrite=previousWrite;RenderTexture.active=ldr;
        var texture=new Texture2D(1920,1080,TextureFormat.RGB24,false);texture.ReadPixels(new Rect(0,0,1920,1080),0,0);texture.Apply();
        File.WriteAllBytes(Path.Combine(NeonNookPaths.OutputDirectory,name+".png"),texture.EncodeToPNG());c.targetTexture=null;RenderTexture.active=null;Object.DestroyImmediate(rt);Object.DestroyImmediate(ldr);Object.DestroyImmediate(texture);
    }
    public static void Open()
    {
        EditorSceneManager.OpenScene(ScenePath);
        var view=SceneView.lastActiveSceneView;
        if(view)
        {
            view.sceneLighting=true;view.sceneViewState.showImageEffects=true;
            Vector3 eye=new Vector3(0,1.75f,-7.2f), target=new Vector3(1,2.35f,3.2f);
            float size=Vector3.Distance(eye,target)*Mathf.Sin(view.camera.fieldOfView*.5f*Mathf.Deg2Rad);
            view.orthographic=false;view.LookAtDirect(target,Quaternion.LookRotation(target-eye),size);view.Repaint();
        }
    }
}
