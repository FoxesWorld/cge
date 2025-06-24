package org.foxesworld.cge.importers.fbx;

import com.jme3.util.BufferUtils;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.io.FileInputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Пример полного импорта FBX-модели и подготовки её к отображению.
 * Требует реализации FBXParser, FBXScene, FBXMesh и вашей Render/EngineMesh системы.
 */
public class FBXModelExample {

    // Ваш движковый класс для хранения данных модели
    public static class EngineMesh {
        public float[] vertices;
        public float[] normals;
        public int[] indices;
        public float[][] uvs;
        // Можно добавить boneWeights, boneIndices, материалы и т.д.

        public EngineMesh(float[] vertices, float[] normals, int[] indices, float[][] uvs) {
            this.vertices = vertices;
            this.normals = normals;
            this.indices = indices;
            this.uvs = uvs;
        }
    }

    // Пример метода импорта FBX и создания EngineMesh
    public static EngineMesh importFbxMesh(String fbxFilePath) throws Exception {
        // 1. Парсинг FBX
        FBXParser parser = new FBXParser();
        FBXNode root = parser.parse(FBXModelExample.class.getClassLoader().getResourceAsStream(fbxFilePath));
        FBXScene scene = FBXScene.fromFBXNode(root);

        // 2. Берём первый меш (или перебираем все меши)
        List<FBXMesh> meshes = scene.getMeshes();
        if (meshes.isEmpty())
            throw new RuntimeException("В FBX нет мешей!");

        FBXMesh mesh = meshes.get(0);

        // 3. Конвертируем в EngineMesh (или ваш собственный класс)
        EngineMesh engineMesh = new EngineMesh(
                mesh.vertices,
                mesh.normals,
                mesh.indices,
                mesh.uvs
        );
        return engineMesh;
    }

    // Пример загрузки в OpenGL (или ваш рендер)
    public static void uploadToGPUAndDraw(EngineMesh mesh) {
        // --- 1. Создаём буферы ---
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        // VBO: координаты вершин
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer vertBuf = BufferUtils.createFloatBuffer(mesh.vertices.length);
        vertBuf.put(mesh.vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertBuf, GL15.GL_STATIC_DRAW);
        // Атрибут 0: позиция (vec3)
        GL20.glVertexAttribPointer(0, 3, GL15.GL_FLOAT, false, 0, 0);
        GL20.glEnableVertexAttribArray(0);

        // VBO: нормали (если есть)
        int nbo = 0;
        if (mesh.normals != null && mesh.normals.length > 0) {
            nbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, nbo);
            FloatBuffer normBuf = BufferUtils.createFloatBuffer(mesh.normals.length);
            normBuf.put(mesh.normals).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, normBuf, GL15.GL_STATIC_DRAW);
            // Атрибут 1: нормаль (vec3)
            GL20.glVertexAttribPointer(1, 3, GL15.GL_FLOAT, false, 0, 0);
            GL20.glEnableVertexAttribArray(1);
        }

        // VBO: первый UV-канал (если есть)
        int uvbo = 0;
        if (mesh.uvs != null && mesh.uvs.length > 0 && mesh.uvs[0] != null) {
            uvbo = GL15.glGenBuffers();
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, uvbo);
            FloatBuffer uvBuf = BufferUtils.createFloatBuffer(mesh.uvs[0].length);
            uvBuf.put(mesh.uvs[0]).flip();
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uvBuf, GL15.GL_STATIC_DRAW);
            // Атрибут 2: uv (vec2)
            GL20.glVertexAttribPointer(2, 2, GL15.GL_FLOAT, false, 0, 0);
            GL20.glEnableVertexAttribArray(2);
        }

        // Индексный буфер
        int ibo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        IntBuffer indBuf = BufferUtils.createIntBuffer(mesh.indices.length);
        indBuf.put(mesh.indices).flip();
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indBuf, GL15.GL_STATIC_DRAW);

        // --- 2. Рендер ---
        GL30.glBindVertexArray(vao);
        GL20.glEnableVertexAttribArray(0);
        if (mesh.normals != null && mesh.normals.length > 0)
            GL20.glEnableVertexAttribArray(1);
        if (mesh.uvs != null && mesh.uvs.length > 0 && mesh.uvs[0] != null)
            GL20.glEnableVertexAttribArray(2);

        GL15.glDrawElements(GL15.GL_TRIANGLES, mesh.indices.length, GL15.GL_UNSIGNED_INT, 0);

        // --- 3. Очистка (если надо) ---
        GL20.glDisableVertexAttribArray(0);
        if (mesh.normals != null && mesh.normals.length > 0)
            GL20.glDisableVertexAttribArray(1);
        if (mesh.uvs != null && mesh.uvs.length > 0 && mesh.uvs[0] != null)
            GL20.glDisableVertexAttribArray(2);

        GL30.glBindVertexArray(0);

        // --- Отладка ---
        System.out.println("Загрузка в GPU: " + mesh.vertices.length / 3 + " вершин, " + mesh.indices.length / 3 + " треугольников.");
    }

    public static void main(String[] args) {
        try {
            // 1. Импортируем FBX-модель
            EngineMesh mesh = importFbxMesh("meshes/AlanTree.fbx");

            // 2. Загружаем в GPU (или вашу систему рендера) и отображаем
            uploadToGPUAndDraw(mesh);

            // 3. (Дополнительно) — обработка материалов, скиннинга, анимаций
            // FBXMaterial mat = ... scene.getMaterials() ...
            // FBXSkeleton skel = ... scene.skeleton ...
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}