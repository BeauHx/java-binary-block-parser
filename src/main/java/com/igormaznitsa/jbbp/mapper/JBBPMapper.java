/*
 * Copyright 2014 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.igormaznitsa.jbbp.mapper;

import com.igormaznitsa.jbbp.exceptions.JBBPMapperException;
import com.igormaznitsa.jbbp.io.JBBPBitOrder;
import com.igormaznitsa.jbbp.model.*;
import com.igormaznitsa.jbbp.utils.JBBPUtils;
import java.lang.reflect.*;

/**
 * The Class processes mapping of a parsed binary data to class fields. The
 * Class uses sun.misc.Unsafe which creates class instances without any
 * constructor call.
 *
 * @see sun.misc.Unsafe
 */
public class JBBPMapper {

  private static final sun.misc.Unsafe SUN_MISC_UNSAFE;

  static {
    try {
      final Field singleoneInstanceField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      singleoneInstanceField.setAccessible(true);
      SUN_MISC_UNSAFE = (sun.misc.Unsafe) singleoneInstanceField.get(null);
    }
    catch (IllegalAccessException e) {
      throw new Error("Can't get sun.misc.Unsafe for illegal access", e);
    }
    catch (IllegalArgumentException e) {
      throw new Error("Can't get sun.misc.Unsafe for wrong argument", e);
    }
    catch (NoSuchFieldException e) {
      throw new Error("Can't get sun.misc.Unsafe because it doesn't exist", e);
    }
    catch (SecurityException e) {
      throw new Error("Can't get sun.misc.Unsafe for security exception", e);
    }
  }

  /**
   * Create a class instance, map binary data of a structure for its path to its
   * fields and return the instance.
   *
   * @param <T> the mapping class type
   * @param root a parsed structure to be used as the root, must not be null
   * @param structPath the path of a structure inside of the root to be mapped
   * to the class, must not be null
   * @param mappingClass the mapping class, must not be null and must have the
   * default constructor
   * @return the created and mapped instance of the mapping class
   * @throws JBBPMapperException for any error
   */
  public static <T> T map(final JBBPFieldStruct root, final String structPath, final Class<T> mappingClass) {
    return map(root, structPath, mappingClass, null);
  }

  /**
   * Create a class instance, map binary data of a structure for its path to its
   * fields and return the instance.
   *
   * @param <T> the mapping class type
   * @param root a parsed structure to be used as the root, must not be null
   * @param structPath the path of a structure inside of the root to be mapped
   * to the class, must not be null
   * @param mappingClass the mapping class, must not be null and must have the
   * default constructor
   * @param customFieldProcessor a custom field processor to provide custom
   * values, it can be null if there is not any mapping field desires the
   * processor
   * @return the created and mapped instance of the mapping class
   * @throws JBBPMapperException for any error
   */
  public static <T> T map(final JBBPFieldStruct root, final String structPath, final Class<T> mappingClass, final JBBPMapperCustomFieldProcessor customFieldProcessor) {
    JBBPUtils.assertNotNull(structPath, "Path must not be null");
    final JBBPFieldStruct struct = root.findFieldForPathAndType(structPath, JBBPFieldStruct.class);
    if (struct == null) {
      throw new JBBPMapperException("Can't find a structure field for its path [" + structPath + ']', null, mappingClass, null, null);
    }
    return map(struct, mappingClass, customFieldProcessor);
  }

  /**
   * Create a class instance, map binary data of a structure to the instance and
   * return it. It will create a class instance through a hack method and its
   * constructor will not be called, thus use the method carefully.
   *
   * @param <T> the mapping class type
   * @param root a parsed structure to be mapped to the class instance, must not
   * be null
   * @param mappingClass the class to be instantiated and mapped, must not be
   * null
   * @return the created and mapped instance of the class
   * @throws JBBPMapperException for any error
   */
  public static <T> T map(final JBBPFieldStruct root, final Class<T> mappingClass) {
    return mappingClass.cast(map(root, makeInstanceOfClass(root, mappingClass), null));
  }

