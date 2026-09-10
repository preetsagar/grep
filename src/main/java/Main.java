import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {

  public static void main(String[] args) {
    Cli cli = Cli.parse(args);
    Regex regex = Regex.compile(cli.pattern());

    boolean matched = cli.paths().isEmpty()
        ? searchStdin(regex, cli)
        : searchPaths(regex, cli);

    System.exit(matched ? 0 : 1);
  }

  // --- stdin --------------------------------------------------------------

  private static boolean searchStdin(Regex regex, Cli cli) {
    boolean anyMatch = false;
    for (String line : readAllLines()) {
      List<int[]> matches = regex.allMatches(line);
      if (matches.isEmpty()) {
        continue;
      }
      anyMatch = true;
      if (cli.onlyMatching()) {
        for (int[] m : matches) {
          System.out.println(line.substring(m[0], m[1]));
        }
      } else if (cli.highlight()) {
        System.out.println(highlight(line, matches));
      } else {
        System.out.println(line);
      }
    }
    return anyMatch;
  }

  // --- files -------------------------------------------------------------

  private static boolean searchPaths(Regex regex, Cli cli) {
    List<String> files = new ArrayList<>();
    boolean withNames = cli.paths().size() > 1;
    for (String p : cli.paths()) {
      Path path = Path.of(p);
      if (cli.recursive() && Files.isDirectory(path)) {
        collectFiles(path, files);
        withNames = true;
      } else {
        files.add(p);
      }
    }

    boolean anyMatch = false;
    for (String file : files) {
      List<String> lines;
      try {
        lines = Files.readAllLines(Path.of(file), StandardCharsets.UTF_8);
      } catch (IOException e) {
        System.err.println("grep: " + file + ": " + e.getMessage());
        continue;
      }
      for (String line : lines) {
        if (!regex.find(line)) {
          continue;
        }
        anyMatch = true;
        System.out.println(withNames ? file + ":" + line : line);
      }
    }
    return anyMatch;
  }

  private static void collectFiles(Path dir, List<String> out) {
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.filter(Files::isRegularFile).map(Path::toString).forEach(out::add);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // --- helpers ----------------------------------------------------------

  private static final String HL_START = "\033[01;31m";
  private static final String HL_END = "\033[m";

  /** Wraps each match in grep's default bold-red escape sequence. */
  private static String highlight(String line, List<int[]> matches) {
    StringBuilder sb = new StringBuilder();
    int cursor = 0;
    for (int[] m : matches) {
      sb.append(line, cursor, m[0]).append(HL_START).append(line, m[0], m[1]).append(HL_END);
      cursor = m[1];
    }
    return sb.append(line.substring(cursor)).toString();
  }

  private static List<String> readAllLines() {
    List<String> lines = new ArrayList<>();
    try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while ((line = r.readLine()) != null) {
        lines.add(line);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return lines;
  }
}
