package com.tcmj.pug.enums.api.fluent;

import com.tcmj.pug.enums.api.ClassBuilder;
import com.tcmj.pug.enums.api.DataProvider;
import com.tcmj.pug.enums.api.EnumExporter;
import com.tcmj.pug.enums.api.EnumResult;
import com.tcmj.pug.enums.api.NamingStrategy;
import com.tcmj.pug.enums.api.SourceFormatter;
import com.tcmj.pug.enums.model.EnumData;
import com.tcmj.pug.enums.model.NameTypeValue;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.camelStrict;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.extractParenthesis;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.flattenGermanUmlauts;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.harmonize;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.lowerCaseFirstLetter;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.minus2underline;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.removeDots;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.removeProhibitedSpecials;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.replaceAtoZ;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.space2underline;
import static com.tcmj.pug.enums.api.tools.NamingStrategyFactory.upperCase;

/**
 * Fluent API.
 */
public class Fluent {
  private static final Fluent INSTANCE = new Fluent();

  private DataProvider dataProvider;
  private ClassBuilder classBuilder;
  private SourceFormatter sourceFormatter;
  private EnumExporter enumExporter;

  private String[] fixedFieldNames;

  private NamingStrategy namingStrategyConstantNames;
  private NamingStrategy namingStrategyFieldNames;

  private Fluent() {
    //not allowed to be instantiated
  }

  public static Fluent builder() {
    return INSTANCE;
  }

  public Fluent dataProvider(DataProvider value) {
    dataProvider = Objects.requireNonNull(value, "DataProvider may not be null!");
    return INSTANCE;
  }

  public Fluent classBuilder(ClassBuilder value) {
    classBuilder = Objects.requireNonNull(value, "ClassBuilder may not be null!");
    return INSTANCE;
  }

  public Fluent sourceFormatter(SourceFormatter value) {
    sourceFormatter = Objects.requireNonNull(value, "SourceFormatter may not be null!");
    return INSTANCE;
  }

  public Fluent enumExporter(EnumExporter value) {
    enumExporter = Objects.requireNonNull(value, "EnumExporter may not be null!");
    return INSTANCE;
  }

  public Fluent useFixedFieldNames(String... overrideFieldNames) {
    this.fixedFieldNames = Objects.requireNonNull(overrideFieldNames, "Field names may not be null if you want to override them!");
    return INSTANCE;
  }

  public Fluent convertConstantNames(NamingStrategy ns) {
    this.namingStrategyConstantNames = Objects.requireNonNull(ns, "NamingStrategy for constant names may not be null!");
    return INSTANCE;
  }

  public Fluent usingDefaultConstantNameConversion() {
    this.namingStrategyConstantNames = getDefaultNamingStrategyConstantNames();
    return INSTANCE;
  }

  /**
   * Strategy how to convert the names of the fields (variables) - not the content!
   */
  public Fluent convertFieldNames(NamingStrategy ns) {
    this.namingStrategyFieldNames = Objects.requireNonNull(ns, "NamingStrategy for field names may not be null!");
    return INSTANCE;
  }

  public Fluent usingDefaultFieldNameConversion() {
    this.namingStrategyFieldNames = getDefaultNamingStrategyFieldNames();
    return INSTANCE;
  }

  public Fluent javaDocClassLevel(String... lines) {
    final ClassBuilder localClassBuilder = Objects.requireNonNull(getClassBuilder(), "classBuilder(ClassBuilder) must set before calling javaDocClassLevel(String...)!");
    Stream.of(lines).forEach(localClassBuilder::addClassJavadoc);
    return this;
  }

  /**
   * A default set of several chained NamingStrategy objects used for the enum names.
   */
  public static NamingStrategy getDefaultNamingStrategyConstantNames() {
    return minus2underline()
      .and(flattenGermanUmlauts())
      .and(space2underline())
      .and(replaceAtoZ())
      .and(removeProhibitedSpecials())
      .and(removeDots())
      .and(upperCase());
  }

