package com.bylins.client.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.sound.sampled.*
import java.io.File
import kotlin.concurrent.thread

/**
 * Менеджер звуковых уведомлений
 */
class SoundManager {
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled

    private val _volume = MutableStateFlow(0.5f) // 0.0 - 1.0
    val volume: StateFlow<Float> = _volume

    /**
     * Предопределённые звуки
     */
    enum class SoundType {
        LOW_HP,      // Низкое HP
        TELL,        // Получен tell
        WHISPER,     // Получен whisper
        LEVEL_UP,    // Повышение уровня
        DEATH,       // Смерть персонажа
        COMBAT,      // Начало боя
        ALERT        // Общее предупреждение
    }

    /**
     * Проигрывает системный звук (beep)
     */
    fun playBeep() {
        if (!_soundEnabled.value) return

        thread {
            try {
                val audioInputStream = AudioSystem.getAudioInputStream(
                    SoundManager::class.java.getResourceAsStream("/sounds/beep.wav")
                        ?: return@thread
                )
                playAudioStream(audioInputStream)
            } catch (e: Exception) {
                // Если файл не найден, используем системный beep
                java.awt.Toolkit.getDefaultToolkit().beep()
            }
        }
    }

    /**
     * Проигрывает звук по типу события
     */
    fun playSound(type: SoundType) {
        if (!_soundEnabled.value) return

        thread {
            try {
                // Пытаемся загрузить звук из ресурсов
                val soundFile = when (type) {
                    SoundType.LOW_HP -> "/sounds/low_hp.wav"
                    SoundType.TELL -> "/sounds/tell.wav"
                    SoundType.WHISPER -> "/sounds/whisper.wav"
                    SoundType.LEVEL_UP -> "/sounds/level_up.wav"
                    SoundType.DEATH -> "/sounds/death.wav"
                    SoundType.COMBAT -> "/sounds/combat.wav"
                    SoundType.ALERT -> "/sounds/alert.wav"
                }

                val audioInputStream = AudioSystem.getAudioInputStream(
                    SoundManager::class.java.getResourceAsStream(soundFile)
                        ?: run {
                            // Если файл не найден, используем beep
                            java.awt.Toolkit.getDefaultToolkit().beep()
                            return@thread
                        }
                )
                playAudioStream(audioInputStream)
            } catch (e: Exception) {
                // В случае ошибки используем системный beep
                java.awt.Toolkit.getDefaultToolkit().beep()
            }
        }
    }

    /**
     * Проигрывает звук из внешнего файла
     */
    fun playCustomSound(file: File) {
        if (!_soundEnabled.value) return
        if (!file.exists()) return

        thread {
            try {
                val audioInputStream = AudioSystem.getAudioInputStream(file)
                playAudioStream(audioInputStream)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Проигрывает аудио поток
     */
    private fun playAudioStream(audioInputStream: AudioInputStream) {
        try {
            val clip = AudioSystem.getClip()
            clip.open(audioInputStream)

            // Устанавливаем громкость
            val gainControl = clip.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
            gainControl?.let {
                val min = it.minimum
                val max = it.maximum
                val range = max - min
                val gain = min + range * _volume.value
                it.value = gain
            }

            clip.start()

            // Ждём окончания воспроизведения
            while (clip.isRunning) {
                Thread.sleep(10)
            }

            clip.close()
            audioInputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Включает/выключает звуки
     */
    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
    }

    /**
     * Устанавливает громкость (0.0 - 1.0)
     */
    fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0f, 1f)
    }
}
