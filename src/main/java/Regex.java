import java.util.ArrayList;
import java.util.List;

/**
 * A small backtracking regular-expression engine supporting the subset of syntax
 * used by this challenge: literals, '.', '\d' '\w' '\s', character groups
 * ([...] and [^...]) with ranges, anchors '^' '$', the quantifiers '+' '?' '*'
 * and '{n}' '{n,}' '{n,m}', grouping with '(...)', alternation '|', and
 * backreferences '\1'..'\9'.
 */
public final class Regex {

  private final Node root;
  private final int groupCount;

  private Regex(Node root, int groupCount) {
    this.root = root;
    this.groupCount = groupCount;
  }

  public static Regex compile(String pattern) {
    Parser p = new Parser(pattern);
    Node root = p.parseAlternation();
    if (!p.atEnd()) {
      throw new IllegalArgumentException("Unexpected trailing characters in pattern: " + pattern);
    }
    return new Regex(root, p.groupCount);
  }

  /** True if the pattern matches anywhere in {@code input}. */
  public boolean find(String input) {
    return matchFrom(input, 0) != null;
  }

  /**
   * Non-overlapping leftmost matches, scanning left to right (grep -o semantics).
   * Each entry is {startInclusive, endExclusive}. Zero-length matches are skipped.
   */
  public List<int[]> allMatches(String input) {
    List<int[]> result = new ArrayList<>();
    int from = 0;
    while (from <= input.length()) {
      int[] m = matchFrom(input, from);
      if (m == null) {
        break;
      }
      if (m[1] > m[0]) {
        result.add(m);
        from = m[1];
      } else {
        from = m[0] + 1;
      }
    }
    return result;
  }

  /** Leftmost match at or after {@code from}, or null. Returns {start, end}. */
  private int[] matchFrom(String input, int from) {
    for (int start = Math.max(from, 0); start <= input.length(); start++) {
      State st = new State(input, groupCount);
      int[] end = {-1};
      if (root.match(st, start, pos -> {
        end[0] = pos;
        return true;
      })) {
        return new int[] {start, end[0]};
      }
    }
    return null;
  }

  // --- matching machinery -------------------------------------------------

  /** Continuation: given the position after an atom matched, try to finish. */
  private interface Cont {
    boolean apply(int pos);
  }

  private static final class State {
    final String input;
    final int[] capStart;
    final int[] capEnd;

    State(String input, int groupCount) {
      this.input = input;
      this.capStart = new int[groupCount + 1];
      this.capEnd = new int[groupCount + 1];
      java.util.Arrays.fill(capStart, -1);
      java.util.Arrays.fill(capEnd, -1);
    }
  }

  private abstract static class Node {
    abstract boolean match(State st, int pos, Cont k);
  }

  private static final class Literal extends Node {
    final char c;

    Literal(char c) {
      this.c = c;
    }

    boolean match(State st, int pos, Cont k) {
      return pos < st.input.length() && st.input.charAt(pos) == c && k.apply(pos + 1);
    }
  }

  private static final class AnyChar extends Node {
    boolean match(State st, int pos, Cont k) {
      return pos < st.input.length() && k.apply(pos + 1);
    }
  }

  private interface CharPredicate {
    boolean test(char c);
  }

  private static final class CharClass extends Node {
    final CharPredicate predicate;

    CharClass(CharPredicate predicate) {
      this.predicate = predicate;
    }

    boolean match(State st, int pos, Cont k) {
      return pos < st.input.length() && predicate.test(st.input.charAt(pos)) && k.apply(pos + 1);
    }
  }

  private static final class StartAnchor extends Node {
    boolean match(State st, int pos, Cont k) {
      return pos == 0 && k.apply(pos);
    }
  }

  private static final class EndAnchor extends Node {
    boolean match(State st, int pos, Cont k) {
      return pos == st.input.length() && k.apply(pos);
    }
  }

  private static final class Sequence extends Node {
    final List<Node> items;

    Sequence(List<Node> items) {
      this.items = items;
    }

    boolean match(State st, int pos, Cont k) {
      return matchFrom(0, st, pos, k);
    }

    private boolean matchFrom(int i, State st, int pos, Cont k) {
      if (i == items.size()) {
        return k.apply(pos);
      }
      return items.get(i).match(st, pos, p -> matchFrom(i + 1, st, p, k));
    }
  }

  private static final class Alternation extends Node {
    final List<Node> branches;

    Alternation(List<Node> branches) {
      this.branches = branches;
    }

    boolean match(State st, int pos, Cont k) {
      for (Node branch : branches) {
        if (branch.match(st, pos, k)) {
          return true;
        }
      }
      return false;
    }
  }

  private static final class Repeat extends Node {
    final Node atom;
    final int min;
    final int max;

    Repeat(Node atom, int min, int max) {
      this.atom = atom;
      this.min = min;
      this.max = max;
    }

    boolean match(State st, int pos, Cont k) {
      return rep(0, st, pos, k);
    }

    // Greedy: consume as many repetitions as possible, then backtrack.
    private boolean rep(int count, State st, int pos, Cont k) {
      if (count < max
          && atom.match(st, pos, p -> p > pos && rep(count + 1, st, p, k))) {
        return true;
      }
      return count >= min && k.apply(pos);
    }
  }

  private static final class Group extends Node {
    final Node body;
    final int index;

    Group(Node body, int index) {
      this.body = body;
      this.index = index;
    }

