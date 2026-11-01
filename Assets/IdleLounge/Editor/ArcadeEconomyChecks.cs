using System;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;
using UdonSharpEditor;

/// <summary>Fast behavioral checks against the real C# UdonSharp proxy, no Play Mode required.</summary>
public static class ArcadeEconomyChecks
{
    [MenuItem("Neon Nook/Check arcade economy")]
    public static void Run()
    {
        var scene = EditorSceneManager.NewPreviewScene();
        try
        {
            var host = new GameObject("Economy checks (temporary)");
            SceneManager.MoveGameObjectToScene(host, scene);
            var game = host.AddUdonSharpComponent<ArcadeEconomy>();
            game.credits = game.startingCredits;
            Equal(game.IncomePerSecond(), 1f, "Starter idle income");

            game.credits = 24f;
            game.BuyCabinet();
            Equal(game.credits, 24f, "Unaffordable purchase keeps wallet");
            Equal(game.cabinets, 0, "Unaffordable purchase keeps inventory");
            Equal(game.IncomePerSecond(), 1f, "Unaffordable purchase keeps income");

            game.credits = 25f;
            game.BuyCabinet();
            Equal(game.credits, 0f, "First cabinet costs exactly 25");
            Equal(game.cabinets, 1, "First cabinet added");
            Equal(game.IncomePerSecond(), 2f, "Cabinet adds one credit per second");
            game.credits = 29f;
            game.BuyCabinet();
            Equal(game.credits, 0f, "Second cabinet uses rounded scaled cost 29");
            Equal(game.cabinets, 2, "Second cabinet added");

            game.credits = 180f;
            game.BuySnackBar();
            Equal(game.credits, 0f, "Snack bar costs 180");
            Equal(game.snackBars, 1, "Snack bar added");
            Equal(game.IncomePerSecond(), 10f, "Snack bar adds seven per second");
            game.credits = 1200f;
            game.BuyLounge();
            Equal(game.credits, 0f, "VIP lounge costs 1200");
            Equal(game.lounges, 1, "VIP lounge added");
            Equal(game.IncomePerSecond(), 45f, "VIP lounge adds 35 per second");

            game.credits = 250f;
            game.BuyBoost();
            Equal(game.credits, 0f, "First boost costs 250");
            Equal(game.IncomePerSecond(), 67.5f, "Boost multiplies all generators");
            game.Tap();
            Equal(game.credits, 3f, "Tap receives boosted reward");
            Equal(game.runEarned, 3f, "Tap counts toward run earnings");
            game.Tap();
            Equal(game.credits, 3f, "Two immediate taps are throttled");

            game.runEarned = game.prestigeThreshold - 1f;
            game.Prestige();
            Equal(game.stars, 0, "Prestige below threshold rejected");
            Equal(game.cabinets, 2, "Rejected prestige keeps generators");
            game.runEarned = game.prestigeThreshold;
            game.Prestige();
            Equal(game.stars, 0, "First prestige press only requests confirmation");
            Equal(game.runEarned, game.prestigeThreshold, "Confirmation does not spend run earnings");
            game.Prestige();
            Equal(game.stars, 1, "Confirmed prestige awards one star");
            Equal(game.credits, game.startingCredits, "Prestige resets wallet to starting amount");
            Equal(game.runEarned, 0f, "Prestige resets run earnings");
            Equal(game.cabinets + game.snackBars + game.lounges + game.boosts, 0, "Prestige resets all businesses and boosts");
            Equal(game.IncomePerSecond(), 1.25f, "Star persists as 25 percent income bonus");

            game.runEarned = game.prestigeThreshold;
            game.Prestige();
            game.BuyCabinet(); // successful spending must invalidate pending destructive confirmation
            game.Prestige();
            Equal(game.stars, 1, "Buying cancels pending prestige confirmation");
            Equal(game.cabinets, 1, "Cancelled confirmation preserves new purchase");

            game.cabinets = 100;
            game.snackBars = 100;
            game.lounges = 100;
            game.boosts = 40;
            game.credits = 1000000000000000f;
            game.BuyCabinet(); game.BuySnackBar(); game.BuyLounge(); game.BuyBoost();
            Equal(game.cabinets, 100, "Cabinet cap");
            Equal(game.snackBars, 100, "Snack cap");
            Equal(game.lounges, 100, "Lounge cap");
            Equal(game.boosts, 40, "Boost cap");
            Equal(game.credits, 1000000000000000f, "Capped purchases do not spend");
            Require(!float.IsInfinity(game.IncomePerSecond()) && !float.IsNaN(game.IncomePerSecond()), "Maximum businesses yield finite income");

            // A new proxy avoids the earlier tap cooldown while checking saturation.
            var cappedHost = new GameObject("Cap check (temporary)");
            SceneManager.MoveGameObjectToScene(cappedHost, scene);
            var capped = cappedHost.AddUdonSharpComponent<ArcadeEconomy>();
            capped.credits = 1000000000000000f;
            capped.runEarned = 1000000000000000f;
            capped.Tap();
            Equal(capped.credits, 1000000000000000f, "Wallet saturates at cap");
            Equal(capped.runEarned, 1000000000000000f, "Run earnings saturate at cap");
            Debug.Log("ARCADE_ECONOMY_CHECKS_PASS: purchases, costs, income, tap throttle, prestige, confirmation cancellation and caps.");
        }
        finally { EditorSceneManager.ClosePreviewScene(scene); }
    }

    private static void Equal(float actual, float expected, string message)
    {
        Require(Mathf.Abs(actual - expected) <= Mathf.Max(0.001f, Mathf.Abs(expected) * 0.000001f), message + ": expected " + expected + ", got " + actual);
    }

    private static void Require(bool condition, string message)
    {
        if (!condition) throw new InvalidOperationException("Arcade economy check failed: " + message);
    }
}