  /**
   * A default set of several chained NamingStrategy objects used for the enum field names.
   */
  public static NamingStrategy getDefaultNamingStrategyFieldNames() {
    NamingStrategy ns1 = extractParenthesis();
    NamingStrategy ns2 = removeProhibitedSpecials();
    NamingStrategy ns3 = camelStrict();
    NamingStrategy ns4 = harmonize();
    NamingStrategy ns5 = lowerCaseFirstLetter();
    return ns1.and(ns2).and(ns3).and(ns4).and(ns5);
  }

  public EnumResult build() {
    Objects.requireNonNull(getDataProvider(), "DataProvider implementation necessary! E.g. URLHtmlDataProvider, CSVDataProvider,...");
    Objects.requireNonNull(getClassBuilder(), "ClassBuilder implementation necessary needed! You can easily use 'ClassBuilderFactory.getBestEnumBuilder()'!");
    Objects.requireNonNull(getEnumExporter(), "Please set a EnumExporter! You can try 'ReportingEnumExporter' or have a look at 'EnumExporterFactory'");

    final EnumData enumData =
      Objects.requireNonNull(getDataProvider().load(), "DataProvider.load() returns a null EnumData object!");

    //Exchange classname into the classbuilder and EnumData
    transferClassName(enumData);

    //Add Class-Level JavaDoc if available
    String jDocClassLevel = enumData.getJavaDoc(EnumData.JDocKeys.CLASS.name());
    if (jDocClassLevel != null && jDocClassLevel.length() > 0) {
      getClassBuilder().addClassJavadoc(jDocClassLevel);
    }

    if (this.fixedFieldNames != null) {
      //Overriding the field names usually fetched by the data provider implementation!
      enumData.setFieldNames(this.fixedFieldNames);
    }

    if (this.namingStrategyFieldNames != null) {
      getClassBuilder().convertFieldNames(Objects.requireNonNull(this.namingStrategyFieldNames, "NamingStrategy for field names is null!"));
    }

    if (this.namingStrategyConstantNames != null) {
      getClassBuilder().convertConstantNames(Objects.requireNonNull(this.namingStrategyConstantNames, "NamingStrategy for constant names is null!"));
    }

    //note that we use the fieldnames which are possibly been overriden!
    getClassBuilder().setFields(enumData.getFieldNames(), enumData.getFieldClasses());

    final List<NameTypeValue> mapData = Objects.requireNonNull(enumData.getData(), "EnumData has no records loaded! It's empty!");

    //add each data record to the classbuilder
    mapData.forEach((nameTypeValue) -> getClassBuilder().addField(nameTypeValue.getConstantName(), nameTypeValue.getValue()));

    String myEnum = getClassBuilder().build();

    EnumResult enumResult = EnumResult.of(enumData, getSourceFormatter(), myEnum);

    return getEnumExporter().export(enumResult);
  }

  /**
   * Little helper method to set the classname in all needed instances.
   */
  private void transferClassName(final EnumData enumData) {
    //if the classname has been set on the classbuilder object:
    if (enumData != null && enumData.getClassNameSimple() == null) {
      String cbCName = getClassBuilder().getModel().getClassName();
      if (cbCName != null && cbCName.length() > 0) {
        enumData.setClassName(getClassBuilder().getModel().getClassName());
      }
    }
    //if classbuilder hasn't the classname set (but the DataProvider#EnumModel)
    if (getClassBuilder().getModel() != null
      && getClassBuilder().getModel().getClassNameSimple() == null
      && enumData != null && enumData.getClassNameSimple() != null) {
      getClassBuilder().withName(enumData.getClassName());
    }

  }

  /**
   * The DataProvider which is used to load the enum data usually from html, json, cvs ...
   */
  public DataProvider getDataProvider() {
    return dataProvider;
  }

  /**
   * The ClassBuilder object is a code creating object optimized for enums.
   */
  public ClassBuilder getClassBuilder() {
    return classBuilder;
  }

  /**
   * Optional formatting object.
   */
  public SourceFormatter getSourceFormatter() {
    return sourceFormatter;
  }

  /**
   * The object to create files or report the enum at the log or something.
   */
  public EnumExporter getEnumExporter() {
    return enumExporter;
  }

}
