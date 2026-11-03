using UdonSharp;
using UnityEngine;
using UnityEngine.UI;
using VRC.SDKBase;
using VRC.SDK3.Components;
using VRC.SDK3.Components.Video;
using VRC.SDK3.Video.Components;

// One shared video, host-controlled playback, and independent listening volume.
[UdonBehaviourSyncMode(BehaviourSyncMode.Manual)]
public class LoungeVideo : UdonSharpBehaviour
{
    public VRCUnityVideoPlayer videoPlayer;
    public VRCUrlInputField urlInput;
    public Text statusText;
    public AudioSource speaker;
    public GameObject idleOverlay;
    [UdonSynced] private VRCUrl sharedUrl = VRCUrl.Empty;
    [UdonSynced] private bool sharedPlaying;
    [UdonSynced] private float sharedPosition;
    [UdonSynced] private double sharedClock;
    [UdonSynced] private int revision;
    private int loadedRevision = -1;
    private bool ready;
    private float nextLoadTime;
    private float nextSyncTime;
    private bool loadPending;
    private float localVolume = 0.35f;
    private bool muted;
    private string message = "Host: paste a video URL, then LOAD. Everyone can adjust their own volume.";

    private void Start()
    {
        if (videoPlayer != null) { videoPlayer.Loop = false; videoPlayer.Stop(); }
        ShowIdle(true);
        ApplyVolume();
        RefreshStatus();
    }

    private void Update()
    {
        if (loadPending && Time.time >= nextLoadTime) LoadShared();
        if (ready && Time.time >= nextSyncTime)
        {
            nextSyncTime = Time.time + 5f;
            MatchPlayback();
        }
    }

    private bool HostControl()
    {
        if (!Utilities.IsValid(Networking.LocalPlayer)) return false;
        if (!Networking.IsMaster)
        {
            SetMessage("The instance host controls the shared video. Volume and retry are personal.");
            return false;
        }
        if (!Networking.IsOwner(gameObject)) Networking.SetOwner(Networking.LocalPlayer, gameObject);
        return true;
    }

    public void LoadVideo()
    {
        if (!HostControl() || urlInput == null || videoPlayer == null) return;
        VRCUrl candidate = urlInput.GetUrl();
        string url = candidate.Get();
        if (url == null || !(url.StartsWith("https://") || url.StartsWith("http://")))
        {
            SetMessage("Paste a complete https:// video URL. Direct MP4 works in the Unity preview too.");
            return;
        }
        sharedUrl = candidate;
        sharedPosition = 0f;
        sharedClock = Networking.GetServerTimeInSeconds();
        sharedPlaying = true;
        revision++;
        RequestSerialization();
        LoadShared();
    }

    private void LoadShared()
    {
        if (videoPlayer == null) return;
        if (sharedUrl == null || string.IsNullOrEmpty(sharedUrl.Get()))
        {
            loadPending = false;
            loadedRevision = revision;
            ready = false;
            videoPlayer.Stop();
            ShowIdle(true);
            SetMessage("Screen idle. Host: paste a video URL and press LOAD.");
            return;
        }
        if (Time.time < nextLoadTime) { loadPending = true; SetMessage("Waiting briefly before loading the video..."); return; }
        loadPending = false;
        loadedRevision = revision;
        ready = false;
        videoPlayer.Stop();
        ShowIdle(true);
        nextLoadTime = Time.time + 6f;
        SetMessage("Loading shared video...");
        videoPlayer.LoadURL(sharedUrl);
    }

    public override void OnDeserialization()
    {
        if (loadedRevision != revision) LoadShared();
        else if (ready)
        {
            MatchPlayback();
            SetMessage(sharedPlaying ? "Playing together. Volume is personal." : "Shared video paused.");
        }
    }

    public override void OnPlayerJoined(VRCPlayerApi player)
    {
        if (Networking.IsOwner(gameObject)) RequestSerialization();
    }

