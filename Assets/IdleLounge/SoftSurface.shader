Shader "NeonNook/SoftSurface" {
 Properties { _Color("Color",Color)=(1,1,1,1) _MainTex("Texture",2D)="white"{} }
 SubShader { Tags { "RenderType"="Opaque" } Pass {
 CGPROGRAM
 #pragma vertex vert
 #pragma fragment frag
 #pragma multi_compile_fog
 #include "UnityCG.cginc"
 struct a {float4 vertex:POSITION; float3 normal:NORMAL; float2 uv:TEXCOORD0;};
 struct v {float4 pos:SV_POSITION; float2 uv:TEXCOORD0; float shade:TEXCOORD1; UNITY_FOG_COORDS(2)};
 sampler2D _MainTex; float4 _MainTex_ST; fixed4 _Color;
 v vert(a i){v o;o.pos=UnityObjectToClipPos(i.vertex);o.uv=TRANSFORM_TEX(i.uv,_MainTex);o.shade=.62+.38*saturate(dot(UnityObjectToWorldNormal(i.normal),normalize(float3(-.4,1,-.3))));UNITY_TRANSFER_FOG(o,o.pos);return o;}
 fixed4 frag(v i):SV_Target{fixed4 c=tex2D(_MainTex,i.uv)*_Color;c.rgb*=i.shade;UNITY_APPLY_FOG(i.fogCoord,c);return c;}
 ENDCG
 }}
}
