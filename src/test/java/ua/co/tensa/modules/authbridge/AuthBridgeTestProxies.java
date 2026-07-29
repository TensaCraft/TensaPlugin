package ua.co.tensa.modules.authbridge;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

final class AuthBridgeTestProxies {
    private AuthBridgeTestProxies() {
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationRouter router) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            return router.invoke(method, args == null ? new Object[0] : args);
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) return null;
        if (returnType == Boolean.TYPE) return false;
        if (returnType == Integer.TYPE) return 0;
        if (returnType == Long.TYPE) return 0L;
        if (returnType == Double.TYPE) return 0D;
        if (returnType == Float.TYPE) return 0F;
        if (returnType == Short.TYPE) return (short) 0;
        if (returnType == Byte.TYPE) return (byte) 0;
        if (returnType == Character.TYPE) return '\0';
        if (returnType == Optional.class) return Optional.empty();
        return null;
    }

    @FunctionalInterface
    interface InvocationRouter {
        Object invoke(Method method, Object[] args) throws Throwable;
    }
}
