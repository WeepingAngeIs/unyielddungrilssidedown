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

    [Header("Arcade dashboard")]
    public Text walletValueText;
    public Text incomeValueText;
    public Text starValueText;
    public Text cabinetOwnedText;
    public Text cabinetPriceText;
    public Text cabinetIncomeText;
    public Text snackOwnedText;
    public Text snackPriceText;
    public Text snackIncomeText;
    public Text loungeOwnedText;
    public Text loungePriceText;
    public Text loungeIncomeText;
    public Text boostValueText;
    public Text prestigeValueText;
    public Text tapValueText;
    public Image prestigeProgressFill;
    public Image[] purchaseStateImages;

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
        float multiplier = Multiplier();
        float cabinetCost = Cost(25f, cabinets);
        float snackCost = Cost(180f, snackBars);
        float loungeCost = Cost(1200f, lounges);
        if (walletValueText != null) walletValueText.text = Amount(credits);
        if (incomeValueText != null) incomeValueText.text = IncomePerSecond().ToString("F1") + " / sec";
        if (starValueText != null) starValueText.text = stars.ToString();
        if (cabinetOwnedText != null) cabinetOwnedText.text = cabinets + " OWNED";
        if (cabinetPriceText != null) cabinetPriceText.text = cabinets >= 100 ? "COMPLETE" : Amount(cabinetCost);
        if (cabinetIncomeText != null) cabinetIncomeText.text = "+" + multiplier.ToString("F1") + " / sec";
        if (snackOwnedText != null) snackOwnedText.text = snackBars + " OWNED";
        if (snackPriceText != null) snackPriceText.text = snackBars >= 100 ? "COMPLETE" : Amount(snackCost);
        if (snackIncomeText != null) snackIncomeText.text = "+" + (7f * multiplier).ToString("F1") + " / sec";
        if (loungeOwnedText != null) loungeOwnedText.text = lounges + " OWNED";
        if (loungePriceText != null) loungePriceText.text = lounges >= 100 ? "COMPLETE" : Amount(loungeCost);
        if (loungeIncomeText != null) loungeIncomeText.text = "+" + (35f * multiplier).ToString("F1") + " / sec";
        if (tapValueText != null) tapValueText.text = "+" + Amount(2f * multiplier) + " credits";
        if (boostValueText != null) boostValueText.text = boosts >= 40 ? "MAX LEVEL" : Amount(BoostCost()) + "  /  +50%";
        if (prestigeValueText != null)
            prestigeValueText.text = prestigeConfirmUntil > Time.time ? "CONFIRM REOPEN" : Amount(runEarned) + " / " + Amount(prestigeThreshold);
        if (prestigeProgressFill != null)
            prestigeProgressFill.fillAmount = Mathf.Clamp01(runEarned / Mathf.Max(1f, prestigeThreshold));
        if (purchaseStateImages != null && purchaseStateImages.Length >= 3)
        {
            Color unavailable = new Color(.48f, .54f, .60f, 1f);
            if (purchaseStateImages[0] != null) purchaseStateImages[0].color = credits >= cabinetCost && cabinets < 100 ? new Color(.439f, .941f, .796f) : unavailable;
            if (purchaseStateImages[1] != null) purchaseStateImages[1].color = credits >= snackCost && snackBars < 100 ? new Color(1f, .792f, .545f) : unavailable;
            if (purchaseStateImages[2] != null) purchaseStateImages[2].color = credits >= loungeCost && lounges < 100 ? new Color(.780f, .710f, 1f) : unavailable;
        }
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
