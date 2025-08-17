package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * DTO для корневого элемента &lt;KeyBindings&gt;.
 *
 * Пример использования:
 *   KeyBindingsXml xml = (KeyBindingsXml) unmarshaller.unmarshal(stream);
 *   xml.normalize();
 *   xml.validate();
 *   // можно получить InputStream и передать в KeyBindingsManager.loadDefinitionsFromXml(...)
 *   InputStream prepared = xml.toXmlInputStream();
 */
@XmlRootElement(name = "KeyBindings")
@XmlAccessorType(XmlAccessType.FIELD)
public class KeyBindingsXml extends ComponentXml {

    @XmlElement(name = "KeyBind")
    public List<KeyBindXml> binds;

    public KeyBindingsXml() {
        // JAXB
    }

    /**
     * Нормализует поля: заменяет null на пустые коллекции/строки, тримит строки.
     * Вызывать сразу после JAXB unmarshalling.
     */
    public void normalize() {
        if (binds == null) binds = new ArrayList<>();
        else {
            binds.removeIf(Objects::isNull);
            for (KeyBindXml kb : binds) {
                if (kb != null) kb.normalize();
            }
        }
    }

    /**
     * Простейшая валидация: проверяет, что у каждой привязки есть id.
     *
     * @throws IllegalStateException при некорректных данных
     */
    public void validate() {
        if (binds == null) return;
        for (KeyBindXml kb : binds) {
            if (kb == null) continue;
            if (kb.id == null || kb.id.trim().isEmpty()) {
                throw new IllegalStateException("KeyBind with empty id encountered: " + kb);
            }
            // defaultKey может быть пустым — допустимо
        }
    }

    /**
     * Возвращает безопасный неизменяемый список KeyBindXml.
     */
    public List<KeyBindXml> getBindsSafe() {
        if (binds == null) return Collections.emptyList();
        return Collections.unmodifiableList(binds);
    }

    /**
     * Сериализует текущий объект в XML и возвращает InputStream с содержимым.
     * Удобно для передачи в методы, которые читают XML из InputStream
     * (например, KeyBindingsManager.loadDefinitionsFromXml(...)).
     *
     * @return InputStream с UTF-8 XML
     * @throws RuntimeException при ошибке сериализации
     */
    public InputStream toXmlInputStream() {
        try {
            JAXBContext ctx = JAXBContext.newInstance(KeyBindingsXml.class, KeyBindXml.class);
            Marshaller m = ctx.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            m.marshal(this, baos);
            byte[] data = baos.toByteArray();
            return new ByteArrayInputStream(data);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to marshal KeyBindingsXml to InputStream", ex);
        }
    }

    @Override
    public String toString() {
        return "KeyBindingsXml{binds=" + (binds == null ? 0 : binds.size()) + "}";
    }
}
