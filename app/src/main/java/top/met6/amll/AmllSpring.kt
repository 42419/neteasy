package top.met6.amll

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Parameters used by the original AMLL analytic spring. */
data class AmllSpringParams(
    val mass: Double = 1.0,
    val damping: Double = 10.0,
    val stiffness: Double = 100.0,
    val soft: Boolean = false,
)

/**
 * Closed-form spring ported from AMLL's TypeScript Spring implementation.
 * Target changes preserve velocity, avoiding the stop/restart jitter of item animations.
 */
class AmllSpring(initialPosition: Double = 0.0) {
    var position: Double = initialPosition
        private set
    var velocity: Double = 0.0
        private set
    var target: Double = initialPosition
        private set
    var params: AmllSpringParams = AmllSpringParams()
        private set

    fun snapTo(value: Double) {
        position = value
        target = value
        velocity = 0.0
    }

    fun updateTarget(value: Double, params: AmllSpringParams = this.params) {
        target = value
        this.params = params
    }

    fun step(deltaSeconds: Double): Double {
        val t = deltaSeconds.coerceIn(0.0, 0.064)
        if (t <= 0.0) return position
        val from = position
        val to = target
        val delta = to - from
        val mass = params.mass.coerceAtLeast(0.0001)
        val stiffness = params.stiffness.coerceAtLeast(0.0001)
        val damping = params.damping.coerceAtLeast(0.0)
        val initialVelocity = velocity

        if (params.soft || damping / (2.0 * sqrt(stiffness * mass)) >= 1.0) {
            val angularFrequency = -sqrt(stiffness / mass)
            val leftover = -angularFrequency * delta - initialVelocity
            val decay = exp(t * angularFrequency)
            position = to - (delta + t * leftover) * decay
            velocity = -(leftover + angularFrequency * (delta + t * leftover)) * decay
        } else {
            val dampingFrequency = sqrt(4.0 * mass * stiffness - damping * damping)
            val leftover = (damping * delta - 2.0 * mass * initialVelocity) / dampingFrequency
            val dfm = 0.5 * dampingFrequency / mass
            val dm = -0.5 * damping / mass
            val decay = exp(t * dm)
            val wave = cos(t * dfm) * delta + sin(t * dfm) * leftover
            position = to - wave * decay
            velocity = decay * (
                dfm * sin(t * dfm) * delta -
                    dfm * cos(t * dfm) * leftover -
                    dm * wave
                )
        }

        if (isSettled()) snapTo(target)
        return position
    }

    fun isSettled(positionThreshold: Double = 0.1, velocityThreshold: Double = 0.1): Boolean =
        abs(target - position) < positionThreshold && abs(velocity) < velocityThreshold
}

/** Exact Kotlin port of AMLL's dynamic vertical-scroll spring policy. */
fun lyricScrollSpringPolicy(
    isSeeking: Boolean,
    isInterludeActive: Boolean,
    intervalMs: Long?,
): AmllSpringParams {
    if (isSeeking || isInterludeActive || intervalMs == null) {
        return AmllSpringParams(mass = 0.9, stiffness = 90.0, damping = 15.0)
    }
    val clamped = intervalMs.coerceIn(100, 800)
    val linearRatio = 1.0 - (clamped - 100).toDouble() / 700.0
    val ratio = linearRatio.pow(0.2)
    val stiffness = 170.0 + ratio * (220.0 - 170.0)
    return AmllSpringParams(
        mass = 0.9,
        stiffness = stiffness,
        damping = sqrt(stiffness) * 2.2,
    )
}
