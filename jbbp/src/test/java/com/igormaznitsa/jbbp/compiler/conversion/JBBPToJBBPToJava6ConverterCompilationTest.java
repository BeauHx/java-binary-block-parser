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

package com.igormaznitsa.jbbp.compiler.conversion;

import com.igormaznitsa.jbbp.JBBPCustomFieldTypeProcessor;
import com.igormaznitsa.jbbp.JBBPParser;
import com.igormaznitsa.jbbp.compiler.JBBPNamedFieldInfo;
import com.igormaznitsa.jbbp.compiler.tokenizer.JBBPFieldTypeParameterContainer;
import com.igormaznitsa.jbbp.io.JBBPBitInputStream;
import com.igormaznitsa.jbbp.io.JBBPBitOrder;
import com.igormaznitsa.jbbp.model.JBBPAbstractField;
import com.igormaznitsa.jbbp.testaux.AbstractJBBPToJava6ConverterTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class JBBPToJBBPToJava6ConverterCompilationTest extends AbstractJBBPToJava6ConverterTest {

  private static String makeSources(final JBBPParser parser, final String classComment, final boolean useSetterGetter) {
    return JBBPToJava6Converter.makeBuilder(parser).setMainClassPackage(PACKAGE_NAME).setMainClassName(CLASS_NAME).setHeadComment(classComment).setAddGettersSetters(useSetterGetter).build().convert();
  }

  private void assertCompilation(final String classSrc) throws Exception {
    System.out.println(classSrc);
    assertNotNull(saveAndCompile(new JavaClassContent(PACKAGE_NAME + '.' + CLASS_NAME, classSrc)));
  }

  @Test
  public void testVarNamesAsJavaTypes() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("ubyte;int integer; int number; int try; int byte; int _byte; int _byte_; int char; int short; int long; int double; int float; int [long+double+char] string;");
    assertCompilation(makeSources(parser, "some multiline text\nto be added into header", true));
  }

  @Test
  public void testExpression() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("bit:8 bitf; var somevar; bool bbb; long aaa; ubyte kkk; {{int lrn; {int [(lrn/aaa*1*(2*somevar-4)&$joomla)/(100%9>>bitf)&56|~kkk^78&bbb];}}}");
    assertCompilation(makeSources(parser, "some multiline text\nto be added into header", false));
    assertCompilation(makeSources(parser, "some multiline text\nto be added into header", true));
  }

  @Test
  public void testForceAbstract() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("byte a;");
    assertFalse(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).setDoMainClassAbstract(false).build().convert().contains("abstract"));
    assertTrue(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).setDoMainClassAbstract(true).build().convert().contains("abstract"));
  }

  @Test
  public void testMakeInternalClassObjects_StaticClasses() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("a { b { c [_] { byte d;}} }");
    final String text = JBBPToJava6Converter
        .makeBuilder(parser)
        .setMainClassName(CLASS_NAME)
        .setAddGettersSetters(true)
        .build()
        .convert();
    assertTrue(text.contains("public A makeA(){ this.a = new A(this); return this.a; }"));
    assertTrue(text.contains("public B makeB(){ this.b = new B(_Root_); return this.b; }"));
    assertTrue(text.contains("public C[] makeC(int _Len_){ this.c = new C[_Len_]; for(int i=0;i < _Len_;i++) this.c[i]=new C(_Root_); return this.c; }"));
  }

  @Test
  public void testMakeInternalClassObjects_NoMakersWithoutGettersSetters() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("a { b { c [_] { byte d;}} }");
    final String text = JBBPToJava6Converter
        .makeBuilder(parser)
        .setMainClassName(CLASS_NAME)
        .setAddGettersSetters(false)
        .build()
        .convert();
    assertFalse(text.contains("public A makeA(){ this.a = new A(this); return this.a; }"));
    assertFalse(text.contains("public B makeB(){ this.b = new B(_Root_); return this.b; }"));
    assertFalse(text.contains("public C[] makeC(int _Len_){ this.c = new C[_Len_]; for(int i=0;i < _Len_;i++) this.c[i]=new C(_Root_); return this.c; }"));
  }

  @Test
  public void testMakeInternalClassObjects_NonStaticClasses() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("a { b { c [_] { byte d;}} }");
    final String text = JBBPToJava6Converter
        .makeBuilder(parser)
        .setMainClassName(CLASS_NAME)
        .setAddGettersSetters(true)
        .doInternalClassesNonStatic()
        .build()
        .convert();
    assertTrue(text.contains("public A makeA(){ this.a = new A(this); return this.a; }"));
    assertTrue(text.contains("public B makeB(){ this.b = new B(_Root_); return this.b; }"));
    assertTrue(text.contains("public C[] makeC(int _Len_){ this.c = new C[_Len_]; for(int i=0;i < _Len_;i++) this.c[i]=new C(_Root_); return this.c; }"));
  }

  @Test
  public void testMapSubstructToSuperclassesInterface() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("a { b { c [_] { byte d;}} }");
    final String text = JBBPToJava6Converter.makeBuilder(parser)
        .setMainClassName(CLASS_NAME)
        .setAddGettersSetters(true)
        .setMapSubClassesSuperclasses(
            makeMap(
                "a.b", "com.igormaznitsa.Impl",
                "a.b.c", "com.igormaznitsa.Impl2"
            )
        )
        .build()
        .convert();
    assertTrue(text.contains("public static class B extends com.igormaznitsa.Impl"));
    assertTrue(text.contains("public static class C extends com.igormaznitsa.Impl2"));
    assertTrue(text.contains("public B getB() { return this.b;}"));
    assertTrue(text.contains("public C [] getC() { return this.c;}"));
    System.out.println(text);
  }

  @Test
  public void testMapSubstructToInterface() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("a { b { c [_] { byte d;}} }");
    final String text = JBBPToJava6Converter.makeBuilder(parser)
        .setMainClassName(CLASS_NAME)
        .setAddGettersSetters(true)
        .setMapSubClassesInterfaces(
            makeMap(
                "a.b", "com.igormaznitsa.Impl",
                "a.b.c", "com.igormaznitsa.Impl2"
            )
        )
        .build()
        .convert();
    assertTrue(text.contains("public static class B implements com.igormaznitsa.Impl"));
    assertTrue(text.contains("public static class C implements com.igormaznitsa.Impl2"));
    assertTrue(text.contains("public com.igormaznitsa.Impl getB() { return this.b;}"));
    assertTrue(text.contains("public com.igormaznitsa.Impl2 [] getC() { return this.c;}"));
    System.out.println(text);
  }

  @Test
  public void testCustomText() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("byte a;");
    assertTrue(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).setMainClassCustomText("public void test(){}").build().convert().contains("public void test(){}"));
  }

  @Test
  public void testSuperclass() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("byte a;");
    assertTrue(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).setSuperClass("com.igormaznitsa.Super").build().convert().contains("extends com.igormaznitsa.Super "));
  }

  @Test
  public void testInterfaces() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("byte a;");
    assertTrue(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).setMainClassImplements("com.igormaznitsa.InterfaceA", "com.igormaznitsa.InterfaceB").build().convert().contains("implements com.igormaznitsa.InterfaceA,com.igormaznitsa.InterfaceB "));
  }

  @Test
  public void testClassPackage() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("byte a;");
    assertFalse(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).build().convert().contains("package "));
    assertFalse(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).setMainClassPackage("").build().convert().contains("package "));
    assertTrue(JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).setMainClassPackage("hello.world").build().convert().contains("package hello.world;"));
  }

  @Test
  public void testGettersSetters() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("byte a;");
    String text = JBBPToJava6Converter.makeBuilder(parser).setMainClassName(CLASS_NAME).build().convert();
    assertTrue(text.contains("public byte a;"));
    assertFalse(text.contains("public void setA(byte value) {"));
    assertFalse(text.contains("public byte getA() {"));

    text = JBBPToJava6Converter.makeBuilder(parser).setAddGettersSetters(true).setMainClassName(CLASS_NAME).build().convert();

    assertFalse(text.contains("public byte a;"));
    assertTrue(text.contains("protected byte a;"));
    assertTrue(text.contains("public void setA(byte value) {"));
    assertTrue(text.contains("public byte getA() {"));
  }

  @Test
  public void testZ80snap1() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("byte reg_a; byte reg_f; <short reg_bc; <short reg_hl; <short reg_pc; <short reg_sp; byte reg_ir; byte reg_r; "
        + "flags{ bit:1 reg_r_bit7; bit:3 bordercolor; bit:1 basic_samrom; bit:1 compressed; bit:2 nomeaning;}"
        + "<short reg_de; <short reg_bc_alt; <short reg_de_alt; <short reg_hl_alt; byte reg_a_alt; byte reg_f_alt; <short reg_iy; <short reg_ix; byte iff; byte iff2;"
        + "emulFlags{bit:2 interruptmode; bit:1 issue2emulation; bit:1 doubleintfreq; bit:2 videosync; bit:2 inputdevice;}"
        + "byte [_] data;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testSinglePrimitiveNamedFields() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("bit a;byte b;ubyte c;short d;ushort e;bool f;int g;long h;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testSinglePrimitiveAnonymousFields() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("bit;byte;ubyte;short;ushort;bool;int;long;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testSinglePrimitiveAnonymousArrayFields() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("bit[1];byte[2];ubyte[3];short[4];ushort[5];bool[6];int[7];long[8];");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testActions() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("reset$$;skip:8;align:22;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testStruct() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("int;{byte;ubyte;{long;}}");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testPrimitiveArrayInsideStructArray() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("ubyte len; {ubyte[len];} ubyte [_] rest;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testExternalValueInExpression() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("ubyte len; <int [len*2+$ex] hello;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testCustomType() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("some alpha; some:1 beta;", new JBBPCustomFieldTypeProcessor() {
      @Override
      public String[] getCustomFieldTypes() {
        return new String[] {"some"};
      }

      @Override
      public boolean isAllowed(JBBPFieldTypeParameterContainer fieldType, String fieldName, int extraData, boolean isArray) {
        return true;
      }

      @Override
      public JBBPAbstractField readCustomFieldType(JBBPBitInputStream in, JBBPBitOrder bitOrder, int parserFlags, JBBPFieldTypeParameterContainer customTypeFieldInfo, JBBPNamedFieldInfo fieldName, int extraData, boolean readWholeStream, int arrayLength) throws IOException {
        return null;
      }
    });

    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testVarType() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("var alpha; var [$$] beta;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testAllVariantsWithLinksToExternalStructures() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("bit:1 bit1; bit:2 bit2; bit:3 bit3; bit:4 bit4; bit:5 bit5; bit:6 bit6; bit:7 bit7; bit:8 bit8;"
        + "byte alpha; ubyte beta; short gamma; ushort delta; bool epsilon; int teta; long longField; var varField;"
        + "floatj flt1; doublej dbl1;"
        + "struct1 { byte someByte; struct2 {bit:3 [34*someByte<<1+$ext] data;} }");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testValFields() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("ubyte a; ubyte b; val:(a+b*2) v; byte [v] data;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testStringFields() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("stringj str; stringj [5] strarr; stringj [_] all;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testStringFieldAsLength_CompilationErrorForStringFieldInArithmeticException() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("stringj str; stringj [str] strarr; stringj [_] all;");
    assertThrows(Exception.class, () -> assertCompilation(makeSources(parser, null, false)));
    assertThrows(Exception.class, () -> assertCompilation(makeSources(parser, null, true)));
  }

  @Test
  public void testStringFieldInExpression_CompilationErrorForStringFieldInArithmeticException() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("stringj str; byte a; stringj [str+a] strarr; stringj [_] all;");
    assertThrows(Exception.class, () -> assertCompilation(makeSources(parser, null, false)));
    assertThrows(Exception.class, () -> assertCompilation(makeSources(parser, null, true)));
  }

  @Test
  public void testPngParsing() throws Exception {
    final JBBPParser parser = JBBPParser.prepare(
        "long header;"
            + "// chunks\n"
            + "chunk [_]{"
            + "   int length; "
            + "   int type; "
            + "   byte[length] data; "
            + "   int crc;"
            + "}"
    );
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testPrimitiveFieldsInExpression() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("long lfield; int ifield; byte bfield; ggg {ubyte ubfield; short shfield;} ushort ushfield; bit:4 bitfield; byte [bfield*ggg.shfield<<bitfield-ggg.ubfield&ushfield%lfield/ifield] array;");
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testAllTypes() throws Exception {
    final JBBPParser parser = JBBPParser.prepare("custom alpha; custom [123] beta; {{ var [alpha*$extr] variarr; var fld;}}", new JBBPCustomFieldTypeProcessor() {
      @Override
      public String[] getCustomFieldTypes() {
        return new String[] {"custom"};
      }

      @Override
      public boolean isAllowed(JBBPFieldTypeParameterContainer fieldType, String fieldName, int extraData, boolean isArray) {
        return true;
      }

      @Override
      public JBBPAbstractField readCustomFieldType(JBBPBitInputStream in, JBBPBitOrder bitOrder, int parserFlags, JBBPFieldTypeParameterContainer customTypeFieldInfo, JBBPNamedFieldInfo fieldName, int extraData, boolean readWholeStream, int arrayLength) throws IOException {
        return null;
      }
    });
    assertCompilation(makeSources(parser, null, false));
    assertCompilation(makeSources(parser, null, true));
  }

  @Test
  public void testStaticInternalClasses() throws Exception {
    String text = JBBPToJava6Converter.makeBuilder(JBBPParser.prepare("somestruct {int a;}"))
        .setMainClassPackage(PACKAGE_NAME).setMainClassName(CLASS_NAME)
        .build().convert();
    assertTrue(text.contains(" static class SOMESTRUCT {"));

    text = JBBPToJava6Converter.makeBuilder(JBBPParser.prepare("somestruct {int a;}"))
        .setMainClassPackage(PACKAGE_NAME).setMainClassName(CLASS_NAME)
        .doInternalClassesNonStatic()
        .build().convert();
    assertFalse(text.contains(" static class SOMESTRUCT {"));
    assertTrue(text.contains(" class SOMESTRUCT {"));
  }


}
