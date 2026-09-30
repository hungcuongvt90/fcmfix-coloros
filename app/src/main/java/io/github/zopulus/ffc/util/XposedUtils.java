package io.github.zopulus.ffc.util;

import java.lang.reflect.Method;

<<<<<<< HEAD:app/src/main/java/com/kooritea/fcmfix/util/XposedUtils.java
=======
import io.github.zopulus.ffc.libxposed.XC_MethodHook;
import io.github.zopulus.ffc.libxposed.XposedBridge;
import io.github.zopulus.ffc.libxposed.XposedHelpers;

>>>>>>> 53450a0 (Apply ColorOS17 customization and MD3E UI):app/src/main/java/io/github/zopulus/ffc/util/XposedUtils.java
public class XposedUtils {

    public static Method findMethod(Class<?> clazz, String methodName, int parameterCount) {
        Method method = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterTypes().length == parameterCount) {
                method = m;
            }
        }
        return method;
    }
}
