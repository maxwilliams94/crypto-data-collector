package ms.maxwillia.cryptodata.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionTestUtils {

    public static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = findField(target.getClass(), fieldName);
            if (field == null) {
                throw new IllegalArgumentException(
                        "Could not find field '" + fieldName + "' on target [" + target + "]");
            }
            makeAccessible(field, target);
            field.set(target, value);
        }
        catch (IllegalAccessException ex) {
            throw new IllegalStateException(
                    "Unexpected reflection exception - " + ex.getClass().getName() + ": " + ex.getMessage());
        }
    }

    public static Method getMethod(Class<?> clazz, Object target, String methodName, Class<?>... paramTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            makeAccessible(method, target);
            return method;
        } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(
                    "Could not find method '" + methodName + "' on class [" + clazz.getName() + "]");
        }
    }

    public static Object invokeMethod(Object target, Method method, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Could not invoke method '" + method.getName() + "'", ex);
        }
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> searchType = clazz;
        while (Object.class != searchType && searchType != null) {
            Field[] fields = searchType.getDeclaredFields();
            for (Field field : fields) {
                if (name.equals(field.getName())) {
                    return field;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }

    private static void makeAccessible(Field field, Object target) {
        if ((!java.lang.reflect.Modifier.isPublic(field.getModifiers()) ||
                !java.lang.reflect.Modifier.isPublic(field.getDeclaringClass().getModifiers()) ||
                java.lang.reflect.Modifier.isFinal(field.getModifiers())) && !field.canAccess(target)) {
            field.setAccessible(true);
        }
    }

    private static void makeAccessible(Method method, Object target) {
        boolean isStatic = java.lang.reflect.Modifier.isStatic(method.getModifiers());
        boolean needsAccessibilityChange = (!java.lang.reflect.Modifier.isPublic(method.getModifiers()) ||
                !java.lang.reflect.Modifier.isPublic(method.getDeclaringClass().getModifiers()));

        if (needsAccessibilityChange && !method.canAccess(target)) {
            method.setAccessible(true);
        }
    }
}