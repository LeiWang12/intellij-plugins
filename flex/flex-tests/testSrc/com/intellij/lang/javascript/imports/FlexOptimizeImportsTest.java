package com.intellij.lang.javascript.imports;

import com.intellij.codeInsight.actions.OptimizeImportsAction;
import com.intellij.flex.FlexTestUtils;
import com.intellij.ide.DataManager;
import com.intellij.lang.javascript.JSTestOption;
import com.intellij.lang.javascript.JSTestOptions;
import com.intellij.lang.javascript.JSTestUtils;
import com.intellij.lang.javascript.formatter.ECMA4CodeStyleSettings;
import com.intellij.lang.javascript.formatter.JSCodeStyleSettings;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;

public class FlexOptimizeImportsTest extends CodeInsightFixtureTestCase<FlexModuleFixtureBuilder> {
  private static final String EXPECTED_RESULT_FILE_SUFFIX = "expected";
  private static final String INPUT_DATA_FILE_SUFFIX = "input";
  private static final String MXML_FILE_EXTENSION = "mxml";
  private static final String AS_FILE_EXTENSION = "as";

  @Override
  protected Class<FlexModuleFixtureBuilder> getModuleBuilderClass() {
    return FlexModuleFixtureBuilder.class;
  }

  @Override
  protected void setUp() throws Exception {
    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(FlexModuleFixtureBuilder.class, FlexModuleFixtureBuilderImpl.class);
    super.setUp();
    JSTestUtils.initJSIndexes(getProject());
    FlexTestUtils.setupFlexSdk(myModule, getTestName(false), getClass());
  }

