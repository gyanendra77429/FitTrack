package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RewardedAdManager {
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val TAG = "RewardedAdManager"

    private var rewardedAd: RewardedAd? = null

    private val _isAdLoaded = MutableStateFlow(false)
    val isAdLoaded: StateFlow<Boolean> = _isAdLoaded.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadRewardedAd(context: Context) {
        if (_isLoading.value || rewardedAd != null) return

        _isLoading.value = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Rewarded ad failed to load: ${adError.message} (code: ${adError.code})")
                    rewardedAd = null
                    _isAdLoaded.value = false
                    _isLoading.value = false
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully.")
                    rewardedAd = ad
                    _isAdLoaded.value = true
                    _isLoading.value = false
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: (amount: Int, type: String) -> Unit,
        onAdNotReady: () -> Unit = {}
    ) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad displayed full screen.")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed by user.")
                    rewardedAd = null
                    _isAdLoaded.value = false
                    // Preload next rewarded ad
                    loadRewardedAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    _isAdLoaded.value = false
                    loadRewardedAd(activity)
                }
            }

            ad.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d(TAG, "User earned reward: $rewardAmount $rewardType")
                activity.runOnUiThread {
                    onUserEarnedReward(rewardAmount, rewardType)
                }
            }
        } else {
            Log.w(TAG, "Rewarded ad not ready yet. Reloading...")
            loadRewardedAd(activity)
            onAdNotReady()
        }
    }
}
