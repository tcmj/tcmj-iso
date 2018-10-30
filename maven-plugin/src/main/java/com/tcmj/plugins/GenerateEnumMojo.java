package com.tcmj.plugins;

import com.tcmj.pug.enums.api.ClassBuilder;
import com.tcmj.pug.enums.api.DataProvider;
import com.tcmj.pug.enums.api.EnumExporter;
import com.tcmj.pug.enums.api.EnumResult;
import com.tcmj.pug.enums.api.NamingStrategy;
import com.tcmj.pug.enums.api.SourceFormatter;
import com.tcmj.pug.enums.api.fluent.Fluent;
import com.tcmj.pug.enums.api.tools.NamingStrategyFactory;
import com.tcmj.pug.enums.builder.ClassBuilderFactory;
import com.tcmj.pug.enums.builder.SourceFormatterFactory;
import com.tcmj.pug.enums.datasources.impl.URLHtmlDataProvider;
import com.tcmj.pug.enums.exporter.impl.JavaSourceFileExporter;
import com.tcmj.pug.enums.model.EnumData;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.ReaderFactory;
import org.slf4j.impl.StaticLoggerBinder;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.tcmj.plugins.LogFormatter.arrange;
import static com.tcmj.plugins.LogFormatter.encloseJavaDoc;
import static com.tcmj.plugins.LogFormatter.getLine;

/**
 * Extract data from a URL and create a java enum source file.
 *
 * @since 2017
 */
