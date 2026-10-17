package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.Achievement
import com.example.data.model.DailyActivity
import com.example.data.model.UserProfile
import com.example.data.model.Workout
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FitnessRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    // --- User Profile ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
                }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                    throw e
                }
        )
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        try {
            val data = hashMapOf<String, Any>(
                "userId" to uid,
                "displayName" to profile.displayName,
                "dailyStepGoal" to profile.dailyStepGoal,
                "preferredUnit" to profile.preferredUnit,
                "strideLengthMeters" to profile.strideLengthMeters,
                "stepRemindersEnabled" to profile.stepRemindersEnabled,
                "themePreference" to profile.themePreference,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            profile.age?.let { data["age"] = it }
            profile.heightCm?.let { data["heightCm"] = it }
            profile.weightKg?.let { data["weightKg"] = it }

            if (profile.createdAt == null) {
                data["createdAt"] = FieldValue.serverTimestamp()
            }

            docRef.set(data, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Daily Activity ---

    fun observeDailyActivity(userId: String, date: String): Flow<DailyActivity?> = flow {
        val path = "users/$userId/daily_activity/$date"
        emitAll(
            db.collection("users").document(userId).collection("daily_activity").document(date)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) snapshot.toObject(DailyActivity::class.java) else null
                }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.GET, path)
                    throw e
                }
        )
    }

    fun observeAllActivities(userId: String): Flow<List<DailyActivity>> = flow {
        val path = "users/$userId/daily_activity"
        emitAll(
            db.collection("users").document(userId).collection("daily_activity")
                .orderBy("date", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(DailyActivity::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun recordOrUpdateSteps(
        steps: Long,
        stepGoal: Long,
        strideLengthMeters: Double,
        weightKg: Double,
        date: String = getTodayDateString()
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("daily_activity").document(date)
        try {
            val distMeters = steps * strideLengthMeters
            // Estimated calories: approx 0.04 - 0.05 kcal per step based on body weight
            val calPerStep = (weightKg / 70.0) * 0.042
            val caloriesBurned = steps * calPerStep
            // Active minutes estimate: approx 100 steps per active minute of walking
            val activeMinutes = (steps / 100).coerceAtLeast(0)
            val reached = steps >= stepGoal

            val data = hashMapOf<String, Any>(
                "date" to date,
                "steps" to steps,
                "stepGoal" to stepGoal,
                "distanceMeters" to distMeters,
                "calories" to caloriesBurned,
                "activeMinutes" to activeMinutes,
                "goalReached" to reached,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            docRef.set(data, SetOptions.merge()).await()

            // Check achievements triggers
            evaluateAndUnlockAchievements(steps, reached)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun addStepsDelta(
        deltaSteps: Long,
        stepGoal: Long,
        strideLengthMeters: Double,
        weightKg: Double,
        date: String = getTodayDateString()
    ) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("daily_activity").document(date)
        try {
            val snapshot = docRef.get().await()
            val currentSteps = if (snapshot.exists()) {
                snapshot.getLong("steps") ?: 0L
            } else {
                0L
            }
            val newSteps = currentSteps + deltaSteps
            recordOrUpdateSteps(newSteps, stepGoal, strideLengthMeters, weightKg, date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Workouts ---

    fun observeWorkouts(userId: String): Flow<List<Workout>> = flow {
        val path = "users/$userId/workouts"
        emitAll(
            db.collection("users").document(userId).collection("workouts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Workout::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun saveWorkout(workout: Workout, strideLengthMeters: Double, weightKg: Double) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("workouts").document(workout.id)
        try {
            val data = hashMapOf<String, Any>(
                "id" to workout.id,
                "userId" to uid,
                "type" to workout.type,
                "durationSeconds" to workout.durationSeconds,
                "steps" to workout.steps,
                "distanceMeters" to workout.distanceMeters,
                "calories" to workout.calories,
                "avgPaceSecondsPerKm" to workout.avgPaceSecondsPerKm,
                "date" to workout.date,
                "timestamp" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data).await()

            // Also add workout steps to daily activity
            if (workout.steps > 0) {
                addStepsDelta(workout.steps, 10000L, strideLengthMeters, weightKg, workout.date)
            }

            // Unlock First Activity badge
            unlockAchievement(
                badgeId = "first_activity",
                title = "First Workout",
                description = "Completed your first active workout session!"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // --- Achievements ---

    fun observeAchievements(userId: String): Flow<List<Achievement>> = flow {
        val path = "users/$userId/achievements"
        emitAll(
            db.collection("users").document(userId).collection("achievements")
                .snapshots()
                .map { snapshot -> snapshot.toObjects(Achievement::class.java) }
                .catch { e ->
                    if (e is Exception) handleFirestoreError(e, OperationType.LIST, path)
                    throw e
                }
        )
    }

    suspend fun unlockAchievement(badgeId: String, title: String, description: String) {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("achievements").document(badgeId)
        try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists() || snapshot.getBoolean("isUnlocked") != true) {
                val data = hashMapOf<String, Any>(
                    "id" to badgeId,
                    "badgeId" to badgeId,
                    "title" to title,
                    "description" to description,
                    "isUnlocked" to true,
                    "unlockedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data, SetOptions.merge()).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    private suspend fun evaluateAndUnlockAchievements(todaySteps: Long, goalReached: Boolean) {
        if (todaySteps > 0) {
            unlockAchievement("first_activity", "First Activity", "Took your first logged steps with FitTrack!")
        }
        if (todaySteps >= 5000) {
            unlockAchievement("steps_5k", "5,000 Steps Club", "Walked over 5,000 steps in a single day!")
        }
        if (todaySteps >= 10000) {
            unlockAchievement("steps_10k", "10,000 Steps Champion", "Crushed the 10,000 steps daily benchmark!")
        }
    }

    // --- Privacy & Data Management ---

    suspend fun clearAllPersonalData() {
        val uid = requireUserId()
        try {
            // Delete daily activities
            val dailyRef = db.collection("users").document(uid).collection("daily_activity")
            val dailyDocs = dailyRef.get().await()
            for (doc in dailyDocs.documents) {
                doc.reference.delete().await()
            }

            // Delete workouts
            val workoutsRef = db.collection("users").document(uid).collection("workouts")
            val workoutDocs = workoutsRef.get().await()
            for (doc in workoutDocs.documents) {
                doc.reference.delete().await()
            }

            // Delete achievements
            val achRef = db.collection("users").document(uid).collection("achievements")
            val achDocs = achRef.get().await()
            for (doc in achDocs.documents) {
                doc.reference.delete().await()
            }

            // Reset user profile stats
            val profileRef = db.collection("users").document(uid)
            profileRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            throw e
        }
    }
}