  /**
   * Create a class instance, map binary data of a structure to the instance and
   * return it. It will create a class instance through a hack method and its
   * constructor will not be called, thus use the method carefully.
   *
   * @param <T> the mapping class type
   * @param root a parsed structure to be mapped to the class instance, must not
   * be null
   * @param mappingClass the class to be instantiated and mapped, must not be
   * null
   * @param customFieldProcessor a custom field processor to provide custom
   * values, it can be null if there is not any mapping field desires the
   * processor
   * @return the created and mapped instance of the class
   * @throws JBBPMapperException for any error
   */
  public static <T> T map(final JBBPFieldStruct root, final Class<T> mappingClass, final JBBPMapperCustomFieldProcessor customFieldProcessor) {
    return mappingClass.cast(map(root, makeInstanceOfClass(root, mappingClass), customFieldProcessor));
  }

  /**
   * Map a structure to a class instance.
   *
   * @param rootStructure a structure to be mapped, must not be null
   * @param mappingClassInstance a class instance to be destination for map
   * operations, must not be null
   * @param customFieldProcessor a custom field processor to provide custom
   * values, it can be null if there is not any mapping field desires the
   * processor
   * @return the processed class instance, the same which was the argument for
   * the method.
   * @throws JBBPMapperException for any error
   */
  public static Object map(final JBBPFieldStruct rootStructure, final Object mappingClassInstance, final JBBPMapperCustomFieldProcessor customFieldProcessor) {
    JBBPUtils.assertNotNull(rootStructure, "The Root structure must not be null");
    JBBPUtils.assertNotNull(mappingClassInstance, "The Mapping class instance must not be null");

    final Class<?> mappingClass = mappingClassInstance.getClass();

    for (final Field mappingField : mappingClassInstance.getClass().getDeclaredFields()) {
      mappingField.setAccessible(true);

      final Bin mappedAnno = mappingField.getAnnotation(Bin.class);
      if (mappedAnno == null) {
        continue;
      }

      if (mappedAnno.custom()) {
        JBBPUtils.assertNotNull(customFieldProcessor, "There is a custom mapping field, in the case you must provide a custom mapping field processor");
        final Object value = customFieldProcessor.prepareObjectForMapping(rootStructure, mappedAnno, mappingField);
        setNonStaticFieldValue(mappingClassInstance, mappingField, null, value);
      }
      else {
        final BinType fieldType;
        if (mappedAnno.type() == BinType.UNDEFINED) {
          fieldType = BinType.findCompatible(mappingField.getType());
          if (fieldType == null) {
            throw new JBBPMapperException("Can't find compatible type for a mapping field", rootStructure, mappingClass, mappingField, null);
          }
        }
        else {
          fieldType = mappedAnno.type();
        }

        final String fieldName = mappedAnno.name().length() == 0 ? mappingField.getName() : mappedAnno.name();
        final String fieldPath = mappedAnno.path();

        final JBBPAbstractField binField;

        if (fieldPath.length() == 0) {
          binField = fieldName.length() == 0 ? rootStructure.findFieldForType(fieldType.getFieldClass()) : rootStructure.findFieldForNameAndType(fieldName, fieldType.getFieldClass());
        }
        else {
          binField = rootStructure.findFieldForNameAndType(fieldPath, fieldType.getFieldClass());
        }

        if (binField == null) {
          throw new JBBPMapperException("Can't find value to be mapped to a mapping field", null, mappingClass, mappingField, null);
        }

        if (mappingField.getType().isArray()) {
          if (binField instanceof JBBPAbstractArrayField) {
            if (binField instanceof JBBPFieldArrayStruct) {
              // structure

              final JBBPFieldArrayStruct structArray = (JBBPFieldArrayStruct) binField;
              final Class<?> componentType = mappingField.getType().getComponentType();
              final Object valueArray = Array.newInstance(componentType, structArray.size());
              for (int i = 0; i < structArray.size(); i++) {
                Array.set(valueArray, i, map(structArray.getElementAt(i), componentType));
              }
              setNonStaticFieldValue(mappingClassInstance, mappingField, binField, valueArray);
            }
            else {
              // primitive
              mapArrayField(mappingClassInstance, mappingField, (JBBPAbstractArrayField<?>) binField, mappedAnno.bitOrder() == JBBPBitOrder.MSB0);
            }
          }
          else {
            throw new JBBPMapperException("Can't map a non-array value to an array mapping field", binField, mappingClass, mappingField, null);
          }
        }
        else {
          if (binField instanceof JBBPNumericField) {
            mapNumericField(mappingClassInstance, mappingField, (JBBPNumericField) binField, mappedAnno.bitOrder() == JBBPBitOrder.MSB0);
          }
          else if (binField instanceof JBBPFieldStruct) {
            if (mappingField.getType().isPrimitive()) {
              throw new JBBPMapperException("Can't map a structure to a primitive mapping field", binField, mappingClass, mappingField, null);
            }
            else {
              setNonStaticFieldValue(mappingClassInstance, mappingField, binField, map((JBBPFieldStruct) binField, mappingField.getType()));
            }
          }
          else {
            boolean processed = false;
            if (mappingField.getType() == String.class && binField instanceof JBBPAbstractArrayField) {
              final String convertedValue = convertFieldValueToString((JBBPAbstractArrayField<?>) binField);
              if (convertedValue != null) {
                setNonStaticFieldValue(mappingClassInstance, mappingField, binField, convertedValue);
                processed = true;
              }
            }
            if (!processed) {
              throw new JBBPMapperException("Can't map a field for its value incompatibility", binField, mappingClass, mappingField, null);
            }
          }
        }
      }
    }

    return mappingClassInstance;
  }

