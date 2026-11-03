using UdonSharp;
using UnityEngine;
using VRC.SDKBase;
using VRC.SDK3.Components;

[UdonBehaviourSyncMode(BehaviourSyncMode.None)]
public class BallReset : UdonSharpBehaviour
{
    public VRCObjectSync ballSync;
    public override void Interact() { ResetBall(); }
    public void ResetBall()
    {
        if (ballSync == null || !Utilities.IsValid(Networking.LocalPlayer)) return;
        VRC_Pickup pickup = (VRC_Pickup)ballSync.GetComponent(typeof(VRC_Pickup));
        if (pickup != null && pickup.IsHeld) return;
        Networking.SetOwner(Networking.LocalPlayer, ballSync.gameObject);
        ballSync.Respawn();
    }
}
