package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Achievement
import com.example.data.model.DailyActivity
import com.example.data.model.UserProfile
import com.example.data.model.Workout
import com.example.data.repository.FitnessRepository
import com.example.sensor.StepTrackerManager
import com.example.sensor.StepTrackerState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ActiveWorkoutSession(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val type: String = "walking",
    val elapsedSeconds: Long = 0L,
    val steps: Long = 0L,
    val distanceMeters: Double = 0.0,
    val calories: Double = 0.0,
    val avgPaceSecondsPerKm: Double = 0.0,
    val startTimeMs: Long = 0L
)

class FitTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FitnessRepository(application.applicationContext)
    val stepTrackerManager = StepTrackerManager(application.applicationContext)

    val stepTrackerState: StateFlow<StepTrackerState> = stepTrackerManager.state

    private val currentUserId: String
        get() = Firebase.auth.currentUser?.uid ?: ""

    // Observables from Firestore
    val userProfile: StateFlow<UserProfile> = repository.observeUserProfile(currentUserId)
        .map { it ?: UserProfile(userId = currentUserId) }
        .catch { emit(UserProfile(userId = currentUserId)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserProfile(userId = currentUserId)
        )

    val todayActivity: StateFlow<DailyActivity> = repository.observeDailyActivity(
        userId = currentUserId,
        date = repository.getTodayDateString()
    )
        .map { it ?: DailyActivity(date = repository.getTodayDateString(), stepGoal = userProfile.value.dailyStepGoal) }
        .catch { emit(DailyActivity(date = repository.getTodayDateString(), stepGoal = 10000L)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DailyActivity(date = repository.getTodayDateString(), stepGoal = 10000L)
        )

    val allActivities: StateFlow<List<DailyActivity>> = repository.observeAllActivities(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val workouts: StateFlow<List<Workout>> = repository.observeWorkouts(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val achievements: StateFlow<List<Achievement>> = repository.observeAchievements(currentUserId)
        .catch { emit(emptyList()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    // Active live workout session
    private val _activeWorkout = MutableStateFlow(ActiveWorkoutSession())
    val activeWorkout: StateFlow<ActiveWorkoutSession> = _activeWorkout.asStateFlow()

    private var workoutTimerJob: Job? = null

    init {
        // Start sensor observation
        stepTrackerManager.startTracking()

        // Sync initial profile if new
        viewModelScope.launch {
            if (currentUserId.isNotBlank()) {
                val existing = repository.observeUserProfile(currentUserId)
                // If profile doesn't exist, create it with user's Google display name
                val googleUser = Firebase.auth.currentUser
                repository.saveUserProfile(
                    UserProfile(
                        userId = currentUserId,
                        displayName = googleUser?.displayName ?: "Fitness Enthusiast"
                    )
                )
            }
        }
    }

    fun addSteps(delta: Long) {
        stepTrackerManager.simulateSteps(delta)
        viewModelScope.launch {
            val profile = userProfile.value
            repository.addStepsDelta(
                deltaSteps = delta,
                stepGoal = profile.dailyStepGoal,
                strideLengthMeters = profile.strideLengthMeters,
                weightKg = profile.weightKg ?: 70.0
            )
        }
    }

    fun updateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveUserProfile(profile)
        }
    }

    // --- Workout Management ---

    fun startWorkout(type: String) {
        workoutTimerJob?.cancel()
        _activeWorkout.value = ActiveWorkoutSession(
            isActive = true,
            isPaused = false,
            type = type,
            elapsedSeconds = 0L,
            steps = 0L,
            distanceMeters = 0.0,
            calories = 0.0,
            avgPaceSecondsPerKm = 0.0,
            startTimeMs = System.currentTimeMillis()
        )
        stepTrackerManager.resetSessionSteps()

        startWorkoutTimer()
    }

    fun pauseWorkout() {
        workoutTimerJob?.cancel()
        _activeWorkout.update { it.copy(isPaused = true) }
    }

    fun resumeWorkout() {
        _activeWorkout.update { it.copy(isPaused = false) }
        startWorkoutTimer()
    }

    private fun startWorkoutTimer() {
        workoutTimerJob?.cancel()
        workoutTimerJob = viewModelScope.launch {
            while (_activeWorkout.value.isActive && !_activeWorkout.value.isPaused) {
                delay(1000L)
                val newElapsed = _activeWorkout.value.elapsedSeconds + 1
                val sessionSteps = stepTrackerManager.state.value.currentSessionSteps
                val stride = userProfile.value.strideLengthMeters
                val weight = userProfile.value.weightKg ?: 70.0

                // Distance calculation
                val distMeters = if (_activeWorkout.value.type == "cycling") {
                    // Cycling: estimate ~5 meters per second (approx 18 km/h)
                    newElapsed * 5.0
                } else {
                    sessionSteps * stride
                }

                // Calories estimate based on MET values
                // Walking: 3.5 MET, Running: 8.0 MET, Cycling: 6.0 MET, Stretching: 2.5 MET
                val met = when (_activeWorkout.value.type) {
                    "running" -> 8.0
                    "cycling" -> 6.0
                    "stretching" -> 2.5
                    else -> 3.5
                }
                // kcal = MET * weight(kg) * time(hours)
                val cal = met * weight * (newElapsed / 3600.0)

                // Pace in seconds per km
                val pace = if (distMeters > 50) {
                    (newElapsed / (distMeters / 1000.0))
                } else {
                    0.0
                }

                _activeWorkout.update {
                    it.copy(
                        elapsedSeconds = newElapsed,
                        steps = sessionSteps,
                        distanceMeters = distMeters,
                        calories = cal,
                        avgPaceSecondsPerKm = pace
                    )
                }
            }
        }
    }

    fun finishWorkout(onCompleted: () -> Unit = {}) {
        workoutTimerJob?.cancel()
        val session = _activeWorkout.value
        val today = repository.getTodayDateString()

        val workout = Workout(
            id = "w_" + UUID.randomUUID().toString().take(8),
            userId = currentUserId,
            type = session.type,
            durationSeconds = session.elapsedSeconds,
            steps = session.steps,
            distanceMeters = session.distanceMeters,
            calories = session.calories,
            avgPaceSecondsPerKm = session.avgPaceSecondsPerKm,
            date = today
        )

        viewModelScope.launch {
            repository.saveWorkout(
                workout = workout,
                strideLengthMeters = userProfile.value.strideLengthMeters,
                weightKg = userProfile.value.weightKg ?: 70.0
            )
            _activeWorkout.value = ActiveWorkoutSession(isActive = false)
            onCompleted()
        }
    }

    fun cancelWorkout() {
        workoutTimerJob?.cancel()
        _activeWorkout.value = ActiveWorkoutSession(isActive = false)
    }

    // --- Data Export & Privacy ---

    fun exportPersonalDataJson(): String {
        val root = JSONObject()
        val profile = userProfile.value
        val activities = allActivities.value
        val workoutList = workouts.value
        val badgeList = achievements.value

        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
        root.put("user", JSONObject().apply {
            put("userId", currentUserId)
            put("displayName", profile.displayName)
            put("dailyStepGoal", profile.dailyStepGoal)
            put("preferredUnit", profile.preferredUnit)
            put("strideLengthMeters", profile.strideLengthMeters)
            put("weightKg", profile.weightKg)
            put("heightCm", profile.heightCm)
            put("age", profile.age)
        })

        val actArray = JSONArray()
        activities.forEach { act ->
            actArray.put(JSONObject().apply {
                put("date", act.date)
                put("steps", act.steps)
                put("stepGoal", act.stepGoal)
                put("distanceMeters", act.distanceMeters)
                put("calories", act.calories)
                put("activeMinutes", act.activeMinutes)
                put("goalReached", act.goalReached)
            })
        }
        root.put("dailyActivities", actArray)

        val wkArray = JSONArray()
        workoutList.forEach { wk ->
            wkArray.put(JSONObject().apply {
                put("id", wk.id)
                put("type", wk.type)
                put("durationSeconds", wk.durationSeconds)
                put("steps", wk.steps)
                put("distanceMeters", wk.distanceMeters)
                put("calories", wk.calories)
                put("date", wk.date)
            })
        }
        root.put("workouts", wkArray)

        val achArray = JSONArray()
        badgeList.forEach { b ->
            achArray.put(JSONObject().apply {
                put("badgeId", b.badgeId)
                put("title", b.title)
                put("description", b.description)
                put("isUnlocked", b.isUnlocked)
            })
        }
        root.put("achievements", achArray)

        return root.toString(2)
    }

    fun deletePersonalData(onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllPersonalData()
            onSuccess()
        }
    }

    override fun onCleared() {
        super.onCleared()
        workoutTimerJob?.cancel()
        stepTrackerManager.stopTracking()
    }
}
