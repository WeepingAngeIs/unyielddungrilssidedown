using UdonSharp;
using UnityEngine;
using UnityEngine.UI;

/// <summary>Personal, session-local economy. No network ownership or shared wallet.</summary>
[UdonBehaviourSyncMode(BehaviourSyncMode.None)]
public class ArcadeEconomy : UdonSharpBehaviour
{
    [Header("World-space display")]
    public Text statusText;
    public Text detailText;
    public Text feedbackText;

    [Header("Tuning")]
    public float startingCredits = 25f;
    public float prestigeThreshold = 25000f;

    [HideInInspector] public float credits;
    [HideInInspector] public float runEarned;
    [HideInInspector] public int cabinets;
    [HideInInspector] public int snackBars;
    [HideInInspector] public int lounges;
    [HideInInspector] public int boosts;
    [HideInInspector] public int stars;

    private float refreshTimer;
    private float prestigeConfirmUntil = -1f;
    private float nextTapTime;
    private const float MaxCredits = 1000000000000000f;

    private void Start()
    {
        credits = startingCredits;
        Say("Welcome! Tap PLAY or relax: your starter arcade earns automatically.");
        RefreshDisplay();
    }

    private void Update()
    {
        // Accrue elapsed in-session time, including a slow frame. No offline reward.
        Earn(IncomePerSecond() * Time.deltaTime);
        refreshTimer += Time.deltaTime;
        if (refreshTimer >= 0.25f)
        {
            refreshTimer = 0f;
            if (prestigeConfirmUntil > 0f && Time.time > prestigeConfirmUntil)
            {
                prestigeConfirmUntil = -1f;
                Say("Reopen cancelled. Your arcade is unchanged.");
            }
            RefreshDisplay();
        }
    }

    public float IncomePerSecond()
    {
        return (1f + cabinets * 1f + snackBars * 7f + lounges * 35f) * Multiplier();
    }

    private float Multiplier()
    {
        return (1f + boosts * 0.5f) * (1f + stars * 0.25f);
    }

    private void Earn(float amount)
    {
        credits = Mathf.Min(MaxCredits, credits + amount);
        runEarned = Mathf.Min(MaxCredits, runEarned + amount);
    }

    public void Tap()
    {
        // Limit runaway mouse macros; normal VR/desktop taps remain responsive.
        if (Time.time < nextTapTime) return;
        nextTapTime = Time.time + 0.1f;
        float reward = 2f * Multiplier();
        Earn(reward);
        Say("Nice play! +" + Amount(reward) + " credits");
        RefreshDisplay();
    }

    public void BuyCabinet()
    {
        if (cabinets >= 100) { Say("Cabinet collection complete!"); return; }
        if (!Spend(Cost(25f, cabinets))) return;
        cabinets++;
        Say("Arcade cabinet added. More credits every second!");
        RefreshDisplay();
    }

    public void BuySnackBar()
    {
        if (snackBars >= 100) { Say("Snack collection complete!"); return; }
        if (!Spend(Cost(180f, snackBars))) return;
        snackBars++;
        Say("Snack bar added. Keep relaxing while it earns.");
        RefreshDisplay();
    }

    public void BuyLounge()
    {
        if (lounges >= 100) { Say("Lounge collection complete!"); return; }
        if (!Spend(Cost(1200f, lounges))) return;
        lounges++;
        Say("VIP lounge added. Your arcade is growing!");
        RefreshDisplay();
    }

    public void BuyBoost()
    {
        if (boosts >= 40) { Say("All 40 efficiency upgrades unlocked!"); return; }
        if (!Spend(BoostCost())) return;
        boosts++;
        Say("Efficiency upgraded! +50% of base income and tap power.");
        RefreshDisplay();
    }

    public void Prestige()
    {
        if (runEarned < prestigeThreshold)
        {
            Say("Earn " + Amount(prestigeThreshold) + " this run to reopen with a star.");
            return;
        }
        if (prestigeConfirmUntil < Time.time)
        {
            prestigeConfirmUntil = Time.time + 8f;
            Say("REOPEN? Resets credits, businesses and boosts. Keep stars. Press again within 8s to confirm.");
            return;
        }
        stars++;
        credits = startingCredits;
        runEarned = 0f;
        cabinets = 0;
        snackBars = 0;
        lounges = 0;
        boosts = 0;
        prestigeConfirmUntil = -1f;
        Say("Grand reopening! +1 star: each star adds +25% lasting income this visit.");
        RefreshDisplay();
    }

    private bool Spend(float price)
    {
        if (credits < price)
        {
            Say("Need " + Amount(price - credits) + " more credits. Relax or tap PLAY.");
            return false;
        }
        credits -= price;
        prestigeConfirmUntil = -1f;
        return true;
    }

    private float Cost(float initial, int owned)
    {
        return Mathf.Ceil(initial * Mathf.Pow(1.15f, owned));
    }

    private float BoostCost()
    {
        return Mathf.Ceil(250f * Mathf.Pow(1.8f, boosts));
    }

    private string Amount(float value)
    {
        if (value >= 1000000000000f) return (value / 1000000000000f).ToString("F1") + "T";
        if (value >= 1000000000f) return (value / 1000000000f).ToString("F1") + "B";
        if (value >= 1000000f) return (value / 1000000f).ToString("F1") + "M";
        if (value >= 10000f) return (value / 1000f).ToString("F1") + "K";
        return Mathf.Floor(value).ToString("F0");
    }

    private void Say(string message)
    {
        if (feedbackText != null) feedbackText.text = message;
    }

    public void RefreshDisplay()
    {
        if (statusText != null)
            statusText.text = "YOUR ARCADE\n" + Amount(credits) + " CREDITS\n" + IncomePerSecond().ToString("F1") + " / second  |  " + stars + " stars";
        if (detailText != null)
            detailText.text = "CABINET  " + cabinets + " owned  |  " + Amount(Cost(25f, cabinets)) + " credits  |  +1/s base\n"
                + "SNACK BAR  " + snackBars + " owned  |  " + Amount(Cost(180f, snackBars)) + " credits  |  +7/s base\n"
                + "VIP LOUNGE  " + lounges + " owned  |  " + Amount(Cost(1200f, lounges)) + " credits  |  +35/s base\n"
                + "BOOST  " + boosts + "/40  |  " + Amount(BoostCost()) + " credits  |  +50% base\n"
                + "REOPEN  " + Amount(runEarned) + " / " + Amount(prestigeThreshold) + " earned\n"
                + "Personal progress • This visit only • No offline earnings";
    }
}
