package org.foxesworld.cge.ui.elements;

import com.jme3.scene.Node;

/**
 * Базовый интерфейс для всех UI-элементов (текст, картинка, кнопка, панель).
 */
public interface UIElement {
    /** Уникальный идентификатор элемента (берётся из XML, атрибут id). */
    String getId();

    /** Нода, содержащая всю визуальную составляющую элемента. */
    Node getNode();

    /**
     * Если у элемента есть своё собственное выравнивание/anchor (например, у TextElement может быть align="center").
     * Если нет — возвращаем false.
     */
    boolean hasOwnAlign();

    /**
     * Если hasOwnAlign() == true, это возвращаемое строковое значение (например: "center", "top-right" или "100,50").
     */
    String getOwnAlign();

    /** Ссылка на родительскую панель (или null, если элемент находится в корне). */
    PanelElement getParentPanel();

    /**
     * Установить свойство по ключу (всё, что приходит через атрибуты XML, кроме специальных: id, type, onClick).
     * Примеры: color="1,1,1,1", fontSize="24", posX="100", posY="200" и т.д.
     */
    void setProperty(String key, String value);

    /**
     * Установить “onClick” обработчик (если в XML есть onClick="methodName").
     * При клике (внутри TextElement/ImageElement) будет вызван метод methodName на eventHandlerTarget.
     */
    void setOnClickHandler(String methodName, Object eventHandlerTarget);
}
