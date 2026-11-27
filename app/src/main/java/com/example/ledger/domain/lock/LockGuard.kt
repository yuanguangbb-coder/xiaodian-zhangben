package com.example.ledger.domain.lock

/** 应用锁策略：连错 [MAX_ATTEMPTS] 次，锁 [COOLDOWN_SECONDS] 秒 */
object LockPolicy {
    const val MAX_ATTEMPTS = 5
    const val COOLDOWN_SECONDS = 30
}

/**
 * 输错密码的计数与冷却。实现放在设置层（SharedPreferences），
 * 这样杀进程重开也绕不过去。
 */
interface LockGuard {
    /** 当前已连错几次（触发冷却后清零） */
    val failedAttempts: Int

    /** 冷却结束时间戳；0 表示不在冷却 */
    val cooldownUntilMillis: Long

    /** 记一次输错，返回是否触发了冷却 */
    fun recordFailure(nowMillis: Long): Boolean

    fun clearFailures()
}