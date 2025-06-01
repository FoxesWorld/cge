package org.foxesworld.cge.ui.novaUi.elements.text;

import com.jme3.font.BitmapText;
import java.util.Random;

/**
 * TextAnimator — анимирует смену старого текста на новый либо:
 *  • INTERPOLATE_NUMERIC — если строки содержат лишь цифры (с точкой или без),
 *    то плавно интерполирует значение от old → new.
 *  • SCRAMBLE_TEXT — для любых других строк: сначала подставляет рандом-символы,
 *    затем постепенно «расшифровывает» реальные символы.
 */
public class TextAnimator {

    private enum Mode { IDLE, INTERPOLATE_NUMERIC, SCRAMBLE_TEXT }

    private final BitmapText bitmapText;
    private final Random random = new Random();

    // Старое и новое «сырые» значения
    private String oldText = "";
    private String newText = "";

    // Для числовой анимации
    private float oldValue;
    private float targetValue;
    private float currentValue;
    private final float numericSpeed; // единицы (единицы числа) в секунду

    // Для scramble-анимации
    private char[] displayChars; // текущий массив символов (включая рандом)
    private int revealIndex;     // до какого индекса уже «раскодировано»
    private final float scrambleSpeed; // символов в секунду при расшифровке

    private Mode mode = Mode.IDLE;

    public TextAnimator(BitmapText bitmapText) {
        this(bitmapText, 50f, 20f);
        // numericSpeed = 50 элементов/сек (при числах)
        // scrambleSpeed = 20 символов/сек во время «раскрытия»
    }

    /**
     * @param bitmapText      сам BitmapText, в который будем писать
     * @param numericSpeed    скорость изменения числа (единицы/сек)
     * @param scrambleSpeed   скорость «раскрытия» реальных символов (символов/сек)
     */
    public TextAnimator(BitmapText bitmapText, float numericSpeed, float scrambleSpeed) {
        this.bitmapText = bitmapText;
        this.numericSpeed = numericSpeed;
        this.scrambleSpeed = scrambleSpeed;
    }

    /**
     * Запускает анимацию смены текста.
     * Если oldText и newText — числа → INTERPOLATE_NUMERIC,
     * иначе → SCRAMBLE_TEXT.
     */
    public void animateTextChange(String rawNewText) {
        if (rawNewText == null) rawNewText = "";
        String rawOld = bitmapText.getText();

        this.oldText = rawOld;
        this.newText = rawNewText;

        if (isNumeric(rawOld) && isNumeric(rawNewText)) {
            // Числовая анимация
            mode = Mode.INTERPOLATE_NUMERIC;
            oldValue = Float.parseFloat(rawOld);
            targetValue = Float.parseFloat(rawNewText);
            currentValue = oldValue;
            updateNumericText(); // сразу показать oldValue
        } else {
            // Scramble-анимация
            mode = Mode.SCRAMBLE_TEXT;
            prepareScramble(rawOld, rawNewText);
        }
    }

    /**
     * Вызывать каждый кадр (tpf) из TextElement.update(tpf).
     */
    public void update(float tpf) {
        switch (mode) {
            case INTERPOLATE_NUMERIC -> {
                float direction = Math.signum(targetValue - oldValue);
                float delta = numericSpeed * tpf * direction;
                currentValue += delta;

                // Если перепрыгнули через цель или равны — останавливаемся
                if ((direction > 0 && currentValue >= targetValue) ||
                        (direction < 0 && currentValue <= targetValue)) {
                    currentValue = targetValue;
                    mode = Mode.IDLE;
                }
                updateNumericText();
            }

            case SCRAMBLE_TEXT -> {
                // Пока revealIndex < newText.length, постепенно «раскрываем» символы
                int length = newText.length();
                if (revealIndex < length) {
                    // Сколько символов нужно «раскрыть» за этот кадр:
                    int toReveal = Math.min(
                            length - revealIndex,
                            (int) Math.ceil(scrambleSpeed * tpf)
                    );
                    // Раскрываем позиции [revealIndex, revealIndex + toReveal)
                    for (int i = revealIndex; i < revealIndex + toReveal && i < length; i++) {
                        displayChars[i] = newText.charAt(i);
                    }
                    revealIndex += toReveal;
                }

                // Для остальных позиций (> revealIndex) — ставим рандомные символы
                for (int i = revealIndex; i < displayChars.length; i++) {
                    displayChars[i] = randomChar();
                }

                bitmapText.setText(new String(displayChars));

                // Если раскрылось всё — заканчиваем
                if (revealIndex >= length) {
                    mode = Mode.IDLE;
                }
            }

            case IDLE -> {
                // Ничего не делаем, текст уже установлен
            }
        }
    }

    // ------------------------------- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ -------------------------------

    /** Заполняем массив displayChars для SCRAMBLE_TEXT-режима */
    private void prepareScramble(String oldStr, String newStr) {
        int length = newStr.length();
        displayChars = new char[length];
        revealIndex = 0;

        // Если старая строка короче — просто заполняем начало случайными символами,
        // если длиннее или равна — копируем в displayChars первые символы из oldStr либо усекаем.
        int copyLen = Math.min(oldStr.length(), length);
        for (int i = 0; i < copyLen; i++) {
            displayChars[i] = oldStr.charAt(i);
        }
        // Остальные — рандом
        for (int i = copyLen; i < length; i++) {
            displayChars[i] = randomChar();
        }
        // Сразу покажем начальное состояние
        bitmapText.setText(new String(displayChars));
    }

    /** Проверка, является ли строка числом (целое или с плавающей точкой). */
    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Float.parseFloat(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Обновляет отображение текущего числа (округляем до 1–2 знаков после запятой по желанию). */
    private void updateNumericText() {
        // Отображаем, например, с двумя знаками после точки:
        String formatted = String.format("%.2f", currentValue);
        // Убираем возможный «.00», если число целое:
        if (formatted.endsWith(".00")) {
            formatted = formatted.substring(0, formatted.length() - 3);
        }
        bitmapText.setText(formatted);
    }

    /** Возвращает случайный «символ-шум»: цифра или заглавная латиница. */
    private char randomChar() {
        int choice = random.nextInt(36);
        if (choice < 10) {
            return (char) ('0' + choice);
        } else {
            return (char) ('A' + (choice - 10));
        }
    }
}
