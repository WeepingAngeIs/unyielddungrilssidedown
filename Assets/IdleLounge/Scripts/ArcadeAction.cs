using UdonSharp;
using UnityEngine;

/// <summary>Attach to a collider button, or wire a UI button to Activate.</summary>
[UdonBehaviourSyncMode(BehaviourSyncMode.None)]
public class ArcadeAction : UdonSharpBehaviour
{
    public ArcadeEconomy economy;
    [Tooltip("0 Play, 1 Cabinet, 2 Snack bar, 3 VIP lounge, 4 Boost, 5 Reopen")]
    public int action;

    public override void Interact()
    {
        Activate();
    }

    public void Activate()
    {
        if (economy == null) return;
        switch (action)
        {
            case 0: economy.Tap(); break;
            case 1: economy.BuyCabinet(); break;
            case 2: economy.BuySnackBar(); break;
            case 3: economy.BuyLounge(); break;
            case 4: economy.BuyBoost(); break;
            case 5: economy.Prestige(); break;
        }
    }
}
