import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Cases lifted verbatim from the codecrafters grep-tester stage_*.go sources
 * (github.com/codecrafters-io/grep-tester/internal). exit 0 == match found.
 */
class RegexTest {

  record Case(String pattern, String input, boolean shouldMatch) {}

  private static Case m(String p, String in) {
    return new Case(p, in, true);
  }

  private static Case n(String p, String in) {
    return new Case(p, in, false);
  }

  private static final List<Case> CASES = List.of(
      // cq2 - literal
      m("d", "dog"), n("f", "dog"),
      // oq2 - \d
      m("\\d", "123"), n("\\d", "apple"), m("\\d", "abc_0_xyz"),
      // mr9 - \w
      m("\\w", "word"), m("\\w", "WORD"), m("\\w", "123"),
      m("\\w", "+-÷_×=#"), n("\\w", "+-÷×=#%"),
      // tl6 - positive char groups
      m("[abc]", "a"), m("[abc]", "axyz"), n("[abc]", "xyz"), n("[abc]", "[]"),
      // rk3 - negative char groups
      m("[^xyz]", "apple"), m("[^abc]", "apple"), n("[^anb]", "banana"), m("[^opq]", "orange"),
      // sh9 - combining
      m("\\d apple", "sally has 3 apples"),
      n("\\d apple", "sally has 1 orange"),
      m("\\d\\d\\d apples", "sally has 124 apples"),
      n("\\d\\\\d\\\\d apples", "sally has 12 apples"),
      m("\\d \\w\\w\\ws", "sally has 3 dogs"),
      m("\\d \\w\\w\\ws", "sally has 4 dogs"),
      n("\\d \\w\\w\\ws", "sally has 1 dog"),
      // rr8 - start anchor
      m("^cat", "cat_dog"), n("^cat", "dog_cat"),
      // ao7 - end anchor
      m("cat$", "dog_cat"), n("cat$", "cat_dog"),
      m("^cat$", "cat"), n("^cat$", "cat_cat"),
      // fz7 - one or more
      m("ca+t", "cat"), m("ca+at", "caaats"), n("ca+t", "act"), n("ca+t", "ca"),
      m("^abc_\\d+_xyz$", "abc_123_xyz"), n("^abc_\\d+_xyz$", "abc_rst_xyz"),
      // ny8 - zero or one
      m("ca?t", "cat"), m("ca?t", "act"), m("ca?a?t", "cat"),
      n("ca?t", "caat"), n("ca?t", "cag"),
      // zb3 - wildcard
      m("c.t", "cat"), n("c.t", "car"), m("g.+gol", "goøö0Ogol"), n("g.+gol", "gol"),
      // zm7 - alternation
      m("a (cat|dog)", "a cat"), n("a (cat|dog)", "a cog"),
      m("^I see \\d+ (cat|dog)s?$", "I see 1 cat"),
      m("^I see \\d+ (cat|dog)s?$", "I see 42 dogs"),
      n("^I see \\d+ (cat|dog)s?$", "I see a cat"),
      n("^I see \\d+ (cat|dog)s?$", "I see 2 dog3"),
      // sb5 - backreferences single
      m("(cat) and \\1", "cat and cat"), n("(cat) and \\1", "cat and dog"),
      m("(\\w+) and \\1", "cat and cat"), n("(\\w+) and \\1", "cat and dog"),
      m("^([act]+) is \\1, not [^xyz]+$", "cat is cat, not dog"),
      n("^([act]+) is \\1, not [^xyz]+$", "cat is c@t, not d0g"),
      // tg1 - backreferences multiple
      m("(\\d+) (\\w+) squares and \\1 \\2 circles", "3 red squares and 3 red circles"),
      n("(\\d+) (\\w+) squares and \\1 \\2 circles", "3 red squares and 4 red circles"),
      m("(\\w\\w\\w\\w) (\\d\\d\\d) is doing \\1 \\2 times", "grep 101 is doing grep 101 times"),
      n("(\\w\\w\\w) (\\d\\d\\d) is doing \\1 \\2 times", "$?! 101 is doing $?! 101 times"),
      n("(\\w\\w\\w\\w) (\\d\\d\\d) is doing \\1 \\2 times", "grep yes is doing grep yes times"),
      m("([abc]+)-([def]+) is \\1-\\2, not [^xyz]+", "abc-def is abc-def, not efg"),
      n("([abc]+)-([def]+) is \\1-\\2, not [^xyz]+", "efg-hij is efg-hij, not efg"),
      n("([abc]+)-([def]+) is \\1-\\2, not [^xyz]+", "abc-def is abc-def, not xyz"),
      m("^(\\w+) (\\w+), \\1 and \\2$", "apple pie, apple and pie"),
      n("^(apple) (\\w+), \\1 and \\2$", "pineapple pie, pineapple and pie"),
      n("^(\\w+) (pie), \\1 and \\2$", "apple pie, apple and pies"),
      m("(how+dy) (he?y) there, \\1 \\2", "howwdy hey there, howwdy hey"),
      n("(how+dy) (he?y) there, \\1 \\2", "hody hey there, howwdy hey"),
      n("(how+dy) (he?y) there, \\1 \\2", "howwdy heeey there, howwdy heeey"),
      m("(c.t|d.g) and (f..h|b..d), \\1 with \\2", "cat and fish, cat with fish"),
      n("(c.t|d.g) and (f..h|b..d), \\1 with \\2", "bat and fish, cat with fish"),
      // xe5 - backreferences nested
      m("(\"(cat) and \\2\") is the same as \\1", "\"cat and cat\" is the same as \"cat and cat\""),
      n("(\"(cat) and \\2\") is the same as \\1", "\"cat and cat\" is the same as \"cat and dog\""),
      m("((\\w\\w\\w\\w) (\\d\\d\\d)) is doing \\2 \\3 times, and again \\1 times",
          "grep 101 is doing grep 101 times, and again grep 101 times"),
      n("((\\w\\w\\w) (\\d\\d\\d)) is doing \\2 \\3 times, and again \\1 times",
          "$?! 101 is doing $?! 101 times, and again $?! 101 times"),
      m("(([abc]+)-([def]+)) is \\1, not ([^xyz]+), \\2, or \\3",
          "abc-def is abc-def, not efg, abc, or def"),
      n("(([abc]+)-([def]+)) is \\1, not ([^xyz]+), \\2, or \\3",
          "abc-def is abc-def, not xyz, abc, or def"),
      m("^((\\w+) (\\w+)) is made of \\2 and \\3. love \\1$",
          "apple pie is made of apple and pie. love apple pie"),
      n("^((\\w+) (pie)) is made of \\2 and \\3. love \\1$",
          "apple pie is made of apple and pie. love apple pies"),
      m("((c.t|d.g) and (f..h|b..d)), \\2 with \\3, \\1",
          "cat and fish, cat with fish, cat and fish"),
      n("((c.t|d.g) and (f..h|b..d)), \\2 with \\3, \\1",
          "bat and fish, bat with fish, bat and fish"),
      // ai9/wy9/hk3/ug0 - asterisk and brace quantifiers
      m("a*", "b"), m("ab*c", "ac"), m("ab*c", "abbbc"),
      m("a{3}", "aaaa"), n("a{3}", "aa"),
      m("a{2,}", "aaaa"), n("a{2,}", "a"),
      m("a{2,3}", "aaaa"), n("a{2,3}", "a"), m("a{2,3}", "aa")
  );

  @TestFactory
  Iterable<DynamicTest> testerCases() {
    return CASES.stream()
        .map(c -> DynamicTest.dynamicTest(
            c.pattern() + " ~ " + c.input(),
            () -> assertEquals(c.shouldMatch(), Regex.compile(c.pattern()).find(c.input()))))
        .toList();
  }
}