    boolean match(State st, int pos, Cont k) {
      int savedStart = st.capStart[index];
      int savedEnd = st.capEnd[index];
      st.capStart[index] = pos;
      boolean ok = body.match(st, pos, end -> {
        int prevEnd = st.capEnd[index];
        st.capEnd[index] = end;
        if (k.apply(end)) {
          return true;
        }
        st.capEnd[index] = prevEnd;
        return false;
      });
      if (!ok) {
        st.capStart[index] = savedStart;
        st.capEnd[index] = savedEnd;
      }
      return ok;
    }
  }

  private static final class Backref extends Node {
    final int index;

    Backref(int index) {
      this.index = index;
    }

    boolean match(State st, int pos, Cont k) {
      if (st.capStart[index] < 0 || st.capEnd[index] < 0) {
        return false;
      }
      String captured = st.input.substring(st.capStart[index], st.capEnd[index]);
      if (st.input.regionMatches(pos, captured, 0, captured.length())) {
        return k.apply(pos + captured.length());
      }
      return false;
    }
  }

  // --- parser -----------------------------------------------------------

  private static final class Parser {
    final String src;
    int i = 0;
    int groupCount = 0;

    Parser(String src) {
      this.src = src;
    }

    boolean atEnd() {
      return i >= src.length();
    }

    private char peek() {
      return src.charAt(i);
    }

    Node parseAlternation() {
      List<Node> branches = new ArrayList<>();
      branches.add(parseSequence());
      while (!atEnd() && peek() == '|') {
        i++;
        branches.add(parseSequence());
      }
      return branches.size() == 1 ? branches.get(0) : new Alternation(branches);
    }

    private Node parseSequence() {
      List<Node> items = new ArrayList<>();
      while (!atEnd() && peek() != '|' && peek() != ')') {
        items.add(parseQuantified());
      }
      return new Sequence(items);
    }

    private Node parseQuantified() {
      Node atom = parseAtom();
      if (atEnd()) {
        return atom;
      }
      char c = peek();
      if (c == '+') {
        i++;
        return new Repeat(atom, 1, Integer.MAX_VALUE);
      }
      if (c == '?') {
        i++;
        return new Repeat(atom, 0, 1);
      }
      if (c == '*') {
        i++;
        return new Repeat(atom, 0, Integer.MAX_VALUE);
      }
      if (c == '{') {
        int[] bounds = parseBraceBounds();
        if (bounds != null) {
          return new Repeat(atom, bounds[0], bounds[1]);
        }
      }
      return atom;
    }

    /** Parses "{n}", "{n,}" or "{n,m}" starting at '{'. Returns null if not a valid quantifier. */
    private int[] parseBraceBounds() {
      int save = i;
      i++; // consume '{'
      int min = readInt();
      if (min < 0) {
        i = save;
        return null;
      }
      int max;
      if (!atEnd() && peek() == ',') {
        i++;
        int m = readInt();
        max = m < 0 ? Integer.MAX_VALUE : m;
      } else {
        max = min;
      }
      if (atEnd() || peek() != '}') {
        i = save;
        return null;
      }
      i++; // consume '}'
      return new int[] {min, max};
    }

    private int readInt() {
      int start = i;
      while (!atEnd() && Character.isDigit(peek())) {
        i++;
      }
      if (i == start) {
        return -1;
      }
      return Integer.parseInt(src.substring(start, i));
    }

    private Node parseAtom() {
      char c = peek();
      switch (c) {
        case '(': {
          i++;
          int index = ++groupCount;
          Node body = parseAlternation();
          expect(')');
          return new Group(body, index);
        }
        case '[':
          return parseCharClass();
        case '^':
          i++;
          return new StartAnchor();
        case '$':
          i++;
          return new EndAnchor();
        case '.':
          i++;
          return new AnyChar();
        case '\\':
          return parseEscape();
        default:
          i++;
          return new Literal(c);
      }
    }

    private Node parseEscape() {
      i++; // consume '\'
      if (atEnd()) {
        throw new IllegalArgumentException("Trailing backslash in pattern");
      }
      char c = src.charAt(i++);
      if (c >= '1' && c <= '9') {
        return new Backref(c - '0');
      }
      CharPredicate p = classEscape(c);
      if (p != null) {
        return new CharClass(p);
      }
      return new Literal(c);
    }

    private static CharPredicate classEscape(char c) {
      return switch (c) {
        case 'd' -> Character::isDigit;
        case 'w' -> ch -> ch == '_' || Character.isLetterOrDigit(ch);
        case 's' -> Character::isWhitespace;
        default -> null;
      };
    }

    private Node parseCharClass() {
      i++; // consume '['
      boolean negated = false;
      if (!atEnd() && peek() == '^') {
        negated = true;
        i++;
      }
      List<CharPredicate> parts = new ArrayList<>();
      while (!atEnd() && peek() != ']') {
        char c = src.charAt(i++);
        if (c == '\\' && !atEnd()) {
          char esc = src.charAt(i++);
          CharPredicate p = classEscape(esc);
          parts.add(p != null ? p : ch -> ch == esc);
          continue;
        }
        if (!atEnd() && peek() == '-' && i + 1 < src.length() && src.charAt(i + 1) != ']') {
          i++; // consume '-'
          char hi = src.charAt(i++);
          char lo = c;
          parts.add(ch -> ch >= lo && ch <= hi);
        } else {
          char lit = c;
          parts.add(ch -> ch == lit);
        }
      }
      expect(']');
      boolean neg = negated;
      CharPredicate combined = ch -> {
        for (CharPredicate p : parts) {
          if (p.test(ch)) {
            return !neg;
          }
        }
        return neg;
      };
      return new CharClass(combined);
    }

    private void expect(char c) {
      if (atEnd() || src.charAt(i) != c) {
        throw new IllegalArgumentException("Expected '" + c + "' at position " + i + " in pattern: " + src);
      }
      i++;
    }
  }
}
