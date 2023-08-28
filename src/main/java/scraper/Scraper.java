package scraper;

import com.opencsv.CSVWriter;
import jdk.internal.access.SharedSecrets;
import legend.game.scripting.RunningScript;
import legend.game.scripting.ScriptDescription;
import legend.game.scripting.ScriptParam;
import legend.game.scripting.ScriptParams;
import org.apache.commons.net.ftp.FTPClient;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static legend.game.Scus94491BpeSegment_8004.scriptSubFunctions_8004e29c;

public class Scraper {
  public static void main(String[] args) throws NoSuchMethodException, IOException {
    new Scraper().scrape();
  }

  public void scrape() throws NoSuchMethodException, IOException {
    final List<ScriptFunction> functions = new ArrayList<>();

    System.out.println("Scraping data...");

    int missingDescription = 0;
    for(int i = 0; i < scriptSubFunctions_8004e29c.length; i++) {
      // This is a major hack that could break at any time, but it's the only way I've found to trace a method ref
      final Member member = SharedSecrets.getJavaLangAccess().getConstantPool(scriptSubFunctions_8004e29c[i].getClass()).getMethodAt(20);

      final Method method = member.getDeclaringClass().getMethod(member.getName(), RunningScript.class);
      System.out.println(i + ": " + method.getDeclaringClass().getSimpleName() + "::" + method.getName());

      final ScriptDescription descriptionAnnotation = method.getAnnotation(ScriptDescription.class);
      final ScriptParams paramsAnnotation = method.getAnnotation(ScriptParams.class);
      final ScriptParam paramAnnotation = method.getAnnotation(ScriptParam.class);
      final ScriptParam[] params;

      if(paramsAnnotation != null) {
        params = paramsAnnotation.value();
      } else if(paramAnnotation != null) {
        params = new ScriptParam[] { paramAnnotation };
      } else {
        params = new ScriptParam[0];
      }

      functions.add(new ScriptFunction(method.getDeclaringClass().getSimpleName() + "::" + method.getName(), descriptionAnnotation != null ? descriptionAnnotation.value() : "", params));

      if(descriptionAnnotation == null) {
        missingDescription++;
      }
    }

    if(missingDescription == 0) {
      System.out.println("Processed " + scriptSubFunctions_8004e29c.length + " script functions");
    } else {
      System.err.println("Missing " + missingDescription + "/" + scriptSubFunctions_8004e29c.length + " descriptions for script functions");

      for(final ScriptFunction function : functions) {
        if(function.description.isEmpty()) {
          System.err.println(function.name);
        }
      }
    }

    System.out.println("Writing CSVs...");

    final Path descriptionsPath = Path.of("descriptions.csv");
    final Path paramsPath = Path.of("params.csv");

    Files.deleteIfExists(descriptionsPath);
    Files.deleteIfExists(paramsPath);

    // Keep a list of what functions have already been added so we don't write out duplicate params
    final Set<String> alreadyAdded = new HashSet<>();

    try (
      final CSVWriter csvDescriptions = new CSVWriter(new FileWriter(descriptionsPath.toFile()));
      final CSVWriter csvParams = new CSVWriter(new FileWriter(paramsPath.toFile()))
    ) {
      for(final ScriptFunction function : functions) {
        csvDescriptions.writeNext(new String[] {function.name, function.description});

        if(!alreadyAdded.contains(function.name)) {
          for(final ScriptParam param : function.params) {
            csvParams.writeNext(new String[]{function.name, param.direction().name().toLowerCase(), param.type().name().toLowerCase(), param.name(), param.description(), param.branch().name().toLowerCase()});
          }
        }

        alreadyAdded.add(function.name);
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
    } finally {
      ftp.disconnect();
    }

    System.out.println("Bye now");
  }
}
