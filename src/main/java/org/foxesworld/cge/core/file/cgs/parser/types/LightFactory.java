package org.foxesworld.cge.core.file.cgs.parser.types;

import com.jme3.light.Light;

import java.util.Map;

@FunctionalInterface
interface LightFactory<T extends Light> {
    T create(int cid, Map<String, Object> values);
}