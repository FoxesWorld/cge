package org.foxesworld.cge.importers.fbx.skeleton;

import org.foxesworld.cge.importers.fbx.FBXNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Представление кости FBX с поддержкой parent-child и локальной матрицы.
 */
 /**
 * Кость скелета, поддержка parent-child, индекса, локальной/глобальной TRS матрицы.
 */
/**
  * Представление кости FBX с поддержкой parent-child и локальной матрицы.
  */
 class FBXBone {
     public String name;
     public String fbxId;
     public FBXBone parent;
     public final List<FBXBone> children = new ArrayList<>();
     public int parentIndex = -1;
     public float[] localTransform = new float[16]; // 4x4 матрица (column major)
     public float[] localTranslation = new float[3];
     public float[] localRotation = new float[4]; // кватернион
     public float[] localScale = new float[3];

     /**
      * Парсит кость из Model/LimbNode
      */
     public static FBXBone fromFBXNode(FBXNode node) {
         FBXBone bone = new FBXBone();
         // Пример строки: Model: 123456, "BoneName", "LimbNode"
         String header = node.getProperties().isEmpty() ? node.getName() : node.getProperties().get(0);
         String[] split = header.split(",");
         if (split.length > 1) {
             bone.fbxId = split[0].replaceAll("[^0-9]", "");
             bone.name = split[1].replaceAll("\"", "").trim();
         } else {
             bone.name = node.getName();
             bone.fbxId = "";
         }

         // Найти Properties70 (позиция, поворот, масштаб)
         FBXNode props = node.findChild("Properties70");
         if (props != null) {
             for (FBXNode prop : props.getChildren()) {
                 if (prop.getName().equals("P")) {
                     List<String> p = prop.getProperties();
                     if (p.size() >= 5) {
                         String key = p.get(0).replaceAll("\"", "").trim();
                         switch (key) {
                             case "Lcl Translation":
                                 bone.localTranslation = parseVec3(p);
                                 break;
                             case "Lcl Rotation":
                                 bone.localRotation = parseVec4FromEuler(p);
                                 break;
                             case "Lcl Scaling":
                                 bone.localScale = parseVec3(p);
                                 break;
                         }
                     }
                 }
             }
         }
         // Сформировать локальную матрицу (TRS, без учёта порядка)
         bone.localTransform = composeTransform(bone.localTranslation, bone.localRotation, bone.localScale);
         return bone;
     }

     private static float[] parseVec3(List<String> p) {
         float[] v = new float[3];
         try {
             v[0] = Float.parseFloat(p.get(4));
             v[1] = Float.parseFloat(p.get(5));
             v[2] = Float.parseFloat(p.get(6));
         } catch (Exception ignore) {}
         return v;
     }

     // Для MVP: преобразуем эйлеры в кватернион (по-хорошему — сделать нормальный math)
     private static float[] parseVec4FromEuler(List<String> p) {
         float[] v = new float[3];
         try {
             v[0] = (float)Math.toRadians(Double.parseDouble(p.get(4)));
             v[1] = (float)Math.toRadians(Double.parseDouble(p.get(5)));
             v[2] = (float)Math.toRadians(Double.parseDouble(p.get(6)));
         } catch (Exception ignore) {}
         return eulerToQuaternion(v[0], v[1], v[2]);
     }

     private static float[] eulerToQuaternion(float x, float y, float z) {
         // yaw (Y), pitch (X), roll (Z)
         float cy = (float)Math.cos(z * 0.5);
         float sy = (float)Math.sin(z * 0.5);
         float cp = (float)Math.cos(y * 0.5);
         float sp = (float)Math.sin(y * 0.5);
         float cr = (float)Math.cos(x * 0.5);
         float sr = (float)Math.sin(x * 0.5);

         float qw = cr * cp * cy + sr * sp * sy;
         float qx = sr * cp * cy - cr * sp * sy;
         float qy = cr * sp * cy + sr * cp * sy;
         float qz = cr * cp * sy - sr * sp * cy;
         return new float[]{qx, qy, qz, qw};
     }

     private static float[] composeTransform(float[] t, float[] q, float[] s) {
         // MVP: возвращаем только TRS как 4x4 матрицу (без parent)
         float[] m = identityMatrix4();
         // Установить scale
         m[0] = s != null ? s[0] : 1f;
         m[5] = s != null ? s[1] : 1f;
         m[10] = s != null ? s[2] : 1f;
         // Установить rotation (кватернион > матрица)
         float[] rot = (q != null && q.length == 4) ? quaternionToMatrix(q) : identityMatrix4();
         m = multiplyMatrix4(m, rot);
         // Установить translation
         m[12] = t != null ? t[0] : 0f;
         m[13] = t != null ? t[1] : 0f;
         m[14] = t != null ? t[2] : 0f;
         return m;
     }

     private static float[] identityMatrix4() {
         float[] m = new float[16];
         m[0] = m[5] = m[10] = m[15] = 1f;
         return m;
     }

     private static float[] quaternionToMatrix(float[] q) {
         float[] m = new float[16];
         float x = q[0], y = q[1], z = q[2], w = q[3];
         float xx = x * x, yy = y * y, zz = z * z;
         float xy = x * y, xz = x * z, yz = y * z;
         float wx = w * x, wy = w * y, wz = w * z;

         m[0] = 1.0f - 2.0f * (yy + zz);
         m[1] = 2.0f * (xy + wz);
         m[2] = 2.0f * (xz - wy);
         m[3] = 0.0f;

         m[4] = 2.0f * (xy - wz);
         m[5] = 1.0f - 2.0f * (xx + zz);
         m[6] = 2.0f * (yz + wx);
         m[7] = 0.0f;

         m[8] = 2.0f * (xz + wy);
         m[9] = 2.0f * (yz - wx);
         m[10] = 1.0f - 2.0f * (xx + yy);
         m[11] = 0.0f;

         m[12] = m[13] = m[14] = 0.0f;
         m[15] = 1.0f;
         return m;
     }

     private static float[] multiplyMatrix4(float[] a, float[] b) {
         float[] m = new float[16];
         for (int row = 0; row < 4; ++row) {
             for (int col = 0; col < 4; ++col) {
                 m[col + row * 4] = 0f;
                 for (int k = 0; k < 4; ++k) {
                     m[col + row * 4] += a[k + row * 4] * b[col + k * 4];
                 }
             }
         }
         return m;
     }
 }