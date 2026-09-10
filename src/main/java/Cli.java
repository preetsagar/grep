import java.io.Console;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the command line. Shape is always: {@code [flags...] -E <pattern> [path...]}
 * where flags are any of {@code -r}, {@code -o}, {@code --color=<always|never|auto>}.
 */
record Cli(String pattern, List<String> paths, boolean recursive, boolean onlyMatching, String colorMode) {

  static Cli parse(String[] args) {
    boolean recursive = false;
    boolean onlyMatching = false;
    String colorMode = "never";
    int i = 0;
    for (; i < args.length; i++) {
      String a = args[i];
      if (a.equals("-E")) {
        break;
      } else if (a.equals("-r") || a.equals("-R") || a.equals("--recursive")) {
        recursive = true;
      } else if (a.equals("-o") || a.equals("--only-matching")) {
        onlyMatching = true;
      } else if (a.startsWith("--color=")) {
        colorMode = a.substring("--color=".length());
      } else if (a.equals("--color")) {
        colorMode = "auto";
      } else {
        fail("Unknown option: " + a);
      }
    }
    if (i + 1 >= args.length) {
      fail("Usage: ./your_program.sh [-r] [-o] [--color=MODE] -E <pattern> [path...]");
    }
    String pattern = args[i + 1];
    List<String> paths = new ArrayList<>();
    for (int j = i + 2; j < args.length; j++) {
      paths.add(args[j]);
    }
    return new Cli(pattern, paths, recursive, onlyMatching, colorMode);
  }

  /** Whether matched substrings should be wrapped in color escape sequences. */
  boolean highlight() {
    return switch (colorMode) {
      case "always" -> true;
      case "auto" -> stdoutIsTerminal();
      default -> false;
    };
  }

  private static boolean stdoutIsTerminal() {
    Console c = System.console();
    return c != null && c.isTerminal();
  }

  private static void fail(String message) {
    System.out.println(message);
    System.exit(2);
  }
}