  private static String convertFieldValueToString(final JBBPAbstractArrayField<?> field) {
    final StringBuilder result;
    if (field instanceof JBBPFieldArrayBit) {
      final JBBPFieldArrayBit array = (JBBPFieldArrayBit) field;
      result = new StringBuilder(array.size());
      for (final byte b : array.getArray()) {
        result.append((char) b);
      }
    }
    else if (field instanceof JBBPFieldArrayByte) {
      final JBBPFieldArrayByte array = (JBBPFieldArrayByte) field;
      result = new StringBuilder(array.size());
      for (final byte b : array.getArray()) {
        result.append((char) (b & 0xFF));
      }
    }
    else if (field instanceof JBBPFieldArrayUByte) {
      final JBBPFieldArrayUByte array = (JBBPFieldArrayUByte) field;
      result = new StringBuilder(array.size());
      for (final byte b : array.getArray()) {
        result.append((char) (b & 0xFF));
      }
    }
    else if (field instanceof JBBPFieldArrayShort) {
      final JBBPFieldArrayShort array = (JBBPFieldArrayShort) field;
      result = new StringBuilder(array.size());
      for (final short b : array.getArray()) {
        result.append((char) b);
      }
    }
    else if (field instanceof JBBPFieldArrayUShort) {
      final JBBPFieldArrayUShort array = (JBBPFieldArrayUShort) field;
      result = new StringBuilder(array.size());
      for (final short b : array.getArray()) {
        result.append((char) b);
      }
    }
    else {
      result = null;
    }
    return result == null ? null : result.toString();
  }

  /**
   * Set a value to a field of a class instance. Can't be used for static
   * fields!
   *
   * @param classInstance a class instance, must not be null
   * @param classField a mapping class field which should be set by the value,
   * must not be null
   * @param binField a parsed bin field which value will be set, can be null
   * @param value a value to be set to the class field
   */
  private static void setNonStaticFieldValue(final Object classInstance, final Field classField, final JBBPAbstractField binField, final Object value) {
    try {
      classField.set(classInstance, value);
    }
    catch (IllegalArgumentException ex) {
      throw new JBBPMapperException("Can't set value to a mapping field", binField, classInstance.getClass(), classField, ex);
    }
    catch (IllegalAccessException ex) {
      throw new JBBPMapperException("Can't get access to a mapping field", binField, classInstance.getClass(), classField, ex);
    }
  }

