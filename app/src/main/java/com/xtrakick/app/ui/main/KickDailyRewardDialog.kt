package com.xtrakick.app.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.xtrakick.app.BuildConfig
import com.xtrakick.app.R
import com.xtrakick.app.databinding.DialogKickDailyRewardBinding
import com.xtrakick.app.databinding.ItemRewardCardPageBinding
import com.xtrakick.app.databinding.ItemRewardOddsPillBinding
import com.xtrakick.app.databinding.ItemRouletteReelSlotBinding
import com.xtrakick.app.model.kick.KickChallengeDropTableItem
import com.xtrakick.app.model.kick.KickDailyChallenge
import com.xtrakick.app.model.kick.KickDailyClaimResult
import com.xtrakick.app.model.kick.KickDailyRewardWinner
import com.xtrakick.app.model.kick.KickDailyRouletteItem
import com.xtrakick.app.repository.KickRewardsPolicy
import com.xtrakick.app.repository.KickRewardsRepository
import com.xtrakick.app.util.AppConstants
import com.xtrakick.app.util.prefs
import com.xtrakick.app.util.tokenPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class KickDailyRewardDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var rewardsRepository: KickRewardsRepository

    private var _binding: DialogKickDailyRewardBinding? = null
    private val binding get() = _binding!!
    private var challenges: List<KickDailyChallenge> = emptyList()
    private var selectedChallengeId: String? = null
    private val selectedChallenge: KickDailyChallenge?
        get() = challenges.firstOrNull { it.id == selectedChallengeId } ?: challenges.firstOrNull()
    private var lastKnownStreak: Long? = null
    private var shareCardUrl: String? = null
    private var pollJob: Job? = null
    private var reelAnimator: ValueAnimator? = null
    private var isClaiming = false
    private val cardAdapter = ChallengeCardAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogKickDailyRewardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        binding.closeButton.setOnClickListener { dismiss() }
        binding.claimButton.setOnClickListener { claimReward() }
        binding.shareButton.setOnClickListener { shareReward() }
        if (BuildConfig.DEBUG) {
            binding.claimButton.setOnLongClickListener {
                selectedChallenge?.let { ch -> simulateClaim(ch) }
                true
            }
        }
        setupCardViewPager()
    }

    private fun setupCardViewPager() {
        val pager = binding.cardViewPager
        pager.adapter = cardAdapter
        pager.offscreenPageLimit = 1
        pager.clipChildren = false
        pager.clipToPadding = false
        (pager.getChildAt(0) as? RecyclerView)?.apply {
            clipChildren = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val pageMarginPx = (8 * resources.displayMetrics.density).toInt()
        val peekMarginPx = (20 * resources.displayMetrics.density).toInt()
        pager.setPadding(peekMarginPx, 0, peekMarginPx, 0)
        pager.setPageTransformer(MarginPageTransformer(pageMarginPx))

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position in challenges.indices && !isClaiming) {
                    val newChallenge = challenges[position]
                    if (selectedChallengeId != newChallenge.id) {
                        selectedChallengeId = newChallenge.id
                        updateChallengeChips(challenges)
                        renderChallengeDetails(newChallenge, lastKnownStreak)
                    }
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        startRewardsPolling()
    }

    override fun onStop() {
        stopRewardsPolling()
        super.onStop()
    }

    override fun onDestroyView() {
        stopRewardsPolling()
        reelAnimator?.cancel()
        reelAnimator = null
        _binding = null
        super.onDestroyView()
    }

    private fun startRewardsPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            loadReward(isPeriodicRefresh = false)
            while (isActive) {
                delay(KickRewardsRepository.REWARDS_POLL_INTERVAL_MS)
                if (!isClaiming && isAdded && _binding != null) {
                    loadReward(isPeriodicRefresh = true)
                }
            }
        }
    }

    private fun stopRewardsPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun loadReward(isPeriodicRefresh: Boolean = false) {
        if (!isPeriodicRefresh) {
            showLoading()
        }
        if (!rewardsRepository.canAttemptClaims()) {
            if (!isPeriodicRefresh && isAdded && _binding != null) {
                showLoadError(R.string.daily_reward_login_required)
            }
            return
        }
        val fetchedChallenges = try {
            rewardsRepository.getDailyChallenges()
        } catch (e: Exception) {
            com.xtrakick.app.util.DiagnosticLogger.w("KickRewards", "getDailyChallenges failed: ${e.message}", e)
            if (!isPeriodicRefresh && isAdded && _binding != null) {
                showLoadError(R.string.daily_reward_load_failed)
            }
            return
        }
        if (!isAdded || _binding == null) return
        val currentContext = context ?: return
        val userId = currentContext.tokenPrefs()
            .getString(AppConstants.KICK_USER_ID, null)
            ?.takeIf { it.isNotBlank() }
        val streak = userId?.let { id ->
            runCatching { rewardsRepository.getDailyStreak(id) }.getOrNull()
        }
        lastKnownStreak = streak

        val sorted = KickRewardsPolicy.sortChallenges(fetchedChallenges)
        challenges = sorted
        if (sorted.none { it.id == selectedChallengeId }) {
            selectedChallengeId = sorted.firstOrNull()?.id
        }

        // Preload unboxing animations for event crates so claiming transitions seamlessly
        sorted.forEach { ch ->
            safeRewardImageUrl(ch.unboxAnimationUrl)?.let { url ->
                Glide.with(this).load(buildKickGlideUrl(url)).preload()
            }
        }

        cardAdapter.notifyDataSetChanged()

        val peekMarginPx = if (sorted.size > 1) (20 * resources.displayMetrics.density).toInt() else 0
        binding.cardViewPager.setPadding(peekMarginPx, 0, peekMarginPx, 0)

        val selectedIndex = sorted.indexOfFirst { it.id == selectedChallengeId }.coerceAtLeast(0)
        if (binding.cardViewPager.currentItem != selectedIndex) {
            binding.cardViewPager.setCurrentItem(selectedIndex, false)
        }

        updateChallengeChips(sorted)
        renderChallengeDetails(selectedChallenge, streak)
    }

    private fun showLoading() {
        val currentBinding = _binding ?: return
        currentBinding.loadingIndicator.isVisible = true
        currentBinding.contentGroup.isVisible = false
    }

    private fun showLoadError(messageRes: Int) {
        val currentBinding = _binding ?: return
        currentBinding.loadingIndicator.isVisible = false
        currentBinding.contentGroup.isVisible = true
        currentBinding.challengeTabsScroll.isVisible = false
        currentBinding.activePeriodContainer.isVisible = false
        currentBinding.streakContainer.isVisible = false
        currentBinding.watchProgress.isVisible = false
        currentBinding.progressText.isVisible = false
        currentBinding.cardViewPager.isVisible = false
        currentBinding.oddsScroll.isVisible = false
        currentBinding.winnerRarityText.isVisible = false
        currentBinding.shareButton.isVisible = false
        currentBinding.resetText.isVisible = false
        currentBinding.statusText.setText(messageRes)
        currentBinding.claimButton.apply {
            isVisible = true
            isEnabled = true
            backgroundTintList = ColorStateList.valueOf(COLOR_KICK_GREEN)
            setTextColor(Color.BLACK)
            iconTint = ColorStateList.valueOf(Color.BLACK)
            if (messageRes == R.string.daily_reward_login_required) {
                setText(R.string.log_in)
                setOnClickListener {
                    dismiss()
                    startActivity(Intent(requireContext(), com.xtrakick.app.ui.login.LoginActivity::class.java))
                }
            } else {
                setText(R.string.retry)
                setOnClickListener { lifecycleScope.launch { loadReward() } }
            }
        }
    }

    private fun challengeTitle(challenge: KickDailyChallenge): String =
        challenge.name?.takeIf { it.isNotBlank() }
            ?: if (challenge.recurrence.equals("daily", ignoreCase = true)) {
                getString(R.string.daily_reward_claim_title)
            } else {
                challenge.recurrence?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
                    ?: getString(R.string.daily_reward_claim_title)
            }

    private fun updateChallengeChips(challengeList: List<KickDailyChallenge>) {
        val currentBinding = _binding ?: return
        if (challengeList.size <= 1) {
            currentBinding.challengeTabsScroll.isVisible = false
            return
        }
        currentBinding.challengeTabsScroll.isVisible = true
        val chipGroup = currentBinding.challengeChips

        val sameChips = chipGroup.childCount == challengeList.size && (0 until chipGroup.childCount).all { i ->
            (chipGroup.getChildAt(i) as? Chip)?.tag == challengeList[i].id
        }

        if (sameChips) {
            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as? Chip ?: continue
                val ch = challengeList[i]
                val isChecked = (ch.id == selectedChallengeId)
                chip.isChecked = isChecked
                updateChipIcon(chip, ch)
                if (isChecked) {
                    currentBinding.challengeTabsScroll.post {
                        currentBinding.challengeTabsScroll.smoothScrollTo((chip.left - 40).coerceAtLeast(0), 0)
                    }
                }
            }
            return
        }

        chipGroup.removeAllViews()
        val currentContext = context ?: return
        for (i in challengeList.indices) {
            val ch = challengeList[i]
            val chip = Chip(currentContext).apply {
                tag = ch.id
                text = challengeTitle(ch)
                isCheckable = true
                isChecked = (ch.id == selectedChallengeId)
                updateChipIcon(this, ch)
                setOnClickListener {
                    if (selectedChallengeId != ch.id) {
                        selectedChallengeId = ch.id
                        binding.cardViewPager.setCurrentItem(i, true)
                        renderChallengeDetails(ch, lastKnownStreak)
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateChipIcon(chip: Chip, ch: KickDailyChallenge) {
        val isClaimable = ch.status.equals(KickRewardsPolicy.STATUS_CLAIMABLE, ignoreCase = true)
        chip.isChipIconVisible = isClaimable
        if (isClaimable && chip.chipIcon == null) {
            chip.chipIcon = ContextCompat.getDrawable(chip.context, R.drawable.baseline_card_giftcard_claimable_24)
        }
    }

    private fun renderChallengeDetails(dailyChallenge: KickDailyChallenge?, streak: Long?) {
        val currentBinding = _binding ?: return
        if (!isAdded) return
        currentBinding.loadingIndicator.isVisible = false
        currentBinding.contentGroup.isVisible = true
        currentBinding.cardViewPager.isVisible = true
        currentBinding.shareButton.isVisible = false
        currentBinding.winnerRarityText.isVisible = false
        currentBinding.resetText.isVisible = false

        currentBinding.titleText.text = dailyChallenge?.let { challengeTitle(it) }
            ?: getString(R.string.daily_reward_claim_title)

        // Server description
        val desc = dailyChallenge?.description?.takeIf { it.isNotBlank() }
        currentBinding.descriptionText.text = desc ?: getString(R.string.daily_reward_description)

        // Event active period
        val activeEndsAt = dailyChallenge?.activePeriodEndsAtEpochMs
        if (activeEndsAt != null) {
            val formatted = SimpleDateFormat("dd MMM, HH:mm z", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(activeEndsAt))
            currentBinding.activePeriodText.text = getString(R.string.event_reward_ends, formatted)
            currentBinding.activePeriodContainer.isVisible = true
        } else {
            currentBinding.activePeriodContainer.isVisible = false
        }

        // Drop table odds pills
        populateDropTablePills(dailyChallenge?.dropTable.orEmpty())

        // Streak (only shown for regular daily challenge, not featured/seasonal)
        val isDailyRewardTab = dailyChallenge?.featured != true
        val showStreak = isDailyRewardTab && (streak != null && streak > 0)
        currentBinding.streakContainer.isVisible = showStreak
        if (showStreak) {
            val days = streak.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            currentBinding.streakText.text = resources.getQuantityString(R.plurals.daily_reward_streak, days, days)
        }

        // Watch progress
        val threshold = dailyChallenge?.thresholdMinutes
        val progress = dailyChallenge?.progressMinutes
        val hasProgress = threshold != null && threshold > 0 && progress != null
        currentBinding.watchProgress.isVisible = hasProgress
        currentBinding.progressText.isVisible = hasProgress
        if (hasProgress) {
            val max = threshold.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val current = progress.coerceIn(0, threshold).toInt()
            currentBinding.watchProgress.max = max
            currentBinding.watchProgress.setProgressCompat(current, true)
            currentBinding.progressText.text = getString(R.string.daily_reward_watch_progress, progress, threshold)
        }
        renderResetTime(dailyChallenge)

        val action = dailyChallenge?.let(KickRewardsPolicy::decide)
        when (action) {
            KickRewardsPolicy.DailyAction.CLAIM_NOW -> {
                currentBinding.statusText.setText(R.string.daily_reward_ready)
                currentBinding.claimButton.apply {
                    isVisible = true
                    isEnabled = true
                    setText(R.string.daily_reward_claim)
                    backgroundTintList = ColorStateList.valueOf(COLOR_KICK_GREEN)
                    setTextColor(Color.BLACK)
                    iconTint = ColorStateList.valueOf(Color.BLACK)
                    setOnClickListener { claimReward() }
                }
            }
            KickRewardsPolicy.DailyAction.WATCH_IN_PROGRESS -> {
                val remaining = dailyChallenge.remainingMinutes
                currentBinding.statusText.text = remaining?.let {
                    getString(R.string.daily_reward_watch_remaining, it)
                } ?: getString(R.string.daily_reward_unavailable)
                currentBinding.claimButton.apply {
                    isVisible = true
                    isEnabled = false
                    setText(R.string.daily_reward_claim)
                    backgroundTintList = ColorStateList.valueOf(COLOR_KICK_GREEN_DISABLED)
                    setTextColor(Color.BLACK)
                    iconTint = ColorStateList.valueOf(Color.BLACK)
                }
            }
            KickRewardsPolicy.DailyAction.ALREADY_CLAIMED -> {
                currentBinding.statusText.setText(R.string.daily_reward_already_claimed)
                currentBinding.claimButton.isVisible = false
                showKnownWinnerDetails(dailyChallenge)
            }
            else -> {
                currentBinding.statusText.setText(R.string.daily_reward_unavailable)
                currentBinding.claimButton.isVisible = false
            }
        }
    }

    private fun populateDropTablePills(dropTable: List<KickChallengeDropTableItem>) {
        val currentBinding = _binding ?: return
        currentBinding.oddsContainer.removeAllViews()
        if (dropTable.isEmpty()) {
            currentBinding.oddsScroll.isVisible = false
            return
        }
        currentBinding.oddsScroll.isVisible = true
        val inflater = layoutInflater
        for (item in dropTable) {
            val pillBinding = ItemRewardOddsPillBinding.inflate(inflater, currentBinding.oddsContainer, false)
            val titleRarity = item.rarity.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
            pillBinding.oddsLabel.text = "$titleRarity ${item.percentageFormatted}"
            val color = rarityColor(item.rarity)
            pillBinding.rarityDot.background?.mutate()?.setTint(color)
            currentBinding.oddsContainer.addView(pillBinding.root)
        }
    }

    private fun rarityColor(rarity: String): Int = when (rarity.lowercase(Locale.ROOT)) {
        "mythic" -> 0xFFEC4899.toInt()     // Pink / Magenta
        "legendary" -> 0xFFF59E0B.toInt()  // Amber / Gold
        "epic" -> 0xFFA855F7.toInt()       // Purple
        "rare" -> 0xFF38BDF8.toInt()       // Sky Blue
        "uncommon" -> 0xFF53FC18.toInt()   // Kick Green
        else -> 0xFF9E9E9E.toInt()         // Common / Grey
    }

    private fun showKnownWinnerDetails(dailyChallenge: KickDailyChallenge?) {
        if (dailyChallenge == null) return
        val currentBinding = _binding ?: return
        currentBinding.streakContainer.isVisible = false
        currentBinding.watchProgress.isVisible = false
        currentBinding.progressText.isVisible = false
        val winner = dailyChallenge.winnerName
        val rarity = dailyChallenge.winnerRarity
        val label = listOfNotNull(winner, rarity?.rarityLabel()).joinToString(" - ")
        currentBinding.winnerRarityText.isVisible = label.isNotBlank()
        currentBinding.winnerRarityText.text = label

        val isStandardDaily = dailyChallenge.recurrence.equals("daily", ignoreCase = true) && !dailyChallenge.featured
        val prefKey = if (isStandardDaily) AppConstants.REWARD_LAST_DAILY_CARD_URL else "${AppConstants.REWARD_LAST_DAILY_CARD_URL}_${dailyChallenge.id}"
        val savedCardUrl = context?.prefs()?.getString(prefKey, null)
        shareCardUrl = safeRewardImageUrl(dailyChallenge.winnerCardUrl)
            ?: safeRewardImageUrl(savedCardUrl)
        currentBinding.shareButton.isVisible = shareCardUrl != null && hasShareUsername()
    }

    private fun renderResetTime(dailyChallenge: KickDailyChallenge?) {
        val currentBinding = _binding ?: return
        val resetAt = dailyChallenge?.endsAtEpochMs ?: return
        val formatted = SimpleDateFormat("MMM d, hh:mm a", Locale.US).format(Date(resetAt))
        val isStandardDaily = dailyChallenge.recurrence.equals("daily", ignoreCase = true) && !dailyChallenge.featured
        currentBinding.resetText.text = if (isStandardDaily) {
            getString(R.string.daily_reward_reset, formatted)
        } else if (dailyChallenge.featured) {
            getString(R.string.seasonal_crate_reset, formatted)
        } else {
            getString(R.string.event_reward_ends, formatted)
        }
        currentBinding.resetText.isVisible = true
    }

    private fun claimReward() {
        val activeChallenge = selectedChallenge ?: return
        val currentBinding = _binding ?: return
        isClaiming = true
        currentBinding.cardViewPager.isUserInputEnabled = false
        currentBinding.claimButton.isEnabled = false
        currentBinding.statusText.setText(R.string.daily_reward_claiming)
        currentBinding.loadingIndicator.isVisible = true
        lifecycleScope.launch {
            val result = try {
                rewardsRepository.claimDailyChallenge(activeChallenge.id)
            } catch (e: Exception) {
                com.xtrakick.app.util.DiagnosticLogger.w("KickRewards", "claimDailyChallenge failed: ${e.message}", e)
                isClaiming = false
                _binding?.let { b ->
                    b.loadingIndicator.isVisible = false
                    b.statusText.setText(R.string.daily_reward_claim_failed)
                    b.claimButton.isEnabled = true
                    b.cardViewPager.isUserInputEnabled = true
                }
                return@launch
            }
            if (!isAdded || _binding == null) {
                isClaiming = false
                return@launch
            }
            val currentContext = context ?: return@launch
            recordClaim(currentContext, activeChallenge, result)
            animateClaimSequence(activeChallenge, result, isSimulated = false)
        }
    }

    private fun simulateClaim(challenge: KickDailyChallenge) {
        if (!BuildConfig.DEBUG || isClaiming) return
        val currentBinding = _binding ?: return
        isClaiming = true
        currentBinding.cardViewPager.isUserInputEnabled = false
        currentBinding.claimButton.isEnabled = false
        currentBinding.statusText.setText(R.string.daily_reward_claiming)
        currentBinding.loadingIndicator.isVisible = true

        lifecycleScope.launch {
            val winnerRarity = challenge.winnerRarity ?: challenge.dropTable.firstOrNull()?.rarity ?: "common"
            val winnerUrl = challenge.winnerCardUrl
                ?: "https://files.kick.com/emotes/37217/fullsize"
            val mockWinner = KickDailyRewardWinner(
                id = "debug-winner-1",
                cardUrl = winnerUrl,
                rarity = winnerRarity,
            )

            // Rich set of roulette items for the gambling reel spin
            val mockRoulette = listOf(
                KickDailyRouletteItem("item-1", "https://files.kick.com/emotes/37217/fullsize"),
                KickDailyRouletteItem("item-2", "https://files.kick.com/emotes/37218/fullsize"),
                KickDailyRouletteItem("item-3", "https://files.kick.com/emotes/37219/fullsize"),
                KickDailyRouletteItem("item-4", "https://files.kick.com/emotes/37220/fullsize"),
                KickDailyRouletteItem("item-5", "https://files.kick.com/emotes/29015/fullsize"),
            )

            val mockResult = KickDailyClaimResult(
                challengeId = challenge.id,
                roulette = mockRoulette,
                winner = mockWinner,
            )

            context?.let { ctx ->
                Toast.makeText(ctx, "[Debug] Simulating claim for ${challengeTitle(challenge)}...", Toast.LENGTH_SHORT).show()
            }

            animateClaimSequence(challenge, mockResult, isSimulated = true)
        }
    }

    private suspend fun animateClaimSequence(
        activeChallenge: KickDailyChallenge,
        result: KickDailyClaimResult,
        isSimulated: Boolean = false,
    ) = coroutineScope {
        val currentBinding = _binding ?: return@coroutineScope
        currentBinding.streakContainer.isVisible = false
        currentBinding.watchProgress.isVisible = false
        currentBinding.progressText.isVisible = false
        currentBinding.statusText.setText(R.string.daily_reward_claiming)

        val activeHolder = getActiveCardViewHolder()
        val activeImageView = activeHolder?.itemBinding?.rewardImage
        val rouletteContainer = activeHolder?.itemBinding?.rouletteContainer
        val rouletteStrip = activeHolder?.itemBinding?.rouletteStrip

        val unboxUrl = safeRewardImageUrl(activeChallenge.unboxAnimationUrl)
        val rouletteUrls = result.roulette.mapNotNull { safeRewardImageUrl(it.itemUrl) }
        val effectiveRouletteUrls = if (rouletteUrls.isNotEmpty()) {
            rouletteUrls
        } else {
            // Fallback reel items so every crate claim has the exciting gambling spin
            listOf(
                "https://files.kick.com/emotes/37217/fullsize",
                "https://files.kick.com/emotes/37218/fullsize",
                "https://files.kick.com/emotes/37219/fullsize",
                "https://files.kick.com/emotes/37220/fullsize",
                "https://files.kick.com/emotes/29015/fullsize",
            )
        }

        val winner = result.winner
        val winnerUrl = safeRewardImageUrl(winner?.cardUrl) ?: effectiveRouletteUrls.first()
        shareCardUrl = safeRewardImageUrl(winner?.cardUrl)

        // Preload winner & roulette reel items into Glide memory cache in background
        val appContext = context?.applicationContext
        val allReelUrls = (effectiveRouletteUrls + winnerUrl).distinct()
        val preloadedDeferred = async(Dispatchers.IO) {
            if (appContext != null) {
                allReelUrls.forEach { url ->
                    runCatching {
                        Glide.with(appContext)
                            .load(buildKickGlideUrl(url))
                            .submit(240, 240)
                    }
                }
            }
        }

        // 1. Play crate opening / unboxing animation (if crate has an unbox animation)
        if (unboxUrl != null && activeImageView != null && isAdded) {
            currentBinding.loadingIndicator.isVisible = false
            withContext(Dispatchers.Main) {
                Glide.with(this@KickDailyRewardDialog)
                    .load(buildKickGlideUrl(unboxUrl))
                    .dontTransform()
                    .into(activeImageView)
            }
            delay(1200L) // Wait for crate lid to pop open
        }

        if (!isAdded || _binding == null) {
            isClaiming = false
            return@coroutineScope
        }

        currentBinding.loadingIndicator.isVisible = false
        preloadedDeferred.await()

        // 2. The Horizontal Roulette Reel (CS:GO / Kick official app style)
        if (rouletteContainer != null && rouletteStrip != null && isAdded) {
            val totalSlots = 24
            val winnerSlotIndex = 19
            val density = resources.displayMetrics.density
            val slotWidthPx = 108f * density
            val inflater = LayoutInflater.from(context)

            rouletteStrip.removeAllViews()
            val slotCards = mutableListOf<MaterialCardView>()

            for (i in 0 until totalSlots) {
                val slotBinding = ItemRouletteReelSlotBinding.inflate(inflater, rouletteStrip, false)
                val isWinnerSlot = (i == winnerSlotIndex)
                val itemUrl = if (isWinnerSlot) winnerUrl else effectiveRouletteUrls[i % effectiveRouletteUrls.size]

                Glide.with(this@KickDailyRewardDialog)
                    .load(buildKickGlideUrl(itemUrl))
                    .fallback(R.drawable.ic_kick_gem)
                    .error(R.drawable.ic_kick_gem)
                    .dontTransform()
                    .into(slotBinding.slotImage)

                rouletteStrip.addView(slotBinding.root)
                slotCards.add(slotBinding.slotCard)
            }

            // Cross-fade crate to roulette container
            rouletteContainer.alpha = 0f
            rouletteContainer.isVisible = true
            rouletteContainer.animate().alpha(1f).setDuration(200L).start()
            activeImageView?.animate()?.alpha(0f)?.setDuration(200L)?.start()

            val containerWidth = if (rouletteContainer.width > 0) {
                rouletteContainer.width.toFloat()
            } else {
                resources.displayMetrics.widthPixels.toFloat()
            }
            val centerX = containerWidth / 2f

            val startTranslationX = centerX - (slotWidthPx / 2f) + (slotWidthPx * 1.5f)
            val targetTranslationX = centerX - (winnerSlotIndex * slotWidthPx + (slotWidthPx / 2f))

            val greenStrokeCsl = ColorStateList.valueOf(COLOR_KICK_GREEN)
            val defaultStrokeCsl = ColorStateList.valueOf(0xFF2E2E33.toInt())

            suspendCancellableCoroutine<Unit> { continuation ->
                val animator = ValueAnimator.ofFloat(startTranslationX, targetTranslationX).apply {
                    duration = 3200L
                    interpolator = DecelerateInterpolator(2.2f)
                    addUpdateListener { va ->
                        if (!isAdded || _binding == null) {
                            va.cancel()
                            return@addUpdateListener
                        }
                        val animX = va.animatedValue as Float
                        rouletteStrip.translationX = animX

                        for (i in 0 until slotCards.size) {
                            val child = slotCards[i]
                            val childCenter = child.left + animX + (child.width / 2f)
                            val dist = Math.abs(childCenter - centerX)
                            if (dist < slotWidthPx) {
                                val fraction = 1f - (dist / slotWidthPx)
                                val scale = 1f + (0.28f * fraction)
                                child.scaleX = scale
                                child.scaleY = scale
                                child.elevation = fraction * 12f
                                child.setStrokeColor(if (fraction > 0.65f) greenStrokeCsl else defaultStrokeCsl)
                            } else {
                                child.scaleX = 1f
                                child.scaleY = 1f
                                child.elevation = 0f
                                child.setStrokeColor(defaultStrokeCsl)
                            }
                        }
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onAnimationCancel(animation: Animator) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    })
                }
                reelAnimator = animator
                animator.start()
            }

            if (!isAdded || _binding == null) {
                isClaiming = false
                return@coroutineScope
            }

            // 3. Winner Pulse Pop on the landed center card
            val winnerCard = slotCards.getOrNull(winnerSlotIndex)
            slotCards.forEachIndexed { idx, card ->
                if (idx != winnerSlotIndex) {
                    card.animate().alpha(0.12f).setDuration(250L).start()
                }
            }
            if (winnerCard != null) {
                winnerCard.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(120L)
                    .withEndAction {
                        winnerCard.animate()
                            .scaleX(1.4f)
                            .scaleY(1.4f)
                            .setDuration(220L)
                            .setInterpolator(OvershootInterpolator(2.0f))
                            .start()
                    }
                    .start()
            }
            delay(500L)

            // Smooth cross-fade back to rewardImage showing the winner details card
            rouletteContainer.animate().alpha(0f).setDuration(250L).withEndAction {
                rouletteContainer.isVisible = false
            }.start()
        }

        if (!isAdded || _binding == null) {
            isClaiming = false
            return@coroutineScope
        }

        // 4. Reveal final winner card and details
        if (activeImageView != null) {
            activeImageView.alpha = 1f
            showRewardImage(activeImageView, winnerUrl, isWinner = true)
        }

        val rarity = winner?.rarity?.rarityLabel()
        _binding?.let { b ->
            b.winnerRarityText.isVisible = rarity != null
            b.winnerRarityText.text = rarity?.let { getString(R.string.daily_reward_rarity, it) }
            b.statusText.setText(R.string.daily_reward_claimed)
            b.claimButton.isVisible = false
            b.shareButton.isVisible = shareCardUrl != null && hasShareUsername()
            b.cardViewPager.isUserInputEnabled = true
        }
        isClaiming = false
        if (!isSimulated && isAdded && _binding != null) {
            lifecycleScope.launch {
                loadReward(isPeriodicRefresh = true)
            }
        }
    }

    private fun getActiveCardViewHolder(): ChallengeCardAdapter.CardViewHolder? {
        val pager = _binding?.cardViewPager ?: return null
        val recyclerView = pager.getChildAt(0) as? RecyclerView ?: return null
        return recyclerView.findViewHolderForAdapterPosition(pager.currentItem) as? ChallengeCardAdapter.CardViewHolder
    }

    private fun getActiveCardImageView(): ImageView? {
        return getActiveCardViewHolder()?.itemBinding?.rewardImage
    }

    private fun showRewardImage(imageView: ImageView, url: String, isWinner: Boolean) {
        if (!isAdded) return
        imageView.animate().cancel()
        imageView.translationX = 0f
        val request = Glide.with(this)
            .load(buildKickGlideUrl(url))
            .fallback(R.drawable.ic_kick_gem)
            .error(R.drawable.ic_kick_gem)
            .dontTransform()

        if (isWinner) {
            imageView.alpha = 0f
            imageView.scaleX = 0.85f
            imageView.scaleY = 0.85f
            request.listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (isAdded) {
                        imageView.alpha = 1f
                        imageView.scaleX = 1f
                        imageView.scaleY = 1f
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (isAdded) {
                        imageView.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(340L)
                            .setInterpolator(OvershootInterpolator(1.2f))
                            .start()
                    }
                    return false
                }
            }).into(imageView)
        } else {
            request.into(imageView)
        }
    }

    private fun buildKickGlideUrl(url: String): GlideUrl {
        return GlideUrl(url) {
            if (url.contains(".kick.com", ignoreCase = true)) {
                KICK_IMAGE_HEADERS
            } else {
                BASE_IMAGE_HEADERS
            }
        }
    }

    private fun recordClaim(context: Context, activeChallenge: KickDailyChallenge, result: KickDailyClaimResult) {
        val now = System.currentTimeMillis()
        val isStandardDaily = activeChallenge.recurrence.equals("daily", ignoreCase = true) && !activeChallenge.featured
        val rarityKey = if (isStandardDaily) AppConstants.REWARD_LAST_DAILY_RARITY else "${AppConstants.REWARD_LAST_DAILY_RARITY}_${activeChallenge.id}"
        val cardKey = if (isStandardDaily) AppConstants.REWARD_LAST_DAILY_CARD_URL else "${AppConstants.REWARD_LAST_DAILY_CARD_URL}_${activeChallenge.id}"

        context.prefs().edit {
            putLong(AppConstants.REWARD_LAST_CLAIM_AT, now)
            result.winner?.rarity?.let { putString(rarityKey, it) }
            result.winner?.cardUrl?.let { putString(cardKey, it) }
            if (isStandardDaily) {
                val resetAt = KickRewardsPolicy.resetTimeMs(activeChallenge, now)
                putLong(AppConstants.REWARD_DAILY_CLAIMED_UNTIL, resetAt)
                remove(AppConstants.REWARD_DAILY_BACKOFF_UNTIL)
            }
        }
        if (isStandardDaily && context.prefs().getBoolean(AppConstants.REWARD_AUTO_CLAIM_ENABLED, false)) {
            val resetAt = KickRewardsPolicy.resetTimeMs(activeChallenge, now)
            RewardClaimScheduler.followUp(context, resetAt - now)
        }
    }

    private fun shareReward() {
        val cardUrl = shareCardUrl ?: return
        val currentContext = context ?: return
        val username = shareUsername(currentContext) ?: return
        val shareUrl = Uri.Builder()
            .scheme("https")
            .authority("kick.com")
            .appendPath("rewards")
            .appendPath("share")
            .appendQueryParameter("img", cardUrl)
            .appendQueryParameter("u", username)
            .build()
            .toString()
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                    },
                    getString(R.string.share),
                ),
            )
        }
    }

    private fun hasShareUsername(): Boolean = context?.let { shareUsername(it) } != null

    private fun shareUsername(context: Context): String? = context.tokenPrefs()
        .getString(AppConstants.KICK_USER_LOGIN, null)
        ?.takeIf { it.isNotBlank() }
        ?: context.tokenPrefs().getString(AppConstants.USERNAME, null)?.takeIf { it.isNotBlank() }

    private fun safeRewardImageUrl(value: String?): String? {
        val uri = value?.let(Uri::parse) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return value.takeIf {
            uri.scheme.equals("https", ignoreCase = true) &&
                (host == "kick.com" || host.endsWith(".kick.com"))
        }
    }

    private fun String.rarityLabel(): String = replaceFirstChar { first ->
        if (first.isLowerCase()) first.titlecase(Locale.getDefault()) else first.toString()
    }

    inner class ChallengeCardAdapter : RecyclerView.Adapter<ChallengeCardAdapter.CardViewHolder>() {

        inner class CardViewHolder(val itemBinding: ItemRewardCardPageBinding) :
            RecyclerView.ViewHolder(itemBinding.root) {

            fun bind(ch: KickDailyChallenge) {
                itemBinding.rouletteContainer.isVisible = false
                itemBinding.rouletteContainer.alpha = 1f
                itemBinding.rouletteStrip.removeAllViews()
                itemBinding.rewardImage.alpha = 1f
                itemBinding.rewardImage.scaleX = 1f
                itemBinding.rewardImage.scaleY = 1f

                val isClaimed = ch.status.equals(KickRewardsPolicy.STATUS_CLAIMED, ignoreCase = true)
                val isStandardDaily = ch.recurrence.equals("daily", ignoreCase = true) && !ch.featured
                val prefKey = if (isStandardDaily) AppConstants.REWARD_LAST_DAILY_CARD_URL else "${AppConstants.REWARD_LAST_DAILY_CARD_URL}_${ch.id}"
                val savedCardUrl = context?.prefs()?.getString(prefKey, null)

                val imageUrl = if (isClaimed) {
                    safeRewardImageUrl(ch.winnerCardUrl) ?: safeRewardImageUrl(savedCardUrl)
                } else {
                    safeRewardImageUrl(ch.idleAnimationUrl)
                }

                if (imageUrl != null) {
                    Glide.with(this@KickDailyRewardDialog)
                        .load(buildKickGlideUrl(imageUrl))
                        .dontTransform()
                        .into(itemBinding.rewardImage)
                } else {
                    Glide.with(this@KickDailyRewardDialog).clear(itemBinding.rewardImage)
                    itemBinding.rewardImage.setImageDrawable(null)
                }

                if (BuildConfig.DEBUG) {
                    itemBinding.rewardCard.setOnLongClickListener {
                        simulateClaim(ch)
                        true
                    }
                } else {
                    itemBinding.rewardCard.setOnLongClickListener(null)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val itemBinding = ItemRewardCardPageBinding.inflate(layoutInflater, parent, false)
            return CardViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            holder.bind(challenges[position])
        }

        override fun getItemCount(): Int = challenges.size
    }

    companion object {
        private const val TAG = "kick_daily_reward"
        private const val COLOR_KICK_GREEN = 0xFF53FC18.toInt()
        private const val COLOR_KICK_GREEN_DISABLED = 0x4D53FC18.toInt()

        private val BASE_IMAGE_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"
        )
        private val KICK_IMAGE_HEADERS = mapOf(
            "User-Agent" to "Mozilla/5.0 (Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36",
            "Referer" to "https://kick.com/"
        )

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                KickDailyRewardDialog().show(fragmentManager, TAG)
            }
        }
    }
}
