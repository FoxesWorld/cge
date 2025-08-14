package org.foxesworld.cge.tmp.menu.xml;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "image")
public class ImageXml extends ComponentXml {
    /** Путь до текстуры (asset path) */
    @XmlAttribute public String path = "";

    /** Режим масштабирования: fit | cover | stretch (по умолчанию fit) */
    @XmlAttribute public String scaleMode = "fit";

    /** Anchor: top-left / top-center / top-right / center-left / center / center-right / bottom-left / bottom-center / bottom-right */
    @XmlAttribute public String anchor = "center";

    /**
     * Ширина / высота области изображения.
     * Поддерживается: абсолютные пиксели ("200"), проценты от высоты или ширины окна ("50%"),
     * либо единицы vw/vh (например "10vw", "8vh") — парсер должен учитывать это.
     */
    @XmlAttribute public String width = "100";
    @XmlAttribute public String height = "100";

    /** Цвет-тинт в HEX (например "#FFFFFFFF") */
    @XmlAttribute public String tint = "#FFFFFFFF";

    /** Показывать ли фон (rounded quad) — "true"/"false" */
    @XmlAttribute public String showBackground = "false";

    /** Цвет фона в HEX */
    @XmlAttribute public String backgroundColor = "#00000000";

    /** Радиус скругления (в пикселях или в процентах если захочешь) */
    @XmlAttribute public String cornerRadius = "0";

    public ImageXml() {}
}
