package org.legendofdragoon.meta;

import com.opencsv.CSVWriter;
import jdk.internal.access.SharedSecrets;
import legend.game.EngineState;
import legend.game.EngineStateEnum;
import legend.game.scripting.FlowControl;
import legend.game.scripting.RunningScript;
import legend.game.scripting.ScriptDescription;
import legend.game.scripting.ScriptEnum;
import legend.game.scripting.ScriptParam;
import legend.game.types.OverlayStruct;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.net.ftp.FTPSClient;

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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import static legend.game.Scus94491BpeSegment_8004.gameStateOverlays_8004dbc0;
import static legend.game.Scus94491BpeSegment_8004.scriptSubFunctions_8004e29c;

public class Scraper {
  public static void main(final String[] args) throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException, InstantiationException {
    final Options options = new Options();
    options.addOption("v", "version", true, "The version name to use for the upload");
    options.addOption("h", "host", true, "The host for the upload");
    options.addOption("u", "username", true, "The username for the upload");
    options.addOption("p", "password", true, "The password for the upload");

    final CommandLine cmd;
    final CommandLineParser parser = new DefaultParser();
    final HelpFormatter helper = new HelpFormatter();

    try {
      cmd = parser.parse(options, args);
    } catch(final ParseException e) {
      System.out.println(e.getMessage());
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    final Path credentialsFile = Path.of("credentials.conf");
    final Properties credentials;
    if(!Files.exists(credentialsFile)) {
      credentials = null;
    } else {
      credentials = new Properties();

      try(final InputStream inputStream = Files.newInputStream(credentialsFile)) {
        credentials.load(inputStream);
      }
    }

    final String version = cmd.getOptionValue("version", "snapshot");
    final String host = cmd.getOptionValue("host", credentials != null ? credentials.getProperty("host") : null);
    final String username = cmd.getOptionValue("username", credentials != null ? credentials.getProperty("username") : null);
    final String password = cmd.getOptionValue("password", credentials != null ? credentials.getProperty("password") : null);

    if(host == null || username == null || password == null) {
      helper.printHelp("Usage:", options);
      System.exit(1);
      return;
    }

    new Scraper().scrape(version, host, username, password);
  }

  public void scrape(final String version, final String host, final String username, final String password) throws NoSuchMethodException, IOException, InvocationTargetException, IllegalAccessException, InstantiationException {
    final List<ScriptFunction> functions = new ArrayList<>();
    final Set<Class<Enum<?>>> allEnums = new HashSet<>();

    System.out.println("Scraping data...");

    int total = 0;
    int missingDescription = 0;
    for(int i = 0; i < scriptSubFunctions_8004e29c.length; i++) {
      final ScriptFunction scriptFunction = this.processFunction(i, scriptSubFunctions_8004e29c[i], functions, allEnums);
      total++;

      if(scriptFunction.description.isEmpty()) {
        missingDescription++;
      }
    }

    for(final EngineStateEnum state : EngineStateEnum.values()) {
      final OverlayStruct overlayInfo = gameStateOverlays_8004dbc0.get(state);

      if(overlayInfo != null) {
        final EngineState overlay = overlayInfo.class_00.getConstructor().newInstance();

        final Function<RunningScript, FlowControl>[] scriptFunctions = overlay.getScriptFunctions();

        for(int i = 0; i < scriptFunctions.length; i++) {
          if(scriptFunctions[i] != null) {
            final ScriptFunction scriptFunction = this.processFunction(i, scriptFunctions[i], functions, allEnums);
            total++;

            if(scriptFunction.description.isEmpty()) {
              missingDescription++;
            }
          }
        }
      }
    }

    if(missingDescription == 0) {
      System.out.println("Processed " + total + " script functions");
    } else {
      System.err.println("Missing " + missingDescription + '/' + total + " descriptions for script functions");

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

      try(final CSVWriter csvEnum = new CSVWriter(new FileWriter(enumPath.toFile()))) {
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

    try(
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

    System.out.printf("Uploading data to %s...%n", version);

    final FTPSClient ftp = new FTPSClient();
    try {
      ftp.connect(host);

      if(!ftp.login(username, password)) {
        throw new RuntimeException("Failed to log in: " + ftp.getReplyString());
      }

      ftp.enterLocalPassiveMode();

      // Set protection buffer size
      ftp.execPBSZ(0);
      // Set data channel protection to private
      ftp.execPROT("P");

      try(final InputStream fis = Files.newInputStream(descriptionsPath)) {
        if(!ftp.makeDirectory(version) && ftp.getReplyCode() != 550) { // 550 is "File exists"
          throw new RuntimeException("Failed to create version directory " + ftp.getReplyString());
        }

        if(!ftp.changeWorkingDirectory(version)) {
          throw new RuntimeException("Failed to cd to version directory " + ftp.getReplyString());
        }

        if(!ftp.storeFile("descriptions.csv", fis)) {
          throw new RuntimeException("Failed to upload descriptions.csv " + ftp.getReplyString());
        }
      }

      try(final InputStream fis = Files.newInputStream(paramsPath)) {
        if(!ftp.storeFile("params.csv", fis)) {
          throw new RuntimeException("Failed to upload params.csv " + ftp.getReplyString());
        }
      }

      try(final InputStream fis = Files.newInputStream(enumsPath)) {
        if(!ftp.storeFile("enums.csv", fis)) {
          throw new RuntimeException("Failed to upload enums.csv " + ftp.getReplyString());
        }
      }

      for(final Class<Enum<?>> cls : allEnums) {
        final Path enumPath = Path.of(cls.getTypeName() + ".csv");
        try(final InputStream fis = Files.newInputStream(enumPath)) {
          if(!ftp.storeFile(enumPath.toString(), fis)) {
            throw new RuntimeException("Failed to upload %s.csv %s".formatted(cls.getTypeName(), ftp.getReplyString()));
          }
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

  private ScriptFunction processFunction(final int index, final Function<RunningScript, FlowControl> function, final List<ScriptFunction> functions, final Set<Class<Enum<?>>> allEnums) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    // This is a major hack that could break at any time, but it's the only way I've found to trace a method ref
    Member member;
    try {
      member = SharedSecrets.getJavaLangAccess().getConstantPool(function.getClass()).getMethodAt(20);
    } catch(final Exception e) {
      member = SharedSecrets.getJavaLangAccess().getConstantPool(function.getClass()).getMethodAt(25);
    }

    final Method method = member.getDeclaringClass().getDeclaredMethod(member.getName(), RunningScript.class);
    System.out.println(index + ": " + method.getDeclaringClass().getSimpleName() + "::" + method.getName());

    final ScriptDescription descriptionAnnotation = method.getAnnotation(ScriptDescription.class);
    final ScriptParam[] params = this.getAnnotations(method, ScriptParam.class);
    final ScriptEnum[] enums = this.getAnnotations(method, ScriptEnum.class);

    final ScriptFunction scriptFunction = new ScriptFunction(method.getDeclaringClass().getSimpleName() + "::" + method.getName(), descriptionAnnotation != null ? descriptionAnnotation.value() : "", params, enums);

    if(index >= functions.size()) {
      functions.add(scriptFunction);
    } else {
      functions.set(index, scriptFunction);
    }

    for(final ScriptEnum e : enums) {
      allEnums.add((Class<Enum<?>>)e.value());
    }

    return scriptFunction;
  }
}
