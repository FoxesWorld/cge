package org.foxesworld.cge.ue.parser;

import org.foxesworld.cge.ue.model.ExportEntry;
import org.foxesworld.cge.ue.model.UPackage;

import java.io.File;

/**
 * Интерфейс для специальных парсеров типов (Texture2D, StaticMesh и т.д.).
 * Реализуйте для того, чтобы декодировать/экспортировать конкретный тип.
 */
public interface TypeParser {
    /**
     * @param pkg    разобранный пакет
     * @param export экспортная запись
     * @param uexp   файл .uexp (может быть null)
     * @param outDir куда складывать извлечённые данные
     * @throws Exception
     */
    void parse(UPackage pkg, ExportEntry export, File uexp, File outDir) throws Exception;

    /**
     * Тип/класс, который парсер обрабатывает, например "Texture2D"
     */
    String typeName();
}