  @Override
  protected String getBasePath() {
    return "/flex/flex-tests/testData/imports/optimize";
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsPositioning() throws Throwable {
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsPositioningWithComments() throws Throwable {
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  public void testImportsPositioningMxml1() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.foo.BaseAs", "com.foo.Smth");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  public void testImportsFqn1() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassA", "com.a.ClassB", "com.a.ClassC", "com.foo.Base3");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  public void testImportsFqn2() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassA", "com.b.ClassA", "com.c.ClassB", "com.c.ClassC", "com.d.ClassA");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  public void testImportsFqn3() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassA");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  public void testImportsFqn4() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassA", "com.a.ClassB", "com.b.ClassA", "com.c.ClassC");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsFqn5() throws Throwable {
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  public void testImportsFqn6() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassA", "com.b.ClassA");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  public void testImportsFqn7() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassA", "com.a.ClassB", "com.b.ClassB", "com.b.ClassC");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsInMxml1() throws Throwable {
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testRemoveUnusedAndSortImports() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.test.SamePackClass", "com.test.pack1.ClassName", "com.test.pack2.ClassName",
                                    "com.test.pack3.ClassName");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsInMxml2() throws Throwable {
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsInMxml3() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassB");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsInMxml4() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassA", "com.a.ClassB");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk})
  public void testImportsInMxml5() throws Throwable {
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk, JSTestOption.WithJsSupportLoader})
  public void testImportsInMxml6() throws Throwable {
    myFixture.addFileToProject("Included2.as", "import mx.rpc.AbstractService;");
    myFixture.addFileToProject("Included.as", "include \"Included2.as\"");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk, JSTestOption.WithJsSupportLoader})
  public void testImportsWithInclude1() throws Throwable {
    myFixture.addFileToProject("Included2.as", "var t : AbstractService;");
    myFixture.addFileToProject("Included.as", "include \"Included2.as\"");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk, JSTestOption.WithJsSupportLoader})
  public void testImportsWithInclude2() throws Throwable {
    myFixture.addFileToProject("Included2.as", "var t : AbstractService;");
    myFixture.addFileToProject("Included.as", "include \"Included2.as\"");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk, JSTestOption.WithJsSupportLoader})
  public void testImportsWithInclude3() throws Throwable {
    myFixture.addFileToProject("Included2.as", "var t : AbstractService;");
    myFixture.addFileToProject("Included.as", "include \"Included2.as\"");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexSdk, JSTestOption.WithJsSupportLoader})
  public void testNamesakeFqn() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.foo.Foo");
    JSTestUtils.addClassesToProject(myFixture, false, "com.bar.Foo");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testInlineComponents() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.Class1", "com.a.Class2", "com.a.Class3", "com.a.Class4");
    myFixture.addFileToProject("InlineComponents_2.as", "function zz() { var tt : Class4; }");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testImplicitImports1() throws Throwable {
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testImplicitImports2() throws Throwable {
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testImplicitImports3() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.foo.IFactory");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testAmbiguous1() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.test.UIComponent");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testAmbiguous2() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.bar.UIComponent");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testAmbiguous3() throws Throwable {
    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    XmlCodeStyleSettings xmlSettings = styleSettings.getCustomSettings(XmlCodeStyleSettings.class);
    int aroundCDataBefore = xmlSettings.XML_WHITE_SPACE_AROUND_CDATA;
    xmlSettings.XML_WHITE_SPACE_AROUND_CDATA = XmlCodeStyleSettings.WS_AROUND_CDATA_NEW_LINES;
    JSTestUtils.addClassesToProject(myFixture, true, "com.a.ClassB", "com.b.ClassB");
    runOptimizeAction(MXML_FILE_EXTENSION);
    xmlSettings.XML_WHITE_SPACE_AROUND_CDATA = aroundCDataBefore;
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testAmbiguous4() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.example.idea.Map", "zzz.Map");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testQualifiedConstructor() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.foo.MyClass", "com.foo.MyClass2", "com.foo.Ambiguous", "test.Ambiguous");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testCoverByStar() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.Base", "com.Other");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader, JSTestOption.WithFlexSdk})
  public void testNoSemicolons() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "foo.MyClass", "foo.MyClass2", "bar.MyClass3");
    final JSCodeStyleSettings codeStyleSettings =
      CodeStyleSettingsManager.getInstance(myFixture.getProject()).getCurrentSettings().getCustomSettings(ECMA4CodeStyleSettings.class);
    boolean b = codeStyleSettings.USE_SEMICOLON_AFTER_STATEMENT;
    codeStyleSettings.USE_SEMICOLON_AFTER_STATEMENT = false;
    try {
      runOptimizeAction(AS_FILE_EXTENSION);
    }
    finally {
      codeStyleSettings.USE_SEMICOLON_AFTER_STATEMENT = b;
    }
  }

  @JSTestOptions({JSTestOption.WithJsSupportLoader})
  public void testConditionalCompileBlock() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.Class1", "com.Class2");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexFacet, JSTestOption.WithGumboSdk})
  public void testDataGridColumn() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.example.flex.ui.utils.Icons");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  public void testNoReplacementForPartOfPackage() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.foo");
    runOptimizeAction(AS_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexFacet})
  public void testNoBlankLines() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.Foo", "com.Bar", "com.Zzz");
    runOptimizeAction(MXML_FILE_EXTENSION);
  }

  @JSTestOptions({JSTestOption.WithFlexFacet})
  public void testNoBlankLines2() throws Throwable {
    JSTestUtils.addClassesToProject(myFixture, true, "com.Foo", "com.Bar", "com.Zzz");
    final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    XmlCodeStyleSettings xmlSettings = styleSettings.getCustomSettings(XmlCodeStyleSettings.class);
    boolean b = xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA;
    try {
      xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA = true;
      runOptimizeAction(MXML_FILE_EXTENSION);
    }
    finally {
      xmlSettings.XML_KEEP_WHITE_SPACES_INSIDE_CDATA = b;
    }
  }

  static String getExpectedResultFilePath(final String dataSubpath, final String testName, final String fileExtension) {
    return getTestDataFilePath(dataSubpath, testName, EXPECTED_RESULT_FILE_SUFFIX);
  }

  private static String getTestDataFilePath(final String dataSubpath, final String testName, final String fileExtension) {
    return PathManager.getHomePath() + "/" + dataSubpath + "/" + testName + "." + fileExtension;
  }

  static String getInputDataFileName(final String testName, final String fileExtension) {
    return testName + "." + INPUT_DATA_FILE_SUFFIX + "." + fileExtension;
  }

  static String getExpectedResultFileName(final String testName, final String fileExtension) {
    return testName + "." + EXPECTED_RESULT_FILE_SUFFIX + "." + fileExtension;
  }

  private String getThisTestExpectedResultFileName(final String fileExtension) {
    return getExpectedResultFileName(getTestName(false), fileExtension);
  }

  private String getThisTestInputFileName(final String fileExtension) {
    return getInputDataFileName(getTestName(false), fileExtension);
  }

  private void runOptimizeAction(final String fileExtension) throws Throwable {
    myFixture.configureByFile(getThisTestInputFileName(fileExtension));
    OptimizeImportsAction.actionPerformedImpl(DataManager.getInstance().getDataContext(myFixture.getEditor().getContentComponent()));
    FileDocumentManager.getInstance().saveAllDocuments();
    myFixture.checkResultByFile(getThisTestExpectedResultFileName(fileExtension));
  }


}
