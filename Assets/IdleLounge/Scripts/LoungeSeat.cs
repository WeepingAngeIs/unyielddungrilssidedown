using UdonSharp;
using UnityEngine;
using VRC.SDKBase;
using VRC.SDK3.Components;

[UdonBehaviourSyncMode(BehaviourSyncMode.None)]
public class LoungeSeat : UdonSharpBehaviour
{
    public VRC.SDKBase.VRCStation station;
    public override void Interact()
    {
        if (station != null && Utilities.IsValid(Networking.LocalPlayer)) station.UseStation(Networking.LocalPlayer);
    }
}