@Mojo(name = "generate-enum", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class GenerateEnumMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project.build.sourceEncoding}", readonly = true)
  private String encoding;

  @Component
  private MavenProject project;

  /** Mandatory Property which data provider should be used. This class name should be created in the {@link #getDataProvider()} method. */
  @Parameter(property = "com.tcmj.pug.enums.dataprovider", defaultValue = "com.tcmj.pug.enums.datasources.impl.URLXPathHtmlDataProvider")
  private String dataProvider;

  /** Mandatory Property which defines the full class name (including packages). It defaults to 'com.tcmj.generated.MyEnum'. */
  @Parameter(property = "com.tcmj.pug.enums.classname", defaultValue = "com.tcmj.generated.MyEnum", required = true)
  private String className;

  /** Mandatory Property which defines the output path to save the generated enum java files. It defaults to mavens 'project.build.sourceDirectory'. */
  @Parameter(property = "com.tcmj.pug.enums.sourcedirectory", defaultValue = "${project.build.sourceDirectory}", required = true)
  private File sourceDirectory;

  /** Property which defines the path where the pom.xml is located. It defaults to mavens 'baseDir'. */
  @Parameter(property = "com.tcmj.pug.enums.basedir", defaultValue = "${basedir}", required = true)
  private File baseDirectory;

  /** Mandatory Property which defines the location (url) where to load the input data. */
  @Parameter(property = "com.tcmj.pug.enums.url", required = true)
  private String url;

  /** Optional Property to override the column names used for the sub fields in the java enum class. */
  @Parameter(property = "com.tcmj.pug.enums.subfieldnames")
  private String[] subFieldNames;

  /** Optional Property to write some static javadoc lines to our java enum. */
  @Parameter(property = "com.tcmj.pug.enums.classjavadoc")
  private String[] javadocClassLevel;

  /** Css selector to a record (also to a table possible). */
  @Parameter(property = "com.tcmj.pug.enums.cssselector", defaultValue = "table", required = true)
  private String tableCssSelector;

  /** Physical position of the column to be used to extract the enum constant names (beginning/defaulting with/to 1). */
  @Parameter(property = "com.tcmj.pug.enums.constantcolumn", defaultValue = "1", required = true)
  private Integer constantColumn;

  /** Optional possibility to extract further columns and use it as fields in the enum. */
  @Parameter(property = "com.tcmj.pug.enums.subdatacolumns")
  private Integer[] subDataColumns;

  /** Optional Property NamingStrategy Constants. */
  @Parameter(property = "com.tcmj.pug.enums.namingstrategy.constants")
  private String[] namingStrategyConstants;

  /** Optional Property NamingStrategy FieldNames. */
  @Parameter(property = "com.tcmj.pug.enums.namingstrategy.fields")
  private String[] namingStrategyFieldNames;

  /** Optional Property to keep the first row of html tables. */
  @Parameter(property = "com.tcmj.pug.enums.keepfirstrow")
  private Boolean keepFirstRow = Boolean.FALSE;

  /** Optional possibility to skip specific records. */
  @Parameter(property = "com.tcmj.pug.enums.skip.values")
  private String[] valuesToSkip;
  private List<String> lstValuesToSkip = new ArrayList<>();

  private EnumResult currentEnumResult;

  private static <T> boolean isParameterSet(T[] param) {
    return param != null && param.length > 0;
  }

  private static NamingStrategy invokeMethod(String methodName) throws InvocationTargetException, IllegalAccessException {
    for (Method method : NamingStrategyFactory.class.getMethods()) {
      if (methodName.equalsIgnoreCase(method.getName())) { //don't be strict
        return (NamingStrategy) method.invoke(null);
      }
    }
    throw new IllegalStateException("Cannot find a NamingStrategy " + methodName);
  }

  private void displayYoureWelcome() {
    getLog().info(getLine());
    getLog().info(arrange("Starting 'tcmj-pug-enum-maven-plugin' !"));
    getLog().info(getLine());
    getLog().info(arrange("ClassNameToBeCreated: " + this.className));
    getLog().info(arrange("SourceOutputDirectory: '" + getSourceDirectory() + "', OutputEncoding: " + getEncoding()));
    getLog().info(arrange("FetchURL: " + this.url));

    if (isParameterSet(this.subFieldNames)) {
      getLog().info(arrange("SubFieldNames fixed to: " + Arrays.toString(this.subFieldNames)));
    } else {
      getLog().info(arrange("SubFieldNames: <not defined, will be computed then>"));
    }
    if (isParameterSet(this.namingStrategyConstants)) {
      getLog().info(arrange("NamingStrategy Constants: " + Arrays.toString(this.namingStrategyConstants)));
    } else {
      getLog().info(arrange("NamingStrategy Constants: <default>"));
    }
    if (isParameterSet(this.namingStrategyFieldNames)) {
      getLog().info(arrange("NamingStrategy FieldNames: " + Arrays.toString(this.namingStrategyFieldNames)));
    } else {
      getLog().info(arrange("NamingStrategy FieldNames: <default>"));
    }
    if (isParameterSet(this.javadocClassLevel)) {
      Stream.of(this.javadocClassLevel).map((v) -> arrange("JavaDocClassLevel: " + v)).forEach((t) -> getLog().info(t));
    } else {
      getLog().info(arrange("JavaDocClassLevel: <will be computed>"));
    }
    getLog().info(arrange("Extracts EnumData from a table of a html document using a URLXPathHtmlDataProvider!"));
    getLog().info(arrange("CSS Locator used to locate the table: " + this.tableCssSelector));
    getLog().info(arrange("Constant column used in Enum: " + this.constantColumn));
    getLog().info(arrange("KeepFirstRow: " + this.keepFirstRow));

    if (isParameterSet(this.subDataColumns)) {
      getLog().info(arrange("SubData columns to include: " + Arrays.toString(this.subDataColumns)));
    }

    if (this.keepFirstRow == Boolean.FALSE) {
      this.lstValuesToSkip.add("#1");
    }

    if (isParameterSet(this.valuesToSkip)) {
      Collections.addAll(this.lstValuesToSkip, this.valuesToSkip);
      getLog().info(arrange("Input values to skip: " + lstValuesToSkip));
    }
  }

  /**
   * Depending if a parameter is set (or not) we use the default strategies or the defined ones.
   */
  private NamingStrategy getNamingStrategyConstantNames() {
    if (isParameterSet(this.namingStrategyConstants)) {
      return resolveNamingStrategies(this.namingStrategyConstants);
    }
    return Fluent.getDefaultNamingStrategyConstantNames();
  }

  private DataProvider getDataProvider() {
    if (this.dataProvider != null && !StringUtils.equals(this.dataProvider, "com.tcmj.pug.enums.datasources.impl.URLXPathHtmlDataProvider")) {
      throw new UnsupportedOperationException("NotYetImplemented ! Cannot change data provider class to: " + this.dataProvider);
    }
    URLHtmlDataProvider urlHtmlDataProvider = new URLHtmlDataProvider(
      getInputUrl(),
      this.tableCssSelector, //xpath to a record to further (also to a table possible)
      this.constantColumn, //enum constant column
      this.subDataColumns == null ? null : Stream.of(this.subDataColumns).mapToInt(i -> i).toArray(), //convert to int[]
      false
    );
    urlHtmlDataProvider.setValuesToSkip(lstValuesToSkip);
    return urlHtmlDataProvider;
  }

  /**
   * Depending if a parameter is set (or not) we use the default strategies or the defined ones.
   */
  private NamingStrategy getNamingStrategyFieldNames() {
    if (isParameterSet(this.namingStrategyFieldNames)) {
      return resolveNamingStrategies(this.namingStrategyFieldNames);
    }
    return Fluent.getDefaultNamingStrategyFieldNames();
  }

  private NamingStrategy resolveNamingStrategies(String[] namingStrategyConstants) {
    NamingStrategy strategy = value -> value;
    for (String namingStrategy : namingStrategyConstants) {
      try {
        NamingStrategy ns = invokeMethod(namingStrategy);
        getLog().debug("ReflectionResult: " + namingStrategy + "--->" + ns);
        strategy = strategy.and(ns);
      } catch (Exception e) {
        getLog().error("Skipping! " + e.getMessage());
      }
    }
    return strategy;
  }


  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    StaticLoggerBinder.getSingleton().setMavenLog(getLog());

    try {
      this.currentEnumResult = null;
      displayYoureWelcome();

      final DataProvider myDataProvider = Objects.requireNonNull(getDataProvider(), "getDataProvider() delivers a NULL DataProvider object!");
      getLog().info(arrange("DataProvider: " + myDataProvider));

      final ClassBuilder myClassBuilder = Objects.requireNonNull(getClassBuilder(), "getClassBuilder() delivers a NULL ClassBuilder object!");
      getLog().info(arrange("ClassBuilder: " + myClassBuilder));

      final SourceFormatter mySourceFormatter = Objects.requireNonNull(getSourceFormatter(), "getSourceFormatter() delivers a NULL SourceFormatter object!");
      getLog().info(arrange("SourceFormatter: " + mySourceFormatter));

      final EnumExporter myEnumExporter = Objects.requireNonNull(getEnumExporter(), "getEnumExporter() delivers a NULL EnumExporter object!");

      final EnumData data = Objects.requireNonNull(
        myDataProvider.load(),
        "DataProvider.load() returns a NULL EnumData object!");
      Objects.requireNonNull(data.getData(), "EnumData has no records loaded! It's empty!");
      data.setClassName(className);

      if (isParameterSet(this.subFieldNames)) {
        //Overriding the field names usually fetched by the data provider implementation!
        data.setFieldNames(this.subFieldNames);
      }

      data.setNamingStrategyConstants(getNamingStrategyConstantNames());
      data.setNamingStrategyFields(getNamingStrategyFieldNames());

      if (isParameterSet(this.javadocClassLevel)) {
        Stream.of(this.javadocClassLevel).map(LogFormatter::encloseJavaDoc).forEach(text -> data.addJavaDoc(EnumData.JDocKeys.CLASS.name(), text));
      } else {
        data.addJavaDoc(EnumData.JDocKeys.CLASS.name(), encloseJavaDoc("Data has been fetched from '" + this.url + "'."));
      }

      myClassBuilder.importData(data);

      String myEnum = myClassBuilder.build();

      EnumResult eResult = EnumResult.of(data, mySourceFormatter, myEnum);

      //add option for JavaSourceFileExporter: as global option
      eResult.addOption(JavaSourceFileExporter.OPTION_EXPORT_PATH_PREFIX, getSourceDirectory());
      eResult.addOption(JavaSourceFileExporter.OPTION_EXPORT_ENCODING, getEncoding());

      myEnumExporter.export(eResult);

      getLog().info(arrange(String.format("Enum successfully created with %s characters!", myEnum.length())));

      this.currentEnumResult = eResult;
    } catch (Exception e) {
      getLog().error("Cannot create your enum: " + className + "!", e);
      throw new MojoExecutionException("ExecutionFailure!", e);
    }
  }

  /** Usually we want always the FileExporter to save the enum into file system. */
  private EnumExporter getEnumExporter() {
    return new JavaSourceFileExporter();
  }

  /** Define which ClassBuilder implementation we want to use. */
  private ClassBuilder getClassBuilder() {
    return ClassBuilderFactory.getBestEnumBuilder(); //..the best we can get
  }

  /** Define which Source Formatter implementation we want to use. */
  private SourceFormatter getSourceFormatter() {
    return SourceFormatterFactory.getBestSourceCodeFormatter(); //..the best we can get
  }

  public String getEncoding() {
    if (this.encoding == null || this.encoding.trim().isEmpty()) {
      getLog().warn("File encoding has not been set, using platform encoding: '" +
        ReaderFactory.FILE_ENCODING + "', i.e. your build is platform dependent!");
      getLog().warn("Please take a look into the FAQ: https://maven.apache.org/general.html#encoding-warning");
      this.encoding = ReaderFactory.FILE_ENCODING;
    }
    return encoding;
  }

  public EnumResult getCurrentEnumResult() {
    return currentEnumResult;
  }

  /**
   * Output path to save the generated enum java files.
   * It defaults to mavens 'project.build.sourceDirectory'.
   * Mandatory Property.
   *
   * @return a java file object pointing to the source output directory
   */
  public File getSourceDirectory() {
    return sourceDirectory;
  }

  public String getInputUrl() {
    URI myURL = parseUrl(this.url, this.baseDirectory);
    if (!StringUtils.equalsIgnoreCase(this.url, myURL.toString())) {
      getLog().info("Using URL: " + myURL.toString());
    }
    return myURL.toString();
  }

  /**
   * Validate the url from the configuration part of the pom.xml.
   * Try to build up a URL object which represents the existing file or the http url
   *
   * @param urlToLoad string representation
   */
  private URI parseUrl(final String urlToLoad, final File baseDirectory) {
    try {
      if (StringUtils.startsWithAny(urlToLoad, "http:", "https:")) {
        getLog().info("URL seems to be external: " + urlToLoad);
        return URI.create(urlToLoad);
      }

      if (StringUtils.startsWith(urlToLoad, "file:")) {
        getLog().info("Trying 'file:' protocol to load a html: " + urlToLoad);
        URI baseURI = URI.create("file:/");
        URI uri = URI.create(urlToLoad);
        URI resultURI = baseURI.resolve(uri);
        Path pathFile = Paths.get(resultURI);
        if (Files.exists(pathFile)) {
          getLog().info("Successfully found file: " + pathFile);
          return resultURI;
        }
      }

      //if still not found ... we try to use base-directory
      if (this.baseDirectory != null) {
        getLog().info(arrange("BaseDirectory: '" + this.baseDirectory));
        URI mavenBaseDirUri = this.baseDirectory.toURI();
        URI resultOfMavenBaseUri = mavenBaseDirUri.resolve(urlToLoad);
        Path resultPath = Paths.get(resultOfMavenBaseUri);
        if (Files.exists(resultPath)) {
          getLog().info("Successfully found file in base directory: " + resultPath);
          return resultPath.toUri();
        }
      }

      if (this.project != null) {
        getLog().info(arrange("MavenProject: " + this.project));
        return this.project.getBasedir().toURI();
      }

      Path resultPath = Paths.get(urlToLoad);
      if (Files.exists(resultPath)) {
        getLog().info("Successfully found file in base directory: " + resultPath);
        return resultPath.toAbsolutePath().toUri();
      }

      if (!StringUtils.startsWith(urlToLoad, "file:///")) {
        return URI.create("file:///" + urlToLoad);
      }
      return URI.create(urlToLoad);
    } catch (Exception e) {
      throw new IllegalStateException("Error validating source url!", e);
    }
  }
}