  /**
   * Map a parsed primitive numeric field to a primitive field in a mapping
   * class.
   *
   * @param mappingClassInstance the mapping class instance, must not be null
   * @param mappingField a mapping field to set the value, must not be null
   * @param numericField a parsed numeric field which value should be used, must
   * not be null
   * @param invertBitOrder flag shows that the parsed numeric field value must
   * be reversed in its bit before setting
   */
  private static void mapNumericField(final Object mappingClassInstance, final Field mappingField, final JBBPNumericField numericField, final boolean invertBitOrder) {
    final Class<?> fieldClass = mappingField.getType();
    try {
      if (fieldClass == byte.class) {
        mappingField.setByte(mappingClassInstance, (byte) (invertBitOrder ? numericField.getAsInvertedBitOrder() : numericField.getAsInt()));
      }
      else if (fieldClass == char.class) {
        mappingField.setChar(mappingClassInstance, (char) (invertBitOrder ? numericField.getAsInvertedBitOrder() : numericField.getAsInt()));
      }
      else if (fieldClass == boolean.class) {
        mappingField.setBoolean(mappingClassInstance, numericField.getAsBool());
      }
      else if (fieldClass == short.class) {
        mappingField.setShort(mappingClassInstance, (short) (invertBitOrder ? numericField.getAsInvertedBitOrder() : numericField.getAsInt()));
      }
      else if (fieldClass == int.class) {
        mappingField.setInt(mappingClassInstance, (int) (invertBitOrder ? numericField.getAsInvertedBitOrder() : numericField.getAsInt()));
      }
      else if (fieldClass == float.class) {
        mappingField.setFloat(mappingClassInstance, (float) (invertBitOrder ? numericField.getAsInvertedBitOrder() : numericField.getAsInt()));
      }
      else if (fieldClass == long.class) {
        mappingField.setLong(mappingClassInstance, (invertBitOrder ? numericField.getAsInvertedBitOrder() : numericField.getAsLong()));
      }
      else if (fieldClass == double.class) {
        mappingField.setDouble(mappingClassInstance, (double) (invertBitOrder ? numericField.getAsInvertedBitOrder() : numericField.getAsLong()));
      }
      else {
        throw new JBBPMapperException("Unsupported mapping class field type to be mapped for binary parsed data", (JBBPAbstractField) numericField, mappingClassInstance.getClass(), mappingField, null);
      }
    }
    catch (IllegalAccessException ex) {
      throw new JBBPMapperException("Can't get access to a mapping field", (JBBPAbstractField) numericField, mappingClassInstance.getClass(), mappingField, ex);
    }
    catch (IllegalArgumentException ex) {
      throw new JBBPMapperException("Can't set argument to a mapping field", (JBBPAbstractField) numericField, mappingClassInstance.getClass(), mappingField, ex);
    }
  }

  /**
   * Map a parsed array to an array field in mapping class.
   *
   * @param mappingClassInstance a mapping class instance, must not be null
   * @param mappingField a field in the mapping class to be set, must not be
   * null
   * @param arrayField a binary parsed array field, must not be null
   * @param invertBitOrder flag shows that values of an array must be bit
   * reversed before set
   */
  private static void mapArrayField(final Object mappingClassInstance, final Field mappingField, final JBBPAbstractArrayField<?> arrayField, final boolean invertBitOrder) {
    try {
      if (arrayField instanceof JBBPFieldArrayUShort && mappingField.getType().getComponentType() == char.class) {
        final short[] shortarray = (short[]) arrayField.getValueArrayAsObject(invertBitOrder);
        final char[] chararray = new char[shortarray.length];
        for (int i = 0; i < shortarray.length; i++) {
          chararray[i] = (char) shortarray[i];
        }
        mappingField.set(mappingClassInstance, chararray);
      }
      else {
        mappingField.set(mappingClassInstance, arrayField.getValueArrayAsObject(invertBitOrder));
      }
    }
    catch (IllegalAccessException ex) {
      throw new JBBPMapperException("Can't get access to a mapping field", arrayField, mappingClassInstance.getClass(), mappingField, ex);
    }
    catch (IllegalArgumentException ex) {
      throw new JBBPMapperException("Can't set argument to a mapping field", arrayField, mappingClassInstance.getClass(), mappingField, ex);
    }
  }

  private static <T> T makeInstanceOfClass(final JBBPFieldStruct root, final Class<T> klazz) {
    try {
      return klazz.cast(SUN_MISC_UNSAFE.allocateInstance(klazz));
    }
    catch (InstantiationException ex) {
      throw new JBBPMapperException("Can't make an instance of a class", root, klazz, null, ex);
    }
  }
}