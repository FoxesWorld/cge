package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;

import java.util.Objects;

/**
 * DTO для элемента &lt;KeyBind id="..." action="..." defaultKey="..."/&gt;
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class KeyBindXml {

    /** Уникальный идентификатор привязки (обязательно). */
    @XmlAttribute
    public String id;

    /** Человекочитаемое описание действия (опционально). */
    @XmlAttribute
    public String action;

    /** Имя клавиши по-умолчанию, как в XML (например, "P", "SPACE", "F1") — опционально. */
    @XmlAttribute
    public String defaultKey;

    public KeyBindXml() {
        // JAXB
    }

    public KeyBindXml(String id, String action, String defaultKey) {
        this.id = id;
        this.action = action;
        this.defaultKey = defaultKey;
    }

    /**
     * Нормализует строки (null -> "", trim).
     */
    public void normalize() {
        if (id != null) id = id.trim();
        if (action != null) action = action.trim();
        if (defaultKey != null) defaultKey = defaultKey.trim();
    }

    /**
     * Простая валидация.
     *
     * @throws IllegalStateException если id пустой
     */
    public void validate() {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalStateException("KeyBind id must be provided (action=" + action + ", defaultKey=" + defaultKey + ")");
        }
    }

    @Override
    public String toString() {
        return "KeyBindXml{" +
                "id='" + id + '\'' +
                ", action='" + action + '\'' +
                ", defaultKey='" + defaultKey + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyBindXml)) return false;
        KeyBindXml that = (KeyBindXml) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(action, that.action) &&
                Objects.equals(defaultKey, that.defaultKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, action, defaultKey);
    }
}
