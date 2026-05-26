package com.hanzg.mipass.domain.usecase

import java.security.SecureRandom
import kotlin.math.ln
import javax.inject.Inject

class GeneratePasswordUseCase @Inject constructor() {

    companion object {
        private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val NUMBERS = "0123456789"
        private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"
    }

    private val secureRandom = SecureRandom()

    data class PasswordConfig(
        val length: Int = 16,
        val includeUppercase: Boolean = true,
        val includeLowercase: Boolean = true,
        val includeNumbers: Boolean = true,
        val includeSymbols: Boolean = true
    )

    /**
     * 生成高强度随机密码
     * 确保至少包含每种选中类型的一个字符，剩余位置随机从所有选中字符集中选取
     */
    fun generate(config: PasswordConfig = PasswordConfig()): String {
        val charSets = buildList {
            if (config.includeUppercase) add(UPPERCASE)
            if (config.includeLowercase) add(LOWERCASE)
            if (config.includeNumbers) add(NUMBERS)
            if (config.includeSymbols) add(SYMBOLS)
        }

        if (charSets.isEmpty()) return ""

        val allChars = charSets.joinToString("")
        val password = CharArray(config.length)
        var index = 0

        // 步骤 1：确保每种选中类型至少包含一个字符
        for (charSet in charSets) {
            if (index < config.length) {
                password[index] = charSet[secureRandom.nextInt(charSet.length)]
                index++
            }
        }

        // 步骤 2：剩余位置随机从所有字符集中选取
        while (index < config.length) {
            password[index] = allChars[secureRandom.nextInt(allChars.length)]
            index++
        }

        // 步骤 3：Fisher-Yates 洗牌消除非随机前缀
        val shuffled = password.toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val temp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = temp
        }

        return shuffled.joinToString("")
    }

    /**
     * 密码强度评分 (0-100)，面向主流网站及账号场景
     *
     * 评分维度：
     * - 熵值估算 (0-45)：基于字符池大小和密码长度的信息熵
     * - 字符类型多样性 (0-25)：实际使用的字符种类数
     * - 长度加分 (0-20)：长度区间的阶梯奖励
     * - 唯一性 (0-10)：唯一字符占比，惩罚过度重复
     */
    fun calculateStrength(password: String, config: PasswordConfig): Int {
        if (password.isEmpty()) return 0

        // 实际使用的字符池大小
        val poolSize = buildList {
            if (config.includeUppercase) add(26)
            if (config.includeLowercase) add(26)
            if (config.includeNumbers) add(10)
            if (config.includeSymbols) add(30)
        }.sum()

        // 1. 熵值估算：bits = length * log2(poolSize)
        val bits = if (poolSize > 0) {
            password.length * (ln(poolSize.toDouble()) / ln(2.0))
        } else 0.0

        val entropyScore = when {
            bits < 30  -> (bits / 30 * 10).toInt()
            bits < 45  -> 10 + ((bits - 30) / 15 * 12).toInt()
            bits < 60  -> 22 + ((bits - 45) / 15 * 10).toInt()
            bits < 80  -> 32 + ((bits - 60) / 20 * 8).toInt()
            bits < 100 -> 40 + ((bits - 100) / 50 * 5).toInt()
            else       -> 45
        }

        // 2. 字符类型多样性：实际使用了几种
        var typeCount = 0
        if (password.any { it in UPPERCASE }) typeCount++
        if (password.any { it in LOWERCASE }) typeCount++
        if (password.any { it in NUMBERS }) typeCount++
        if (password.any { it in SYMBOLS }) typeCount++

        val diversityScore = when (typeCount) {
            1 -> 4
            2 -> 10
            3 -> 18
            4 -> 25
            else -> 0
        }

        // 3. 长度阶梯加分
        val lengthScore = when {
            password.length < 6  -> 0
            password.length < 8  -> 3
            password.length < 10 -> 6
            password.length < 12 -> 10
            password.length < 16 -> 14
            password.length < 20 -> 17
            else                -> 20
        }

        // 4. 唯一性：唯一字符占比
        val uniqueRatio = password.toSet().size.toFloat() / password.length.toFloat()
        val uniquenessScore = (uniqueRatio * 10).toInt()

        val total = entropyScore + diversityScore + lengthScore + uniquenessScore
        return total.coerceIn(0, 100)
    }
}
