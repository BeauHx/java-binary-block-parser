/*
 * Copyright 2017 Igor Maznitsa.
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

package com.igormaznitsa.jbbp.model;

import com.igormaznitsa.jbbp.compiler.JBBPNamedFieldInfo;
import com.igormaznitsa.jbbp.utils.JBBPUtils;

/**
 * Describes a byte field.
 *
 * @since 1.0
 */
public final class JBBPFieldByte extends JBBPAbstractField implements JBBPNumericField {
  private static final long serialVersionUID = -446415543054607091L;
  /**
   * Inside value storage.
   */
  private final byte value;

  /**
   * The Constructor.
   *
   * @param name  a field name info, it can be null.
   * @param value the field value.
   */
  public JBBPFieldByte(final JBBPNamedFieldInfo name, final byte value) {
    super(name);
    this.value = value;
  }

  /**
   * Get the reversed bit representation of the value.
   *
   * @param value the value to be reversed
   * @return the reversed value
   */
  public static long reverseBits(final byte value) {
    return JBBPUtils.reverseBitsInByte(value);
  }

  @Override
  public double getAsDouble() {
    return this.value;
  }

  @Override
  public float getAsFloat() {
    return this.value;
  }

  @Override
  public int getAsInt() {
    return this.value;
  }

  @Override
  public long getAsLong() {
    return this.getAsInt();
  }

  @Override
  public boolean getAsBool() {
    return this.value != 0;
  }

  @Override
  public long getAsInvertedBitOrder() {
    return reverseBits(this.value);
  }

  @Override
  public String getTypeAsString() {
    return "byte";
  }

}
