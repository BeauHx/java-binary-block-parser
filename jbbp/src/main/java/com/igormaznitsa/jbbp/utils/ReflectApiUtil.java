package com.igormaznitsa.jbbp.utils;

import java.lang.reflect.AccessibleObject;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Auxiliary class collects methods for work with reflection.
 * <b>It is internal auxiliary class! Its method will be changed, don't use them in your developments!</b>
 *
 * @since 1.4.0
 */
public final class ReflectApiUtil {
  /**
   * Inside auxiliary cache of privilege processors organized as a queue.
   */
  private static final Queue<PrivilegedProcessor> PROCESSORS_QUEUE = new ArrayBlockingQueue<>(32);

  private ReflectApiUtil() {
  }

  /**
   * Make accessible an accessible object (if it is not a null), AccessController.doPrivileged will be
   * called.
   *
   * @param obj an object to make accessible, it can be null.
   * @see AccessController#doPrivileged(java.security.PrivilegedAction)
   */
  public static <T extends AccessibleObject> T makeAccessible(final T obj) {
    if (obj != null && !obj.isAccessible()) {
      PrivilegedProcessor processor = PROCESSORS_QUEUE.poll();
      if (processor == null) {
        processor = new PrivilegedProcessor();
      }
      processor.setAccessibleObject(obj);
      AccessController.doPrivileged(processor);
      if (!PROCESSORS_QUEUE.offer(processor)) {
        throw new Error("Unexpected! can't queue a processor");
      }
    }
    return obj;
  }

  /**
   * Create class instance through default constructor call
   *
   * @param klazz class to be instantiated, must not be null
   * @param <T>   type of the class
   * @return instance of class, must not be null
   */
  public static <T> T newInstance(final Class<T> klazz) {
    try {
      return klazz.getConstructor().newInstance();
    } catch (Exception ex) {
      throw new Error(String.format("Can't create instance of %s for error %s", klazz.getCanonicalName(), ex.getMessage()), ex);
    }
  }

  /**
   * Find class for name and make an instance through call of the default constructor.
   *
   * @param className the class name to be instantiated, must not be null
   * @return new instance of the class, must not be null
   * @throws Error if it is impossible to build instance for an exception
   */
  public static Object newInstanceForClassName(final String className) {
    try {
      return newInstance(Class.forName(className));
    } catch (Exception ex) {
      throw new Error(String.format("Can't create instance of %s for error %s", className, ex.getMessage()), ex);
    }
  }

  /**
   * Inside auxiliary class to make makeAccessible as a privileged action.
   */
  private static final class PrivilegedProcessor implements PrivilegedAction<AccessibleObject> {

    private AccessibleObject theObject;

    public void setAccessibleObject(final AccessibleObject obj) {
      this.theObject = obj;
    }

    @Override
    public AccessibleObject run() {
      final AccessibleObject objectToProcess = this.theObject;
      this.theObject = null;
      if (objectToProcess != null) {
        objectToProcess.setAccessible(true);
      }
      return objectToProcess;
    }
  }


}