    public override void OnVideoReady()
    {
        // A previous asynchronous request may finish after the host replaced or stopped it.
        if (loadedRevision != revision || sharedUrl == null || string.IsNullOrEmpty(sharedUrl.Get())) return;
        ready = true;
        ShowIdle(false);
        videoPlayer.SetTime(TargetTime());
        if (sharedPlaying) videoPlayer.Play();
        else videoPlayer.Pause();
        SetMessage(sharedPlaying ? "Playing together. Volume is personal." : "Shared video paused.");
    }

    public override void OnVideoStart()
    {
        if (!sharedPlaying) videoPlayer.Pause();
    }

    private float TargetTime()
    {
        float target = sharedPosition;
        if (sharedPlaying) target += (float)(Networking.GetServerTimeInSeconds() - sharedClock);
        float duration = videoPlayer.GetDuration();
        if (duration > 0f && !float.IsInfinity(duration)) target = Mathf.Min(target, Mathf.Max(0f, duration - 0.1f));
        return Mathf.Max(0f, target);
    }

    private void MatchPlayback()
    {
        if (!ready || videoPlayer == null) return;
        float target = TargetTime();
        if (Mathf.Abs(videoPlayer.GetTime() - target) > 2f) videoPlayer.SetTime(target);
        if (sharedPlaying && !videoPlayer.IsPlaying) videoPlayer.Play();
        if (!sharedPlaying && videoPlayer.IsPlaying) videoPlayer.Pause();
    }

    public void TogglePause()
    {
        if (!HostControl() || !ready) return;
        sharedPosition = videoPlayer.GetTime();
        sharedClock = Networking.GetServerTimeInSeconds();
        sharedPlaying = !sharedPlaying;
        RequestSerialization();
        MatchPlayback();
        SetMessage(sharedPlaying ? "Playing together. Volume is personal." : "Shared video paused.");
    }

    public void StopVideo()
    {
        if (!HostControl()) return;
        sharedPlaying = false;
        sharedUrl = VRCUrl.Empty;
        sharedPosition = 0f;
        revision++;
        RequestSerialization();
        // Stopping needs no URL request, so it bypasses the load cooldown.
        loadPending = false;
        loadedRevision = revision;
        ready = false;
        videoPlayer.Stop();
        ShowIdle(true);
        SetMessage("Screen idle. Host: paste a video URL and press LOAD.");
    }

    public void RetryVideo() { LoadShared(); }

    public override void OnVideoEnd()
    {
        ready = false;
        ShowIdle(true);
        SetMessage("Video finished. Host can load another, or press LOAD to replay.");
        if (Networking.IsOwner(gameObject))
        {
            sharedPlaying = false;
            sharedPosition = videoPlayer.GetTime();
            sharedClock = Networking.GetServerTimeInSeconds();
            RequestSerialization();
        }
    }

    public override void OnVideoError(VideoError error)
    {
        ready = false;
        ShowIdle(true);
        if (error == VideoError.RateLimited)
        {
            nextLoadTime = Time.time + 6f;
            loadPending = true;
            SetMessage("Video request was too soon. Retrying in six seconds...");
        }
        else if (error == VideoError.AccessDenied) SetMessage("This host is blocked by your URL settings. Use a trusted host or review Allow Untrusted URLs.");
        else if (error == VideoError.InvalidURL) SetMessage("That link is not playable. Try a direct H.264 MP4 URL, then LOAD.");
        else SetMessage("Video could not play on this device. Try RETRY or a compatible MP4 link.");
    }

    public void VolumeUp() { localVolume = Mathf.Clamp01(localVolume + 0.1f); muted = false; ApplyVolume(); }
    public void VolumeDown() { localVolume = Mathf.Clamp01(localVolume - 0.1f); ApplyVolume(); }
    public void ToggleMute() { muted = !muted; ApplyVolume(); }
    private void ApplyVolume() { if (speaker != null) speaker.volume = muted ? 0f : localVolume; RefreshStatus(); }
    private void SetMessage(string value) { message = value; RefreshStatus(); }
    private void ShowIdle(bool show) { if (idleOverlay != null) idleOverlay.SetActive(show); }
    private void RefreshStatus()
    {
        if (statusText != null) statusText.text = message + "\nYour volume: " + (muted ? "muted" : Mathf.RoundToInt(localVolume * 100f).ToString() + "%");
    }
}
