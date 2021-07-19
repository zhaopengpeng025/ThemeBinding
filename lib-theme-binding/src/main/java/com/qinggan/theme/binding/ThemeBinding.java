package com.qinggan.theme.binding;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.qinggan.theme.annotation.Unbinder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;

public class ThemeBinding {
  private static final String TAG = "ThemeBinding";
  private static boolean debug = false;

  /**
   * Control whether debug logging is enabled.
   */
  public static void setDebug(boolean debug) {
    ThemeBinding.debug = debug;
  }

  private static final Map<Class<?>, Constructor<? extends Unbinder>> BINDINGS =
          new LinkedHashMap<>();
  private static final Map<String, Unbinder> UNBINDERS = new LinkedHashMap<>();

  public static void bind(Activity activity) {
    bind(activity, activity);
  }

  public static void bind(Fragment fragment) {
    bind(fragment, fragment.getContext());
  }

  public static void bind(View view) {
    bind(view, view.getContext());
  }

  public static void bind(Object target, Context context) {
    Class<?> targetClass = target.getClass();
    if (debug)
      Log.d(TAG, "Looking up binding for " + targetClass.getName());
    Constructor<? extends Unbinder> constructor = findBindingConstructorForClass(targetClass);
    //noinspection TryWithIdenticalCatches Resolves to API 19+ only type.
    try {
      Unbinder unbinder = constructor.newInstance(target, context);
      UNBINDERS.put(target.toString(), unbinder);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Unable to invoke " + constructor, e);
    } catch (InstantiationException e) {
      throw new RuntimeException("Unable to invoke " + constructor, e);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      throw new RuntimeException("Unable to create binding instance.", cause);
    }
  }

  private static Constructor<? extends Unbinder> findBindingConstructorForClass(Class<?> cls) {
    Constructor<? extends Unbinder> bindingCtor = BINDINGS.get(cls);
    if (bindingCtor != null || BINDINGS.containsKey(cls)) {
      if (debug)
        Log.d(TAG, "HIT: Cached in binding map.");
      return bindingCtor;
    }
    String clsName = cls.getName();
    if (clsName.startsWith("android.") || clsName.startsWith("java.")
            || clsName.startsWith("androidx.")) {
      if (debug)
        Log.d(TAG, "MISS: Reached framework class. Abandoning search.");
      return null;
    }
    try {
      if (clsName.contains("$")) {
        String packageStr = clsName.substring(0, clsName.lastIndexOf("."));
        String innerName = clsName.substring(clsName.indexOf("$") + 1);
        clsName = (packageStr + "." + innerName);
      }
      Class<?> bindingClass = cls.getClassLoader().loadClass(clsName + "_ThemeBinding");
      //noinspection unchecked
      bindingCtor = (Constructor<? extends Unbinder>) bindingClass.getConstructor(cls,
                                                                                  Context.class);
      if (debug)
        Log.d(TAG, "HIT: Loaded binding class and constructor.");
    } catch (ClassNotFoundException e) {
      if (debug)
        Log.d(TAG, "Not found. Trying superclass " + cls.getSuperclass().getName());
      bindingCtor = findBindingConstructorForClass(cls.getSuperclass());
    } catch (NoSuchMethodException e) {
      throw new RuntimeException("Unable to find binding constructor for " + clsName, e);
    }
    BINDINGS.put(cls, bindingCtor);
    return bindingCtor;
  }

  public static void unbind(Object target) {
    if (UNBINDERS.containsKey(target.toString())) {
      UNBINDERS.get(target.toString()).unbind();
      UNBINDERS.remove(target.toString());
    }
  }
}
