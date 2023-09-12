package scraper;

import com.opencsv.CSVWriter;
import jdk.internal.access.SharedSecrets;
import legend.game.scripting.RunningScript;
import legend.game.scripting.ScriptDescription;
import legend.game.scripting.ScriptEnum;
import legend.game.scripting.ScriptParam;
import org.apache.commons.net.ftp.FTPClient;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static legend.game.Scus94491BpeSegment_8004.scriptSubFunctions_8004e29c;

public class Scraper {
  public static void main(final String[] args) throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException {
    new Scraper().scrape();
  }

  public void scrape() throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException {
    final List<ScriptFunction> functions = new ArrayList<>();
    final Set<Class<Enum<?>>> allEnums = new HashSet<>();

    System.out.println("Scraping data...");

    int missingDescription = 0;
    for(int i = 0; i < scriptSubFunctions_8004e29c.length; i++) {
      // This is a major hack that could break at any time, but it's the only way I've found to trace a method ref
      final Member member = SharedSecrets.getJavaLangAccess().getConstantPool(scriptSubFunctions_8004e29c[i].getClass()).getMethodAt(20);

      final Method method = member.getDeclaringClass().getMethod(member.getName(), RunningScript.class);
      System.out.println(i + ": " + method.getDeclaringClass().getSimpleName() + "::" + method.getName());

      final ScriptDescription descriptionAnnotation = method.getAnnotation(ScriptDescription.class);
      final ScriptParam[] params = this.getAnnotations(method, ScriptParam.class);
      final ScriptEnum[] enums = this.getAnnotations(method, ScriptEnum.class);

      functions.add(new ScriptFunction(method.getDeclaringClass().getSimpleName() + "::" + method.getName(), descriptionAnnotation != null ? descriptionAnnotation.value() : "", params, enums));

      for(final ScriptEnum e : enums) {
        allEnums.add((Class<Enum<?>>)e.value());
      }

      if(descriptionAnnotation == null) {
        missingDescription++;
      }
    }

    if(missingDescription == 0) {
      System.out.println("Processed " + scriptSubFunctions_8004e29c.length + " script functions");
    } else {
      System.err.println("Missing " + missingDescription + '/' + scriptSubFunctions_8004e29c.length + " descriptions for script functions");

      for(final ScriptFunction function : functions) {
        if(function.description.isEmpty()) {
          System.err.println(function.name);
        }
      }
    }

    System.out.println("Writing CSVs...");

    for(final Class<Enum<?>> cls : allEnums) {
      final Path enumPath = Path.of(cls.getTypeName() + ".csv");
      Files.deleteIfExists(enumPath);

      try (final CSVWriter csvEnum = new CSVWriter(new FileWriter(enumPath.toFile()));) {
        for(final Enum<?> val : cls.getEnumConstants()) {
          csvEnum.writeNext(new String[] {val.name()});
        }
      }
    }

    final Path descriptionsPath = Path.of("descriptions.csv");
    final Path paramsPath = Path.of("params.csv");
    final Path enumsPath = Path.of("enums.csv");

    Files.deleteIfExists(descriptionsPath);
    Files.deleteIfExists(paramsPath);
    Files.deleteIfExists(enumsPath);

    // Keep a list of what functions have already been added so we don't write out duplicate params
    final Set<String> alreadyAdded = new HashSet<>();

    try (
      final CSVWriter csvDescriptions = new CSVWriter(new FileWriter(descriptionsPath.toFile()));
      final CSVWriter csvParams = new CSVWriter(new FileWriter(paramsPath.toFile()));
      final CSVWriter csvEnums = new CSVWriter(new FileWriter(enumsPath.toFile()))
    ) {
      for(final ScriptFunction function : functions) {
        csvDescriptions.writeNext(new String[] {function.name, function.description});

        if(!alreadyAdded.contains(function.name)) {
          int enumIndex = 0;
          for(final ScriptParam param : function.params) {
            final String paramType;
            if(param.type() == ScriptParam.Type.ENUM) {
              paramType = function.enums[enumIndex++].value().getTypeName();
            } else {
              paramType = param.type().name().toLowerCase();
            }

            csvParams.writeNext(new String[] {function.name, param.direction().name().toLowerCase(), paramType, param.name(), param.description(), param.branch().name().toLowerCase()});
          }
        }

        alreadyAdded.add(function.name);
      }

      for(final Class<Enum<?>> val : allEnums) {
        csvEnums.writeNext(new String[] {val.getTypeName()});
      }
    }

    final Path credentialsFile = Path.of("credentials.conf");
    if(!Files.exists(credentialsFile)) {
      System.out.println("No FTP credentials provided, skipping upload");
      return;
    }

    final Properties credentials = new Properties();

    try(final InputStream inputStream = Files.newInputStream(credentialsFile)) {
      credentials.load(inputStream);
    }

    System.out.println("Uploading data...");

    final FTPClient ftp = new FTPClient();
    try {
      ftp.connect(credentials.getProperty("host"));
      ftp.login(credentials.getProperty("username"), credentials.getProperty("password"));
      ftp.enterLocalPassiveMode();

      try(final InputStream fis = Files.newInputStream(descriptionsPath)) {
        ftp.storeFile("descriptions.csv", fis);
      }

      try(final InputStream fis = Files.newInputStream(paramsPath)) {
        ftp.storeFile("params.csv", fis);
      }

      try(final InputStream fis = Files.newInputStream(enumsPath)) {
        ftp.storeFile("enums.csv", fis);
      }

      for(final Class<Enum<?>> cls : allEnums) {
        final Path enumPath = Path.of(cls.getTypeName() + ".csv");
        try(final InputStream fis = Files.newInputStream(enumPath)) {
          ftp.storeFile(enumPath.toString(), fis);
        }
      }
    } finally {
      ftp.disconnect();
    }

    System.out.println("Bye now");
  }

  private <T extends Annotation> T[] getAnnotations(final Method method, final Class<T> singleCls) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    final Class<? extends Annotation> pluralCls = singleCls.getAnnotation(Repeatable.class).value();
    final Annotation plural = method.getAnnotation(pluralCls);
    final T single = method.getAnnotation(singleCls);

    final T[] all;
    if(plural != null) {
      all = (T[])pluralCls.getMethod("value").invoke(plural);
    } else if(single != null) {
      all = (T[])Array.newInstance(singleCls, 1);
      all[0] = single;
    } else {
      all = (T[])Array.newInstance(singleCls, 0);
    }

    return all;
  }
}
